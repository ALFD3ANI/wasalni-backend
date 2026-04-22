package com.wasalni;

// ============================================
// OrderController - إدارة الطلبات
// يتعامل مع:
// POST /api/orders              - إنشاء طلب جديد
// GET  /api/orders/{id}         - تفاصيل طلب
// GET  /api/orders/{id}/tracking- تتبع الطلب
// POST /api/orders/{id}/review  - تقييم الطلب
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/orders")
public class OrderController {

    // للتعامل مع قاعدة البيانات
    @Autowired
    JdbcTemplate db;

    // لقراءة التوكن
    @Autowired
    JwtUtil jwtUtil;

    // ============================================
    // دالة مساعدة - استخراج رقم المستخدم من التوكن
    // ============================================
    private String getUserIdFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getSubject(token);
    }

    // ============================================
    // POST /api/orders
    // إنشاء طلب جديد
    // ============================================
    @PostMapping
    public Map<String, Object> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // استخراج بيانات الطلب
            Integer restaurantId = (Integer) data.get("restaurantId");
            Integer addressId = (Integer) data.get("addressId");
            String paymentMethod = (String) data.get("paymentMethod");
            String notes = (String) data.get("notes");
            String couponCode = (String) data.get("couponCode");
            List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

            // التحقق من البيانات الأساسية
            if (restaurantId == null || paymentMethod == null || items == null || items.isEmpty()) {
                response.put("success", false);
                response.put("message", "بيانات الطلب غير مكتملة");
                return response;
            }

            // حساب مجموع المنتجات
            double subtotal = 0;
            for (Map<String, Object> item : items) {
                Integer productId = (Integer) item.get("productId");
                Integer quantity = (Integer) item.get("quantity");

                // جلب سعر المنتج من قاعدة البيانات
                Map<String, Object> product = db.queryForMap(
                    "SELECT price FROM products WHERE id = ? AND restaurant_id = ?",
                    productId, restaurantId
                );

                double price = ((Number) product.get("price")).doubleValue();
                subtotal += price * quantity;
            }

            // حساب رسوم التوصيل
            double deliveryFee = 5.0;

            // التحقق من الكوبون إذا موجود
            double discount = 0;
            Integer couponId = null;
            if (couponCode != null && !couponCode.isEmpty()) {
                List<Map<String, Object>> coupons = db.queryForList(
                    "SELECT * FROM coupons WHERE code = ? AND is_active = true",
                    couponCode
                );

                if (!coupons.isEmpty()) {
                    Map<String, Object> coupon = coupons.get(0);
                    couponId = (Integer) coupon.get("id");
                    String discountType = (String) coupon.get("discount_type");
                    double discountValue = ((Number) coupon.get("discount_value")).doubleValue();

                    // حساب الخصم
                    if (discountType.equals("percentage")) {
                        discount = subtotal * (discountValue / 100);
                    } else {
                        discount = discountValue;
                    }

                    // تحديث عداد استخدام الكوبون
                    db.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ?", couponId);
                }
            }

            // حساب المبلغ النهائي
            double totalPrice = subtotal + deliveryFee - discount;

            // إنشاء الطلب في قاعدة البيانات
            db.update(
                "INSERT INTO orders (user_id, restaurant_id, address_id, coupon_id, subtotal, delivery_fee, discount, total_price, payment_method, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, restaurantId, addressId, couponId, subtotal, deliveryFee, discount, totalPrice, paymentMethod, notes
            );

            // جلب رقم الطلب الجديد
            Map<String, Object> newOrder = db.queryForMap(
                "SELECT id FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                userId
            );
            Integer orderId = (Integer) newOrder.get("id");

            // إضافة تفاصيل الطلب
            for (Map<String, Object> item : items) {
                Integer productId = (Integer) item.get("productId");
                Integer quantity = (Integer) item.get("quantity");
                String extras = (String) item.get("extras");
                String itemNotes = (String) item.get("notes");

                Map<String, Object> product = db.queryForMap(
                    "SELECT price FROM products WHERE id = ?", productId
                );
                double price = ((Number) product.get("price")).doubleValue();

                db.update(
                    "INSERT INTO order_items (order_id, product_id, quantity, price, extras, notes) VALUES (?, ?, ?, ?, ?, ?)",
                    orderId, productId, quantity, price, extras, itemNotes
                );
            }

            // إضافة أول سجل في تتبع الطلب
            db.update(
                "INSERT INTO order_tracking (order_id, status, message) VALUES (?, ?, ?)",
                orderId, "pending", "تم استلام طلبك وبانتظار تأكيد المطعم"
            );

            // إرجاع تفاصيل الطلب
            response.put("success", true);
            response.put("message", "تم إنشاء الطلب بنجاح");
            response.put("orderId", orderId);
            response.put("totalPrice", totalPrice);
            response.put("estimatedTime", "25-35 دقيقة");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/orders/{id}
    // تفاصيل طلب محدد
    // ============================================
    @GetMapping("/{id}")
    public Map<String, Object> getOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // جلب الطلب مع اسم المطعم
            Map<String, Object> order = db.queryForMap(
                "SELECT o.*, r.name as restaurant_name, r.image as restaurant_image, r.phone as restaurant_phone " +
                "FROM orders o " +
                "JOIN restaurants r ON o.restaurant_id = r.id " +
                "WHERE o.id = ? AND o.user_id = ?",
                id, userId
            );

            // جلب تفاصيل المنتجات
            List<Map<String, Object>> items = db.queryForList(
                "SELECT oi.*, p.name as product_name, p.image as product_image " +
                "FROM order_items oi " +
                "JOIN products p ON oi.product_id = p.id " +
                "WHERE oi.order_id = ?",
                id
            );

            order.put("items", items);

            response.put("success", true);
            response.put("order", order);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "الطلب غير موجود");
        }

        return response;
    }

    // ============================================
    // GET /api/orders/{id}/tracking
    // تتبع مراحل الطلب
    // ============================================
    @GetMapping("/{id}/tracking")
    public Map<String, Object> getOrderTracking(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            // جلب كل مراحل الطلب بالترتيب
            List<Map<String, Object>> tracking = db.queryForList(
                "SELECT * FROM order_tracking WHERE order_id = ? ORDER BY created_at ASC",
                id
            );

            // جلب الحالة الحالية للطلب
            Map<String, Object> order = db.queryForMap(
                "SELECT status, estimated_time FROM orders WHERE id = ?", id
            );

            response.put("success", true);
            response.put("currentStatus", order.get("status"));
            response.put("estimatedTime", order.get("estimated_time"));
            response.put("tracking", tracking);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/orders/{id}/review
    // تقييم الطلب بعد التوصيل
    // ============================================
    @PostMapping("/{id}/review")
    public Map<String, Object> reviewOrder(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            Integer restaurantRating = (Integer) data.get("restaurantRating");
            Integer driverRating = (Integer) data.get("driverRating");
            String comment = (String) data.get("comment");

            // جلب بيانات الطلب
            Map<String, Object> order = db.queryForMap(
                "SELECT restaurant_id, driver_id, status FROM orders WHERE id = ? AND user_id = ?",
                id, userId
            );

            // التحقق إن الطلب موصّل
            if (!order.get("status").equals("delivered")) {
                response.put("success", false);
                response.put("message", "لا يمكن التقييم قبل استلام الطلب");
                return response;
            }

            // حفظ التقييم
            db.update(
                "INSERT INTO reviews (user_id, order_id, restaurant_id, driver_id, restaurant_rating, driver_rating, comment) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                userId, id, order.get("restaurant_id"), order.get("driver_id"),
                restaurantRating, driverRating, comment
            );

            // تحديث متوسط تقييم المطعم
            db.update(
                "UPDATE restaurants SET " +
                "rating = (SELECT AVG(restaurant_rating) FROM reviews WHERE restaurant_id = ?), " +
                "total_reviews = (SELECT COUNT(*) FROM reviews WHERE restaurant_id = ?) " +
                "WHERE id = ?",
                order.get("restaurant_id"), order.get("restaurant_id"), order.get("restaurant_id")
            );

            response.put("success", true);
            response.put("message", "شكراً على تقييمك!");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }
}