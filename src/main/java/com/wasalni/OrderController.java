package com.wasalni;

// ============================================
// OrderController - إدارة الطلبات
// يتعامل مع:
// POST /api/orders              - إنشاء طلب جديد
// GET  /api/orders/my           - طلبات المستخدم الحالي
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

            // ============================================
            // معالجة العنوان: إذا لم يُرسل addressId أو كان 0
            // نبحث عن عنوان مسجل للمستخدم، أو ننشئ واحداً افتراضياً
            // ============================================
            Integer finalAddressId = (addressId != null && addressId > 0) ? addressId : null;
            if (finalAddressId == null) {
                List<Map<String, Object>> userAddresses = db.queryForList(
                    "SELECT id FROM addresses WHERE user_id = ? ORDER BY id ASC LIMIT 1", userId
                );
                if (!userAddresses.isEmpty()) {
                    // استخدام أول عنوان موجود للمستخدم
                    finalAddressId = ((Number) userAddresses.get(0).get("id")).intValue();
                } else {
                    // إنشاء عنوان افتراضي إذا لم يكن للمستخدم أي عنوان
                    db.update(
                        "INSERT INTO addresses (user_id, label, address_text) VALUES (?, ?, ?)",
                        userId, "الموقع الافتراضي", "غير محدد"
                    );
                    Map<String, Object> newAddr = db.queryForMap(
                        "SELECT id FROM addresses WHERE user_id = ? ORDER BY id DESC LIMIT 1", userId
                    );
                    finalAddressId = ((Number) newAddr.get("id")).intValue();
                }
            }

            // التأكد من وجود عمود is_available في products
            try { db.execute("ALTER TABLE products ADD COLUMN is_available TINYINT DEFAULT 1"); } catch (Exception ignored) {}

            // حساب مجموع المنتجات (مع دعم extras pricing)
            double subtotal = 0;
            for (Map<String, Object> item : items) {
                Integer productId = (Integer) item.get("productId");
                Integer quantity  = (Integer) item.get("quantity");
                if (productId == null || quantity == null || quantity <= 0) continue;

                // جلب سعر المنتج من قاعدة البيانات والتحقق من توفره
                List<Map<String, Object>> productList = db.queryForList(
                    "SELECT price, is_available FROM products WHERE id = ? AND restaurant_id = ?",
                    productId, restaurantId
                );

                // إذا المنتج غير موجود أو غير تابع لهذا المطعم، نتجاهله
                if (productList.isEmpty()) continue;

                Map<String, Object> prod = productList.get(0);
                // تحقق من أن المنتج متاح (not out of stock)
                Object avail = prod.get("is_available");
                if (avail != null && avail.toString().equals("0")) {
                    response.put("success", false);
                    response.put("message", "المنتج غير متاح حالياً — يرجى إزالته من السلة");
                    return response;
                }

                double basePrice = ((Number) prod.get("price")).doubleValue();

                // احسب سعر الإضافات المُختارة (extraIds أو unitPrice من الفرونت)
                double extrasTotal = 0;
                Object unitPriceObj = item.get("unitPrice");
                if (unitPriceObj != null) {
                    double sentUnitPrice = ((Number) unitPriceObj).doubleValue();
                    // استخدم السعر المُرسل إن كان >= السعر الأساسي (الفرق هو الإضافات)
                    if (sentUnitPrice >= basePrice) {
                        extrasTotal = sentUnitPrice - basePrice;
                    }
                } else {
                    // بديل: احسب من extraIds إذا أُرسلت
                    @SuppressWarnings("unchecked")
                    List<Integer> extraIds = (List<Integer>) item.get("extraIds");
                    if (extraIds != null && !extraIds.isEmpty()) {
                        for (Integer extraId : extraIds) {
                            try {
                                Map<String, Object> ex = db.queryForMap(
                                    "SELECT price FROM menu_extras WHERE id = ? AND product_id = ? AND is_available = 1",
                                    extraId, productId
                                );
                                extrasTotal += ((Number) ex.get("price")).doubleValue();
                            } catch (Exception ignored) {}
                        }
                    }
                }

                subtotal += (basePrice + extrasTotal) * quantity;
            }

            // التحقق أن هناك على الأقل منتج واحد صالح
            if (subtotal == 0) {
                response.put("success", false);
                response.put("message", "لا توجد منتجات صالحة في الطلب أو الطلب فارغ");
                return response;
            }

            // حساب رسوم التوصيل من قاعدة البيانات (أو 5 افتراضياً)
            double deliveryFee = 5.0;
            try {
                Map<String, Object> restInfo = db.queryForMap(
                    "SELECT delivery_fee FROM restaurants WHERE id = ?", restaurantId
                );
                if (restInfo.get("delivery_fee") != null) {
                    deliveryFee = ((Number) restInfo.get("delivery_fee")).doubleValue();
                }
            } catch (Exception ignored) {}

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

                    // تطبيق الحد الأقصى للخصم (max_discount_amount) إن وجد
                    Object maxDiscObj = coupon.get("max_discount_amount");
                    if (maxDiscObj != null) {
                        double maxDiscount = ((Number) maxDiscObj).doubleValue();
                        if (maxDiscount > 0 && discount > maxDiscount) discount = maxDiscount;
                    }

                    // الخصم لا يتجاوز قيمة الطلب
                    if (discount > subtotal) discount = subtotal;

                    // تحديث عداد استخدام الكوبون
                    db.update("UPDATE coupons SET used_count = used_count + 1 WHERE id = ?", couponId);
                }
            }

            // حساب المبلغ النهائي
            double totalPrice = subtotal + deliveryFee - discount;

            // إنشاء الطلب في قاعدة البيانات (استخدام finalAddressId بدل addressId الأصلي)
            db.update(
                "INSERT INTO orders (user_id, restaurant_id, address_id, coupon_id, subtotal, delivery_fee, discount, total_price, payment_method, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                userId, restaurantId, finalAddressId, couponId, subtotal, deliveryFee, discount, totalPrice, paymentMethod, notes
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
    // GET /api/orders/my
    // كل طلبات المستخدم الحالي (يحتاج توكن)
    // ============================================
    @GetMapping("/my")
    public Map<String, Object> getMyOrders(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // جلب كل طلبات المستخدم مع اسم المطعم
            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.id, o.status, o.total_price as total_amount, o.created_at, " +
                "r.name as restaurant_name, r.image as restaurant_image " +
                "FROM orders o " +
                "JOIN restaurants r ON o.restaurant_id = r.id " +
                "WHERE o.user_id = ? ORDER BY o.created_at DESC",
                userId
            );

            // إضافة ملخص المنتجات لكل طلب
            for (Map<String, Object> order : orders) {
                Integer orderId = ((Number) order.get("id")).intValue();
                List<Map<String, Object>> items = db.queryForList(
                    "SELECT oi.quantity, p.name as product_name " +
                    "FROM order_items oi " +
                    "JOIN products p ON oi.product_id = p.id " +
                    "WHERE oi.order_id = ?",
                    orderId
                );
                // بناء ملخص نصي للمنتجات
                StringBuilder summary = new StringBuilder();
                for (int i = 0; i < items.size(); i++) {
                    if (i > 0) summary.append("، ");
                    summary.append(items.get(i).get("product_name"))
                           .append(" ×")
                           .append(items.get(i).get("quantity"));
                }
                order.put("items_summary", summary.toString());
            }

            response.put("success", true);
            response.put("orders", orders);

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

            // جلب تفاصيل المنتجات مع الأسعار الكاملة للفاتورة
            List<Map<String, Object>> items = db.queryForList(
                "SELECT oi.id, oi.quantity, oi.price, oi.extras, oi.notes, " +
                "p.name as product_name, p.image as product_image " +
                "FROM order_items oi " +
                "JOIN products p ON oi.product_id = p.id " +
                "WHERE oi.order_id = ?",
                id
            );
            // حساب subtotal لكل عنصر للعرض في الفاتورة
            for (Map<String, Object> item : items) {
                double itemPrice = item.get("price") != null ? ((Number) item.get("price")).doubleValue() : 0;
                int qty = item.get("quantity") != null ? ((Number) item.get("quantity")).intValue() : 1;
                item.put("line_total", itemPrice * qty);
            }
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
    // GET /api/orders/{id}/timeline
    // مراحل الطلب مع أيقونات وألوان للعرض في الواجهة
    // ============================================
    @GetMapping("/{id}/timeline")
    public Map<String, Object> getOrderTimeline(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            String userId = getUserIdFromToken(authHeader);

            // التحقق أن الطلب تابع للمستخدم الحالي
            List<Map<String, Object>> orderCheck = db.queryForList(
                "SELECT id, status FROM orders WHERE id = ? AND user_id = ?", id, userId
            );
            if (orderCheck.isEmpty()) {
                response.put("success", false);
                response.put("message", "الطلب غير موجود");
                return response;
            }

            // جلب مراحل التتبع بالترتيب الزمني
            List<Map<String, Object>> steps = db.queryForList(
                "SELECT status, message, created_at FROM order_tracking WHERE order_id = ? ORDER BY created_at ASC",
                id
            );

            // إضافة أيقونة ولون لكل مرحلة حسب الحالة
            for (Map<String, Object> step : steps) {
                String s = (String) step.get("status");
                switch (s) {
                    case "pending":    step.put("icon", "🕐"); step.put("color", "#FFA500"); break;
                    case "accepted":   step.put("icon", "✅"); step.put("color", "#2196F3"); break;
                    case "preparing":  step.put("icon", "👨‍🍳"); step.put("color", "#9C27B0"); break;
                    case "ready":      step.put("icon", "📦"); step.put("color", "#FF5722"); break;
                    case "picked_up":  step.put("icon", "🛵"); step.put("color", "#00BCD4"); break;
                    case "delivered":  step.put("icon", "🏠"); step.put("color", "#4CAF50"); break;
                    case "cancelled":  step.put("icon", "❌"); step.put("color", "#F44336"); break;
                    default:           step.put("icon", "📍"); step.put("color", "#757575");
                }
            }

            response.put("success", true);
            response.put("currentStatus", orderCheck.get(0).get("status"));
            response.put("timeline", steps);

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