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

            String name        = (String) data.get("name");
            String description = (String) data.get("description");
            String phone       = (String) data.get("phone");
            String address     = (String) data.get("address");
            String image       = (String) data.get("image");       // شعار المطعم
            String coverImage  = (String) data.get("coverImage");  // صورة الغلاف
            Integer deliveryTime = data.get("deliveryTime") != null ? ((Number) data.get("deliveryTime")).intValue() : null;
            Double minOrder      = data.get("minOrder")     != null ? ((Number) data.get("minOrder")).doubleValue()    : null;

            // إضافة عمود cover_image تلقائياً إن لم يكن موجوداً
            try {
                db.execute("ALTER TABLE restaurants ADD COLUMN cover_image VARCHAR(500) DEFAULT NULL");
            } catch (Exception ignored) {}

            db.update(
                "UPDATE restaurants SET " +
                "name = COALESCE(?, name), " +
                "description = COALESCE(?, description), " +
                "phone = COALESCE(?, phone), " +
                "address = COALESCE(?, address), " +
                "image = COALESCE(?, image), " +
                "cover_image = COALESCE(?, cover_image), " +
                "delivery_time = COALESCE(?, delivery_time), " +
                "min_order = COALESCE(?, min_order) " +
                "WHERE id = ?",
                name, description, phone, address, image, coverImage, deliveryTime, minOrder, restaurantId
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
            Double oldPrice = data.get("old_price") != null ? ((Number) data.get("old_price")).doubleValue() : null;
            String image = (String) data.get("image");
            Boolean isAvailable = (Boolean) data.get("isAvailable");

            db.update(
                "UPDATE products SET " +
                "name = COALESCE(?, name), " +
                "description = COALESCE(?, description), " +
                "price = COALESCE(?, price), " +
                "old_price = COALESCE(?, old_price), " +
                "image = COALESCE(?, image), " +
                "is_available = COALESCE(?, is_available) " +
                "WHERE id = ? AND restaurant_id = ?",
                name, description, price, oldPrice, image, isAvailable, id, restaurantId
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

            // قراءة الحالة الحالية قبل التحديث لمنع تكرار نقاط الولاء
            String previousStatus = "";
            try {
                Map<String, Object> currentOrder = db.queryForMap(
                    "SELECT status FROM orders WHERE id = ?", id
                );
                previousStatus = (String) currentOrder.get("status");
            } catch (Exception ignored) {}

            db.update(
                "UPDATE orders SET status = ? WHERE id = ? AND restaurant_id = ?",
                status, id, restaurantId
            );

            // إضافة سجل في تتبع الطلب مع رسالة واضحة حسب الحالة
            Map<String, String> statusMessages = new HashMap<>();
            statusMessages.put("accepted",  "تم قبول طلبك من المطعم ✅");
            statusMessages.put("preparing", "جاري تحضير طلبك 👨‍🍳");
            statusMessages.put("ready",     "طلبك جاهز وبانتظار السائق 📦");
            statusMessages.put("picked_up", "السائق في الطريق إليك 🛵");
            statusMessages.put("delivered", "تم توصيل طلبك بنجاح 🎉");
            statusMessages.put("cancelled", "تم إلغاء الطلب ❌");
            String trackMsg = statusMessages.getOrDefault(status, "تم تحديث حالة الطلب");

            db.update(
                "INSERT INTO order_tracking (order_id, status, message) VALUES (?, ?, ?)",
                id, status, trackMsg
            );

            // تعيين سائق تلقائي عند جهوزية الطلب
            if ("ready".equals(status)) {
                try {
                    List<Map<String, Object>> available = db.queryForList(
                        "SELECT id, name FROM drivers WHERE is_available = 1 AND is_active = 1 " +
                        "AND (is_blocked IS NULL OR is_blocked = 0) ORDER BY RAND() LIMIT 1"
                    );
                    if (!available.isEmpty()) {
                        int driverId = ((Number) available.get(0).get("id")).intValue();
                        db.update("UPDATE orders SET driver_id = ? WHERE id = ?", driverId, id);
                        db.update("UPDATE drivers SET is_available = 0 WHERE id = ?", driverId);
                    }
                } catch (Exception ignored) {}
            }

            // إرسال إشعار للزبون + إضافة نقاط ولاء عند التوصيل
            try {
                Map<String, Object> orderInfo = db.queryForMap(
                    "SELECT o.user_id, o.total_price, r.name as rest_name FROM orders o " +
                    "JOIN restaurants r ON o.restaurant_id = r.id WHERE o.id = ?", id
                );
                db.update(
                    "INSERT INTO notifications (user_id, title, message, type) VALUES (?, ?, ?, ?)",
                    orderInfo.get("user_id"),
                    "تحديث طلبك #" + id,
                    trackMsg + " — " + orderInfo.get("rest_name"),
                    "order_update"
                );
                // عند التوصيل: أضف نقاط ولاء فقط إذا لم تكن الحالة السابقة "delivered"
                if ("delivered".equals(status) && !"delivered".equals(previousStatus)) {
                    int points = ((Number) orderInfo.get("total_price")).intValue();
                    db.update(
                        "UPDATE users SET loyalty_points = loyalty_points + ? WHERE id = ?",
                        points, orderInfo.get("user_id")
                    );
                    db.update(
                        "INSERT INTO notifications (user_id, title, message, type) VALUES (?, ?, ?, ?)",
                        orderInfo.get("user_id"),
                        "نقاط ولاء 🎁",
                        "حصلت على " + points + " نقطة من طلبك #" + id,
                        "loyalty"
                    );
                }
            } catch (Exception ignored) {}

            response.put("success", true);
            response.put("message", "تم تحديث حالة الطلب");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/restaurants/featured — المطاعم المميزة (أعلى تقييم)
    // ============================================
    @GetMapping("/api/restaurants/featured")
    public Map<String, Object> getFeaturedRestaurants() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT r.*, c.name as category_name FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id = c.id " +
                "WHERE r.is_active = true AND r.rating >= 4.0 " +
                "ORDER BY r.rating DESC LIMIT 10"
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
    // GET /api/restaurants/popular — الأكثر طلباً
    // ============================================
    @GetMapping("/api/restaurants/popular")
    public Map<String, Object> getPopularRestaurants() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT r.*, c.name as category_name, COUNT(o.id) as order_count " +
                "FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id = c.id " +
                "LEFT JOIN orders o ON r.id = o.restaurant_id " +
                "WHERE r.is_active = true " +
                "GROUP BY r.id ORDER BY order_count DESC LIMIT 10"
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
    // GET /api/products/offers — منتجات عليها خصم
    // ============================================
    @GetMapping("/api/products/offers")
    public Map<String, Object> getOffers() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> products = db.queryForList(
                "SELECT p.*, r.name as restaurant_name, r.id as restaurant_id " +
                "FROM products p " +
                "JOIN restaurants r ON p.restaurant_id = r.id " +
                "WHERE p.is_available = true AND p.old_price IS NOT NULL AND p.old_price > p.price " +
                "ORDER BY (p.old_price - p.price) DESC LIMIT 20"
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
    // GET /api/search?q=برغر — بحث في المطاعم والمنتجات
    // ============================================
    @GetMapping("/api/search")
    public Map<String, Object> search(@RequestParam String q) {
        Map<String, Object> response = new HashMap<>();
        try {
            String like = "%" + q + "%";
            // بحث في المطاعم
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT r.*, c.name as category_name FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id = c.id " +
                "WHERE r.is_active = true AND (r.name LIKE ? OR c.name LIKE ?) " +
                "ORDER BY r.rating DESC LIMIT 10",
                like, like
            );
            // بحث في المنتجات
            List<Map<String, Object>> products = db.queryForList(
                "SELECT p.*, r.name as restaurant_name, r.id as restaurant_id " +
                "FROM products p JOIN restaurants r ON p.restaurant_id = r.id " +
                "WHERE p.is_available = true AND r.is_active = true " +
                "AND (p.name LIKE ? OR p.description LIKE ?) " +
                "ORDER BY r.rating DESC LIMIT 20",
                like, like
            );
            response.put("success", true);
            response.put("restaurants", restaurants);
            response.put("products", products);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/products/{id}/extras — إضافات منتج
    // ============================================
    @GetMapping("/api/products/{id}/extras")
    public Map<String, Object> getProductExtras(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            // إنشاء جدول الإضافات الخاص بنا (menu_extras) إذا لم يكن موجوداً
            db.execute(
                "CREATE TABLE IF NOT EXISTS menu_extras (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "product_id INT NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "price DECIMAL(8,2) DEFAULT 0, " +
                "is_available TINYINT DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            List<Map<String, Object>> extras = db.queryForList(
                "SELECT * FROM menu_extras WHERE product_id = ? AND is_available = 1 ORDER BY price ASC",
                id
            );
            response.put("success", true);
            response.put("extras", extras);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/restaurant/products/{id}/extras — إضافة extra للمنتج
    // ============================================
    @PostMapping("/api/restaurant/products/{id}/extras")
    public Map<String, Object> addProductExtra(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);
            // إنشاء الجدول إذا لم يكن موجوداً
            db.execute(
                "CREATE TABLE IF NOT EXISTS menu_extras (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "product_id INT NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "price DECIMAL(8,2) DEFAULT 0, " +
                "is_available TINYINT DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            // التحقق أن المنتج تابع للمطعم
            List<Map<String, Object>> check = db.queryForList(
                "SELECT id FROM products WHERE id = ? AND restaurant_id = ?", id, restaurantId
            );
            if (check.isEmpty()) {
                response.put("success", false);
                response.put("message", "المنتج غير موجود");
                return response;
            }
            String name  = (String) data.get("name");
            Double price = data.get("price") != null ? ((Number) data.get("price")).doubleValue() : 0.0;
            db.update(
                "INSERT INTO menu_extras (product_id, name, price) VALUES (?, ?, ?)",
                id, name, price
            );
            response.put("success", true);
            response.put("message", "تم إضافة الإضافة بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // DELETE /api/restaurant/products/extras/{id} — حذف extra
    // ============================================
    @DeleteMapping("/api/restaurant/products/extras/{id}")
    public Map<String, Object> deleteProductExtra(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("DELETE FROM menu_extras WHERE id = ?", id);
            response.put("success", true);
            response.put("message", "تم حذف الإضافة");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/restaurants/{id}/hours — ساعات عمل المطعم
    // ============================================
    @GetMapping("/api/restaurants/{id}/hours")
    public Map<String, Object> getHours(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> hours = db.queryForList(
                "SELECT * FROM restaurant_hours WHERE restaurant_id = ? ORDER BY day_of_week ASC", id
            );
            response.put("success", true);
            response.put("hours", hours);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/restaurant/stats — إحصائيات المطعم
    // ============================================
    @GetMapping("/api/restaurant/stats")
    public Map<String, Object> getRestaurantStats(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);

            // إجمالي الطلبات
            Long totalOrders = db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE restaurant_id = ?", Long.class, restaurantId
            );
            // إيرادات الطلبات المسلّمة
            Double totalRevenue = db.queryForObject(
                "SELECT COALESCE(SUM(total_price),0) FROM orders WHERE restaurant_id = ? AND status='delivered'",
                Double.class, restaurantId
            );
            // طلبات اليوم
            Long todayOrders = db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE restaurant_id = ? AND DATE(created_at)=CURDATE()",
                Long.class, restaurantId
            );
            // إيرادات اليوم
            Double todayRevenue = db.queryForObject(
                "SELECT COALESCE(SUM(total_price),0) FROM orders WHERE restaurant_id = ? AND status='delivered' AND DATE(created_at)=CURDATE()",
                Double.class, restaurantId
            );
            // طلبات الأسبوع
            Long weekOrders = db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE restaurant_id = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)",
                Long.class, restaurantId
            );
            // الطلبات النشطة الآن
            Long activeOrders = db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE restaurant_id = ? AND status NOT IN ('delivered','cancelled')",
                Long.class, restaurantId
            );
            // متوسط التقييم
            Double avgRating = db.queryForObject(
                "SELECT COALESCE(AVG(restaurant_rating),0) FROM reviews WHERE restaurant_id = ?",
                Double.class, restaurantId
            );
            // أفضل المنتجات مبيعاً
            List<Map<String, Object>> topProducts = db.queryForList(
                "SELECT p.name, SUM(oi.quantity) as sold_count, SUM(oi.price*oi.quantity) as revenue " +
                "FROM order_items oi JOIN products p ON oi.product_id=p.id " +
                "JOIN orders o ON oi.order_id=o.id " +
                "WHERE o.restaurant_id=? AND o.status='delivered' " +
                "GROUP BY p.id, p.name ORDER BY sold_count DESC LIMIT 5",
                restaurantId
            );
            // إيرادات آخر 7 أيام
            List<Map<String, Object>> dailyRevenue = db.queryForList(
                "SELECT DATE(created_at) as day, COUNT(*) as orders, COALESCE(SUM(total_price),0) as revenue " +
                "FROM orders WHERE restaurant_id=? AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) " +
                "GROUP BY DATE(created_at) ORDER BY day ASC",
                restaurantId
            );

            response.put("success", true);
            response.put("totalOrders",   totalOrders   != null ? totalOrders   : 0);
            response.put("totalRevenue",  totalRevenue  != null ? totalRevenue  : 0);
            response.put("todayOrders",   todayOrders   != null ? todayOrders   : 0);
            response.put("todayRevenue",  todayRevenue  != null ? todayRevenue  : 0);
            response.put("weekOrders",    weekOrders    != null ? weekOrders    : 0);
            response.put("activeOrders",  activeOrders  != null ? activeOrders  : 0);
            response.put("avgRating",     avgRating     != null ? avgRating     : 0);
            response.put("topProducts",   topProducts);
            response.put("dailyRevenue",  dailyRevenue);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/restaurant/products/{id}/extras — إضافات منتج بالمجموعات
    // ============================================
    @GetMapping("/api/restaurant/products/{id}/extras")
    public Map<String, Object> getProductExtrasByRestaurant(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);
            // إضافة عمود group_name إذا لم يكن موجوداً
            try { db.execute("ALTER TABLE menu_extras ADD COLUMN group_name VARCHAR(80) DEFAULT 'إضافات'"); } catch (Exception ignored) {}
            List<Map<String, Object>> extras = db.queryForList(
                "SELECT me.* FROM menu_extras me " +
                "JOIN products p ON me.product_id=p.id " +
                "WHERE me.product_id=? AND p.restaurant_id=? AND me.is_available=1 " +
                "ORDER BY me.group_name, me.price ASC",
                id, restaurantId
            );
            response.put("success", true);
            response.put("extras", extras);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/restaurant/products/{id}/extras/group — إضافة extra مع مجموعة
    // Body: { name, price, groupName }
    // ============================================
    @PostMapping("/api/restaurant/products/{id}/extras/group")
    public Map<String, Object> addProductExtraGroup(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);
            try { db.execute("ALTER TABLE menu_extras ADD COLUMN group_name VARCHAR(80) DEFAULT 'إضافات'"); } catch (Exception ignored) {}
            List<Map<String, Object>> check = db.queryForList(
                "SELECT id FROM products WHERE id=? AND restaurant_id=?", id, restaurantId
            );
            if (check.isEmpty()) { response.put("success", false); response.put("message", "المنتج غير موجود"); return response; }
            String name      = (String) data.get("name");
            Double price     = data.get("price") != null ? ((Number) data.get("price")).doubleValue() : 0.0;
            String groupName = data.get("groupName") != null ? (String) data.get("groupName") : "إضافات";
            db.update("INSERT INTO menu_extras (product_id, name, price, group_name) VALUES (?,?,?,?)",
                id, name, price, groupName);
            response.put("success", true);
            response.put("message", "تم إضافة الإضافة بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/restaurant/products/{id}/availability — تغيير حالة التوفر
    // ============================================
    @PutMapping("/api/restaurant/products/{id}/availability")
    public Map<String, Object> toggleProductAvailability(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);
            Boolean available = (Boolean) data.get("isAvailable");
            int rows = db.update(
                "UPDATE products SET is_available=? WHERE id=? AND restaurant_id=?",
                available, id, restaurantId
            );
            response.put("success", rows > 0);
            response.put("message", rows > 0 ? (available ? "المنتج متاح الآن" : "تم إيقاف المنتج مؤقتاً") : "المنتج غير موجود");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/restaurants/featured/city/{cityId} — مطاعم مميزة حسب المدينة
    // ============================================
    @GetMapping("/api/restaurants/featured/city/{cityId}")
    public Map<String, Object> getFeaturedByCity(@PathVariable int cityId) {
        Map<String, Object> response = new HashMap<>();
        try {
            try { db.execute("ALTER TABLE restaurants ADD COLUMN is_featured TINYINT DEFAULT 0"); } catch (Exception ignored) {}
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT r.*, c.name as category_name FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id=c.id " +
                "WHERE r.is_active=1 AND r.is_featured=1 AND (r.city_id=? OR r.city_id IS NULL) " +
                "ORDER BY r.rating DESC LIMIT 10",
                cityId
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
    // PUT /api/restaurant/hours — تعديل ساعات العمل
    // Body: [ {day_of_week:0, open_time:"09:00", close_time:"23:00", is_closed:false}, ... ]
    // ============================================
    @PutMapping("/api/restaurant/hours")
    public Map<String, Object> updateHours(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody List<Map<String, Object>> hours) {
        Map<String, Object> response = new HashMap<>();
        try {
            String restaurantId = getRestaurantIdFromToken(authHeader);
            for (Map<String, Object> h : hours) {
                Integer day       = (Integer) h.get("day_of_week");
                String  openTime  = (String)  h.get("open_time");
                String  closeTime = (String)  h.get("close_time");
                Boolean isClosed  = h.get("is_closed") != null && (Boolean) h.get("is_closed");
                // إدراج أو تحديث
                db.update(
                    "INSERT INTO restaurant_hours (restaurant_id, day_of_week, open_time, close_time, is_closed) " +
                    "VALUES (?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE open_time=VALUES(open_time), close_time=VALUES(close_time), is_closed=VALUES(is_closed)",
                    restaurantId, day, openTime, closeTime, isClosed
                );
            }
            response.put("success", true);
            response.put("message", "تم تحديث ساعات العمل");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }
}