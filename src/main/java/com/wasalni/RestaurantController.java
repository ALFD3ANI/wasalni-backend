package com.wasalni;

// ============================================
// RestaurantController - إدارة المطاعم
// يتعامل مع:
// GET  /api/restaurants              - عرض كل المطاعم
// GET  /api/restaurants/{id}         - تفاصيل مطعم
// GET  /api/restaurants/search       - البحث عن مطعم
// GET  /api/restaurants/category/{id}- مطاعم حسب التصنيف
// PUT  /api/restaurant/profile       - تعديل بيانات المطعم
// GET  /api/restaurant/products      - منتجات المطعم
// POST /api/restaurant/products      - إضافة منتج
// PUT  /api/restaurant/products/{id} - تعديل منتج
// DEL  /api/restaurant/products/{id} - حذف منتج
// GET  /api/restaurant/orders        - طلبات المطعم
// PUT  /api/restaurant/orders/{id}   - تحديث حالة الطلب
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class RestaurantController {

    // للتعامل مع قاعدة البيانات
    @Autowired
    JdbcTemplate db;

    // لقراءة التوكن
    @Autowired
    JwtUtil jwtUtil;

    // ============================================
    // دالة مساعدة - استخراج رقم المطعم من التوكن
    // ============================================
    private String getRestaurantIdFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getSubject(token);
    }

    // ============================================
    // GET /api/health
    // فحص صحة الـ API
    // ============================================
    @GetMapping("/api/health")
    public Map<String, String> health() {
        Map<String, String> res = new HashMap<>();
        res.put("status", "OK");
        res.put("message", "Wasalni API is running!");
        res.put("developer", "Bader AL-anazi");
        return res;
    }

    // ============================================
    // GET /api/restaurants
    // عرض كل المطاعم المفعلة
    // ============================================
    @GetMapping("/api/restaurants")
    public Map<String, Object> getRestaurants() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT r.*, c.name as category_name FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id = c.id " +
                "WHERE r.is_active = true " +
                "ORDER BY r.rating DESC"
            );

            response.put("success", true);
            response.put("restaurants", restaurants);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/restaurants/search?q=برغر
    // البحث عن مطعم بالاسم
    // ============================================
    @GetMapping("/api/restaurants/search")
    public Map<String, Object> searchRestaurants(@RequestParam String q) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT * FROM restaurants WHERE is_active = true AND name LIKE ? ORDER BY rating DESC",
                "%" + q + "%"
            );

            response.put("success", true);
            response.put("restaurants", restaurants);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/restaurants/category/{categoryId}
    // عرض المطاعم حسب التصنيف
    // ============================================
    @GetMapping("/api/restaurants/category/{categoryId}")
    public Map<String, Object> getRestaurantsByCategory(@PathVariable int categoryId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT * FROM restaurants WHERE is_active = true AND category_id = ? ORDER BY rating DESC",
                categoryId
            );

            response.put("success", true);
            response.put("restaurants", restaurants);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/restaurants/{id}
    // تفاصيل مطعم مع منتجاته
    // ============================================
    @GetMapping("/api/restaurants/{id}")
    public Map<String, Object> getRestaurantById(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            // جلب بيانات المطعم
            Map<String, Object> restaurant = db.queryForMap(
                "SELECT r.*, c.name as category_name FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id = c.id " +
                "WHERE r.id = ?",
                id
            );

            // جلب منتجات المطعم
            List<Map<String, Object>> products = db.queryForList(
                "SELECT * FROM products WHERE restaurant_id = ? AND is_available = true",
                id
            );

            // جلب أوقات العمل
            List<Map<String, Object>> hours = db.queryForList(
                "SELECT * FROM restaurant_hours WHERE restaurant_id = ?",
                id
            );

            // جلب آخر التقييمات
            List<Map<String, Object>> reviews = db.queryForList(
                "SELECT rv.*, u.name as user_name FROM reviews rv " +
                "JOIN users u ON rv.user_id = u.id " +
                "WHERE rv.restaurant_id = ? " +
                "ORDER BY rv.created_at DESC LIMIT 10",
                id
            );

            restaurant.put("products", products);
            restaurant.put("hours", hours);
            restaurant.put("reviews", reviews);

            response.put("success", true);
            response.put("restaurant", restaurant);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "المطعم غير موجود");
        }

        return response;
    }

    // ============================================
    // PUT /api/restaurant/profile
    // تعديل بيانات المطعم (يحتاج توكن مطعم)
    // ============================================
    @PutMapping("/api/restaurant/profile")
    public Map<String, Object> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);

            String name = (String) data.get("name");
            String description = (String) data.get("description");
            String phone = (String) data.get("phone");
            String address = (String) data.get("address");
            String image = (String) data.get("image");
            Integer deliveryTime = (Integer) data.get("deliveryTime");
            Double minOrder = data.get("minOrder") != null ? ((Number) data.get("minOrder")).doubleValue() : null;

            db.update(
                "UPDATE restaurants SET " +
                "name = COALESCE(?, name), " +
                "description = COALESCE(?, description), " +
                "phone = COALESCE(?, phone), " +
                "address = COALESCE(?, address), " +
                "image = COALESCE(?, image), " +
                "delivery_time = COALESCE(?, delivery_time), " +
                "min_order = COALESCE(?, min_order) " +
                "WHERE id = ?",
                name, description, phone, address, image, deliveryTime, minOrder, restaurantId
            );

            response.put("success", true);
            response.put("message", "تم تحديث بيانات المطعم بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/restaurant/products
    // عرض كل منتجات المطعم (يحتاج توكن مطعم)
    // ============================================
    @GetMapping("/api/restaurant/products")
    public Map<String, Object> getMyProducts(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);

            List<Map<String, Object>> products = db.queryForList(
                "SELECT * FROM products WHERE restaurant_id = ? ORDER BY category, name",
                restaurantId
            );

            response.put("success", true);
            response.put("products", products);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/restaurant/products
    // إضافة منتج جديد (يحتاج توكن مطعم)
    // ============================================
    @PostMapping("/api/restaurant/products")
    public Map<String, Object> addProduct(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);

            String name = (String) data.get("name");
            String description = (String) data.get("description");
            Double price = ((Number) data.get("price")).doubleValue();
            String image = (String) data.get("image");
            String category = (String) data.get("category");

            if (name == null || price == null) {
                response.put("success", false);
                response.put("message", "اسم المنتج والسعر مطلوبان");
                return response;
            }

            db.update(
                "INSERT INTO products (restaurant_id, name, description, price, image, category) VALUES (?, ?, ?, ?, ?, ?)",
                restaurantId, name, description, price, image, category
            );

            response.put("success", true);
            response.put("message", "تم إضافة المنتج بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/restaurant/products/{id}
    // تعديل منتج (يحتاج توكن مطعم)
    // ============================================
    @PutMapping("/api/restaurant/products/{id}")
    public Map<String, Object> updateProduct(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);

            String name = (String) data.get("name");
            String description = (String) data.get("description");
            Double price = data.get("price") != null ? ((Number) data.get("price")).doubleValue() : null;
            String image = (String) data.get("image");
            Boolean isAvailable = (Boolean) data.get("isAvailable");

            db.update(
                "UPDATE products SET " +
                "name = COALESCE(?, name), " +
                "description = COALESCE(?, description), " +
                "price = COALESCE(?, price), " +
                "image = COALESCE(?, image), " +
                "is_available = COALESCE(?, is_available) " +
                "WHERE id = ? AND restaurant_id = ?",
                name, description, price, image, isAvailable, id, restaurantId
            );

            response.put("success", true);
            response.put("message", "تم تعديل المنتج بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // DELETE /api/restaurant/products/{id}
    // حذف منتج (يحتاج توكن مطعم)
    // ============================================
    @DeleteMapping("/api/restaurant/products/{id}")
    public Map<String, Object> deleteProduct(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);

            int rows = db.update(
                "DELETE FROM products WHERE id = ? AND restaurant_id = ?",
                id, restaurantId
            );

            if (rows > 0) {
                response.put("success", true);
                response.put("message", "تم حذف المنتج بنجاح");
            } else {
                response.put("success", false);
                response.put("message", "المنتج غير موجود");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/restaurant/orders
    // طلبات المطعم (يحتاج توكن مطعم)
    // ============================================
    @GetMapping("/api/restaurant/orders")
    public Map<String, Object> getRestaurantOrders(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);

            // جلب الطلبات مع تسمية total_price باسم total_amount لأن الـ frontend يقرأ total_amount
            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.id, o.status, o.payment_method, o.notes, o.created_at, " +
                "o.subtotal, o.delivery_fee, o.discount, " +
                "o.total_price as total_amount, " +
                "u.name as user_name, u.phone as user_phone " +
                "FROM orders o " +
                "JOIN users u ON o.user_id = u.id " +
                "WHERE o.restaurant_id = ? " +
                "ORDER BY o.created_at DESC",
                restaurantId
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
    // PUT /api/restaurant/orders/{id}
    // تحديث حالة الطلب من المطعم (يحتاج توكن مطعم)
    // الحالات: accepted / preparing / ready / cancelled
    // ============================================
    @PutMapping("/api/restaurant/orders/{id}")
    public Map<String, Object> updateOrderStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);
            String status = (String) data.get("status");

            db.update(
                "UPDATE orders SET status = ? WHERE id = ? AND restaurant_id = ?",
                status, id, restaurantId
            );

            // إضافة سجل في تتبع الطلب
            db.update(
                "INSERT INTO order_tracking (order_id, status, message) VALUES (?, ?, ?)",
                id, status, "تم تحديث حالة الطلب إلى: " + status
            );

            response.put("success", true);
            response.put("message", "تم تحديث حالة الطلب");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }
}