package com.wasalni;

// ============================================
// AdminController - لوحة تحكم الأدمن
// يتعامل مع:
// POST /api/admin/login                    - تسجيل دخول الأدمن
// GET  /api/admin/dashboard                - إحصائيات عامة
// GET  /api/admin/users                    - كل الزبائن
// PUT  /api/admin/users/{id}/block         - حظر/فك حظر زبون
// GET  /api/admin/restaurants              - كل المطاعم
// POST /api/admin/restaurants              - إضافة مطعم جديد
// PUT  /api/admin/restaurants/{id}/toggle  - تفعيل/تعطيل مطعم
// GET  /api/admin/drivers                  - كل السائقين
// PUT  /api/admin/drivers/{id}/toggle      - تفعيل/تعطيل سائق
// GET  /api/admin/orders                   - كل الطلبات
// GET  /api/admin/coupons                  - كل الكوبونات
// POST /api/admin/coupons                  - إضافة كوبون
// PUT  /api/admin/coupons/{id}/toggle      - تفعيل/تعطيل كوبون
// GET  /api/admin/categories               - كل التصنيفات
// POST /api/admin/categories               - إضافة تصنيف
// GET  /api/admin/banners                  - كل الإعلانات
// POST /api/admin/banners                  - إضافة إعلان
// DELETE /api/admin/banners/{id}           - حذف إعلان
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/admin")
public class AdminController {

    // للتعامل مع قاعدة البيانات
    @Autowired
    JdbcTemplate db;

    // لتشفير كلمات المرور
    @Autowired
    PasswordEncoder passwordEncoder;

    // لإنشاء وقراءة التوكن
    @Autowired
    JwtUtil jwtUtil;

    // بيانات الأدمن الثابتة - في المستقبل تنحط في قاعدة البيانات
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin2030";

    // ============================================
    // POST /api/admin/login
    // تسجيل دخول الأدمن — يتحقق من admin_users أولاً ثم الحساب الثابت
    // ============================================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String username = (String) data.get("username");
            String password = (String) data.get("password");

            // إنشاء جدول admin_users إذا لم يكن موجوداً
            db.execute(
                "CREATE TABLE IF NOT EXISTS admin_users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "username VARCHAR(50) UNIQUE NOT NULL, " +
                "password VARCHAR(255) NOT NULL, " +
                "role VARCHAR(20) DEFAULT 'admin', " +
                "description VARCHAR(200) DEFAULT NULL, " +
                "is_active TINYINT DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            // إضافة عمود description إذا لم يكن موجوداً
            try { db.execute("ALTER TABLE admin_users ADD COLUMN description VARCHAR(200) DEFAULT NULL"); } catch (Exception ignored) {}

            // بذر حسابات تجريبية إذا كان الجدول فارغاً
            Long tableCount = db.queryForObject("SELECT COUNT(*) FROM admin_users", Long.class);
            if (tableCount != null && tableCount == 0) {
                db.update("INSERT INTO admin_users (name, username, password, role, description) VALUES (?,?,?,?,?)",
                    "فريق الدعم", "support_team", passwordEncoder.encode("support123"), "support", "دعم فني الرياض");
                db.update("INSERT INTO admin_users (name, username, password, role, description) VALUES (?,?,?,?,?)",
                    "مشاهد التقارير", "viewer_test", passwordEncoder.encode("viewer123"), "viewer", "قسم المالية");
            }

            // البحث في جدول admin_users أولاً
            List<Map<String, Object>> admins = db.queryForList(
                "SELECT * FROM admin_users WHERE username = ? AND is_active = 1 LIMIT 1", username);

            if (!admins.isEmpty()) {
                Map<String, Object> admin = admins.get(0);
                String storedHash = (String) admin.get("password");
                if (!passwordEncoder.matches(password, storedHash)) {
                    response.put("success", false);
                    response.put("message", "كلمة المرور غير صحيحة");
                    return response;
                }
                String role = (String) admin.get("role");
                String token = jwtUtil.generateToken(username, "admin");
                response.put("success", true);
                response.put("message", "مرحباً بك في لوحة التحكم");
                response.put("token", token);
                response.put("id", admin.get("id"));
                response.put("name", admin.get("name"));
                response.put("role", "admin");
                response.put("adminRole", role);
                response.put("description", admin.get("description"));
                return response;
            }

            // الرجوع للحساب الثابت (SuperAdmin)
            if (!ADMIN_USERNAME.equals(username) || !ADMIN_PASSWORD.equals(password)) {
                response.put("success", false);
                response.put("message", "اسم المستخدم أو كلمة المرور غير صحيحة");
                return response;
            }

            String token = jwtUtil.generateToken("admin", "admin");
            response.put("success", true);
            response.put("message", "مرحباً بك في لوحة التحكم");
            response.put("token", token);
            response.put("id", 0);
            response.put("name", "Bader AL-anazi");
            response.put("role", "admin");
            response.put("adminRole", "superadmin");
            response.put("description", "مدير النظام الأعلى");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/dashboard
    // إحصائيات عامة للوحة التحكم
    // ============================================
    @GetMapping("/dashboard")
    public Map<String, Object> getDashboard(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            // إجمالي الزبائن
            Map<String, Object> usersCount = db.queryForMap("SELECT COUNT(*) as total FROM users");

            // إجمالي المطاعم
            Map<String, Object> restaurantsCount = db.queryForMap("SELECT COUNT(*) as total FROM restaurants");

            // إجمالي السائقين
            Map<String, Object> driversCount = db.queryForMap("SELECT COUNT(*) as total FROM drivers");

            // إجمالي الطلبات
            Map<String, Object> ordersCount = db.queryForMap("SELECT COUNT(*) as total FROM orders");

            // إجمالي الإيرادات
            Map<String, Object> revenue = db.queryForMap(
                "SELECT SUM(total_price) as total FROM orders WHERE status = 'delivered'"
            );

            // طلبات اليوم
            Map<String, Object> todayOrders = db.queryForMap(
                "SELECT COUNT(*) as total FROM orders WHERE DATE(created_at) = CURDATE()"
            );

            // إيرادات اليوم
            Map<String, Object> todayRevenue = db.queryForMap(
                "SELECT SUM(total_price) as total FROM orders WHERE status = 'delivered' AND DATE(created_at) = CURDATE()"
            );

            // الطلبات النشطة
            Map<String, Object> activeOrders = db.queryForMap(
                "SELECT COUNT(*) as total FROM orders WHERE status NOT IN ('delivered', 'cancelled')"
            );

            // تجميع الإحصائيات
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalUsers", usersCount.get("total"));
            stats.put("totalRestaurants", restaurantsCount.get("total"));
            stats.put("totalDrivers", driversCount.get("total"));
            stats.put("totalOrders", ordersCount.get("total"));
            stats.put("totalRevenue", revenue.get("total"));
            stats.put("todayOrders", todayOrders.get("total"));
            stats.put("todayRevenue", todayRevenue.get("total"));
            stats.put("activeOrders", activeOrders.get("total"));

            response.put("success", true);
            response.put("stats", stats);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/users
    // عرض كل الزبائن
    // ============================================
    @GetMapping("/users")
    public Map<String, Object> getUsers(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> users = db.queryForList(
                "SELECT id, name, email, phone, is_active, is_blocked, loyalty_points, wallet_balance, created_at " +
                "FROM users ORDER BY created_at DESC"
            );

            response.put("success", true);
            response.put("users", users);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/users/{id}/block
    // حظر أو فك حظر زبون
    // ============================================
    @PutMapping("/users/{id}/block")
    public Map<String, Object> toggleUserBlock(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            Boolean isBlocked = (Boolean) data.get("isBlocked");

            db.update("UPDATE users SET is_blocked = ? WHERE id = ?", isBlocked, id);

            // تسجيل في سجل الأدمن
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", isBlocked ? "حظر زبون" : "فك حظر زبون", "رقم الزبون: " + id
            );

            response.put("success", true);
            response.put("message", isBlocked ? "تم حظر الزبون" : "تم فك الحظر عن الزبون");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/restaurants
    // عرض كل المطاعم
    // ============================================
    @GetMapping("/restaurants")
    public Map<String, Object> getRestaurants(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT r.*, c.name as category_name, " +
                "(SELECT COUNT(*) FROM orders WHERE restaurant_id = r.id) as total_orders " +
                "FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id = c.id " +
                "ORDER BY r.created_at DESC"
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
    // POST /api/admin/restaurants
    // إضافة مطعم جديد من الأدمن
    // ============================================
    @PostMapping("/restaurants")
    public Map<String, Object> addRestaurant(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = (String) data.get("name");
            String description = (String) data.get("description");
            String phone = (String) data.get("phone");
            String address = (String) data.get("address");
            String image = (String) data.get("image");
            String username = (String) data.get("username");
            String password = (String) data.get("password");
            Integer categoryId = (Integer) data.get("categoryId");

            // تشفير كلمة مرور المطعم
            String hashedPassword = passwordEncoder.encode(password);

            db.update(
                "INSERT INTO restaurants (name, description, phone, address, image, username, password, category_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                name, description, phone, address, image, username, hashedPassword, categoryId
            );

            // تسجيل في سجل الأدمن
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "إضافة مطعم", "اسم المطعم: " + name
            );

            response.put("success", true);
            response.put("message", "تم إضافة المطعم بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/restaurants/{id}/toggle
    // تفعيل أو تعطيل مطعم
    // ============================================
    @PutMapping("/restaurants/{id}/toggle")
    public Map<String, Object> toggleRestaurant(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            Boolean isActive = (Boolean) data.get("isActive");

            db.update("UPDATE restaurants SET is_active = ? WHERE id = ?", isActive, id);

            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", isActive ? "تفعيل مطعم" : "تعطيل مطعم", "رقم المطعم: " + id
            );

            response.put("success", true);
            response.put("message", isActive ? "تم تفعيل المطعم" : "تم تعطيل المطعم");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/drivers
    // عرض كل السائقين
    // ============================================
    @GetMapping("/drivers")
    public Map<String, Object> getDrivers(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> drivers = db.queryForList(
                "SELECT id, name, email, phone, vehicle_type, vehicle_plate, " +
                "is_active, is_available, is_blocked, rating, total_deliveries, wallet_balance, created_at " +
                "FROM drivers ORDER BY created_at DESC"
            );

            response.put("success", true);
            response.put("drivers", drivers);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/drivers/{id}/toggle
    // تفعيل أو تعطيل سائق
    // ============================================
    @PutMapping("/drivers/{id}/toggle")
    public Map<String, Object> toggleDriver(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            Boolean isActive = (Boolean) data.get("isActive");

            db.update("UPDATE drivers SET is_active = ? WHERE id = ?", isActive, id);

            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", isActive ? "تفعيل سائق" : "تعطيل سائق", "رقم السائق: " + id
            );

            response.put("success", true);
            response.put("message", isActive ? "تم تفعيل السائق" : "تم تعطيل السائق");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/orders
    // عرض كل الطلبات
    // ============================================
    @GetMapping("/orders")
    public Map<String, Object> getOrders(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.*, u.name as user_name, r.name as restaurant_name, " +
                "d.name as driver_name " +
                "FROM orders o " +
                "JOIN users u ON o.user_id = u.id " +
                "JOIN restaurants r ON o.restaurant_id = r.id " +
                "LEFT JOIN drivers d ON o.driver_id = d.id " +
                "ORDER BY o.created_at DESC"
            );

            response.put("success", true);
            response.put("orders", orders);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/orders/{id}/status
    // تحديث حالة الطلب وتعيين سائق
    // ============================================
    @PutMapping("/orders/{id}/status")
    public Map<String, Object> updateOrderStatus(
            @PathVariable int id,
            @RequestBody Map<String, Object> data,
            @RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            String status   = (String) data.get("status");
            Object driverIdObj = data.get("driverId");

            if (driverIdObj != null && !driverIdObj.toString().isBlank() && !driverIdObj.toString().equals("null")) {
                int driverId = Integer.parseInt(driverIdObj.toString());
                db.update("UPDATE orders SET status = ?, driver_id = ? WHERE id = ?", status, driverId, id);
                db.update("UPDATE drivers SET is_available = 0 WHERE id = ?", driverId);
            } else {
                db.update("UPDATE orders SET status = ? WHERE id = ?", status, id);
            }

            // عند التوصيل: زيادة عداد التوصيلات للسائق
            if ("delivered".equals(status)) {
                db.update(
                    "UPDATE drivers SET total_deliveries = total_deliveries + 1, is_available = 1 " +
                    "WHERE id = (SELECT driver_id FROM (SELECT driver_id FROM orders WHERE id = ?) AS t)",
                    id
                );
                // إضافة نقاط ولاء للمستخدم
                db.update(
                    "UPDATE users SET loyalty_points = loyalty_points + 10 " +
                    "WHERE id = (SELECT user_id FROM orders WHERE id = ?)",
                    id
                );
            }

            response.put("success", true);
            response.put("message", "تم تحديث حالة الطلب");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/coupons
    // عرض كل الكوبونات
    // ============================================
    @GetMapping("/coupons")
    public Map<String, Object> getCoupons(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> coupons = db.queryForList(
                "SELECT * FROM coupons ORDER BY created_at DESC"
            );

            response.put("success", true);
            response.put("coupons", coupons);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/admin/coupons
    // إضافة كوبون جديد
    // ============================================
    @PostMapping("/coupons")
    public Map<String, Object> addCoupon(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String code = (String) data.get("code");
            String discountType = (String) data.get("discountType");
            Double discountValue = ((Number) data.get("discountValue")).doubleValue();
            Double minOrder = data.get("minOrder") != null ? ((Number) data.get("minOrder")).doubleValue() : 0;
            Integer maxUses = (Integer) data.get("maxUses");

            db.update(
                "INSERT INTO coupons (code, discount_type, discount_value, min_order, max_uses) VALUES (?, ?, ?, ?, ?)",
                code, discountType, discountValue, minOrder, maxUses
            );

            response.put("success", true);
            response.put("message", "تم إضافة الكوبون بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/coupons/{id}/toggle
    // تفعيل أو تعطيل كوبون
    // ============================================
    @PutMapping("/coupons/{id}/toggle")
    public Map<String, Object> toggleCoupon(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            Boolean isActive = (Boolean) data.get("isActive");

            db.update("UPDATE coupons SET is_active = ? WHERE id = ?", isActive, id);

            response.put("success", true);
            response.put("message", isActive ? "تم تفعيل الكوبون" : "تم تعطيل الكوبون");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/categories
    // عرض كل التصنيفات
    // ============================================
    @GetMapping("/categories")
    public Map<String, Object> getCategories(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> categories = db.queryForList(
                "SELECT c.*, COUNT(r.id) as restaurants_count " +
                "FROM categories c " +
                "LEFT JOIN restaurants r ON c.id = r.category_id " +
                "GROUP BY c.id"
            );

            response.put("success", true);
            response.put("categories", categories);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/admin/categories
    // إضافة تصنيف جديد
    // ============================================
    @PostMapping("/categories")
    public Map<String, Object> addCategory(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = (String) data.get("name");
            String icon = (String) data.get("icon");

            db.update("INSERT INTO categories (name, icon) VALUES (?, ?)", name, icon);

            response.put("success", true);
            response.put("message", "تم إضافة التصنيف بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/banners
    // عرض كل الإعلانات
    // ============================================
    @GetMapping("/banners")
    public Map<String, Object> getBanners(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> banners = db.queryForList(
                "SELECT * FROM banners ORDER BY sort_order ASC"
            );

            response.put("success", true);
            response.put("banners", banners);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/admin/banners
    // إضافة إعلان جديد
    // ============================================
    @PostMapping("/banners")
    public Map<String, Object> addBanner(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String title = (String) data.get("title");
            String image = (String) data.get("image");
            String link = (String) data.get("link");
            Integer sortOrder = (Integer) data.get("sortOrder");

            db.update(
                "INSERT INTO banners (title, image, link, sort_order) VALUES (?, ?, ?, ?)",
                title, image, link, sortOrder
            );

            response.put("success", true);
            response.put("message", "تم إضافة الإعلان بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // DELETE /api/admin/banners/{id}
    // حذف إعلان
    // ============================================
    @DeleteMapping("/banners/{id}")
    public Map<String, Object> deleteBanner(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            db.update("DELETE FROM banners WHERE id = ?", id);

            response.put("success", true);
            response.put("message", "تم حذف الإعلان بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/reports/orders-by-status
    // تقرير: توزيع الطلبات حسب الحالة
    // ============================================
    @GetMapping("/reports/orders-by-status")
    public Map<String, Object> reportOrdersByStatus(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> data = db.queryForList(
                "SELECT status, COUNT(*) as count FROM orders GROUP BY status ORDER BY count DESC"
            );
            response.put("success", true);
            response.put("data", data);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/reports/revenue-weekly
    // تقرير: إيرادات آخر 7 أيام
    // ============================================
    @GetMapping("/reports/revenue-weekly")
    public Map<String, Object> reportRevenueWeekly(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> data = db.queryForList(
                "SELECT DATE(created_at) as day, SUM(total_price) as revenue, COUNT(*) as orders " +
                "FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                "GROUP BY DATE(created_at) ORDER BY day ASC"
            );
            response.put("success", true);
            response.put("data", data);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/reports/top-restaurants
    // تقرير: أفضل 10 مطاعم حسب عدد الطلبات
    // ============================================
    @GetMapping("/reports/top-restaurants")
    public Map<String, Object> reportTopRestaurants(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> data = db.queryForList(
                "SELECT r.name, COUNT(o.id) as order_count, SUM(o.total_price) as total_revenue " +
                "FROM restaurants r JOIN orders o ON r.id = o.restaurant_id " +
                "GROUP BY r.id, r.name ORDER BY order_count DESC LIMIT 10"
            );
            response.put("success", true);
            response.put("data", data);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/reports/new-users
    // تقرير: مستخدمون جدد آخر 7 أيام
    // ============================================
    @GetMapping("/reports/new-users")
    public Map<String, Object> reportNewUsers(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> data = db.queryForList(
                "SELECT DATE(created_at) as day, COUNT(*) as new_users " +
                "FROM users WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                "GROUP BY DATE(created_at) ORDER BY day ASC"
            );
            response.put("success", true);
            response.put("data", data);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/admin-users
    // عرض كل مديري النظام
    // ============================================
    @GetMapping("/admin-users")
    public Map<String, Object> getAdminUsers(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> admins = db.queryForList(
                "SELECT id, name, username, role, description, is_active, created_at FROM admin_users ORDER BY created_at DESC"
            );
            response.put("success", true);
            response.put("admins", admins);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/admin/admin-users
    // إضافة مدير نظام جديد
    // ============================================
    @PostMapping("/admin-users")
    public Map<String, Object> addAdminUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String name        = (String) data.get("name");
            String username    = (String) data.get("username");
            String password    = (String) data.get("password");
            String role        = data.get("role") != null ? (String) data.get("role") : "admin";
            String description = data.get("description") != null ? (String) data.get("description") : null;

            String hashed = passwordEncoder.encode(password);
            db.update(
                "INSERT INTO admin_users (name, username, password, role, description) VALUES (?, ?, ?, ?, ?)",
                name, username, hashed, role, description
            );
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "إضافة مدير", "اسم المستخدم: " + username
            );
            response.put("success", true);
            response.put("message", "تم إضافة المدير بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/admin/admin-users/{id}
    // تعديل بيانات مدير نظام
    // ============================================
    @PutMapping("/admin-users/{id}")
    public Map<String, Object> updateAdminUser(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String name        = (String) data.get("name");
            String role        = (String) data.get("role");
            Boolean active     = (Boolean) data.get("isActive");
            String description = data.get("description") != null ? (String) data.get("description") : null;
            db.update(
                "UPDATE admin_users SET name = ?, role = ?, description = ?, is_active = ? WHERE id = ?",
                name, role, description, active, id
            );
            response.put("success", true);
            response.put("message", "تم تحديث بيانات المدير");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // DELETE /api/admin/admin-users/{id}
    // حذف مدير نظام
    // ============================================
    @DeleteMapping("/admin-users/{id}")
    public Map<String, Object> deleteAdminUser(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("DELETE FROM admin_users WHERE id = ?", id);
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "حذف مدير", "رقم المدير: " + id
            );
            response.put("success", true);
            response.put("message", "تم حذف المدير بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/admin/restaurants/{id}/featured
    // تحديد مطعم كمميز أو إلغاء تمييزه
    // ============================================
    @PutMapping("/restaurants/{id}/featured")
    public Map<String, Object> toggleFeatured(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            try { db.execute("ALTER TABLE restaurants ADD COLUMN is_featured TINYINT DEFAULT 0"); } catch (Exception ignored) {}
            Boolean featured = (Boolean) data.get("isFeatured");
            db.update("UPDATE restaurants SET is_featured=? WHERE id=?", featured, id);
            db.update("INSERT INTO admin_logs (admin_name, action, details) VALUES (?,?,?)",
                "admin", featured ? "تمييز مطعم" : "إلغاء تمييز مطعم", "رقم المطعم: " + id);
            response.put("success", true);
            response.put("message", featured ? "تم تمييز المطعم ⭐" : "تم إلغاء التمييز");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/stats/daily
    // إحصائيات الطلبات والإيرادات آخر 30 يوم
    // ============================================
    @GetMapping("/stats/daily")
    public Map<String, Object> getDailyStats(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> daily = db.queryForList(
                "SELECT DATE(created_at) as day, COUNT(*) as orders, " +
                "COALESCE(SUM(total_price),0) as revenue, " +
                "COALESCE(SUM(CASE WHEN status='delivered' THEN total_price ELSE 0 END),0) as delivered_revenue " +
                "FROM orders WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) " +
                "GROUP BY DATE(created_at) ORDER BY day ASC"
            );
            Long weekOrders = db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)", Long.class
            );
            Long monthOrders = db.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)", Long.class
            );
            Double weekRevenue = db.queryForObject(
                "SELECT COALESCE(SUM(total_price),0) FROM orders WHERE status='delivered' AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)", Double.class
            );
            List<Map<String, Object>> topProducts = db.queryForList(
                "SELECT p.name, r.name as restaurant_name, SUM(oi.quantity) as sold " +
                "FROM order_items oi JOIN products p ON oi.product_id=p.id " +
                "JOIN orders o ON oi.order_id=o.id " +
                "JOIN restaurants r ON o.restaurant_id=r.id " +
                "WHERE o.status='delivered' " +
                "GROUP BY p.id, p.name, r.name ORDER BY sold DESC LIMIT 10"
            );
            response.put("success", true);
            response.put("daily", daily);
            response.put("weekOrders",   weekOrders  != null ? weekOrders  : 0);
            response.put("monthOrders",  monthOrders != null ? monthOrders : 0);
            response.put("weekRevenue",  weekRevenue != null ? weekRevenue : 0);
            response.put("topProducts",  topProducts);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/best-admins
    // أفضل الإداريين حسب التذاكر المنجزة
    // ============================================
    @GetMapping("/best-admins")
    public Map<String, Object> getBestAdmins(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS support_tickets (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, subject VARCHAR(255), message TEXT, " +
                "status VARCHAR(20) DEFAULT 'open', assigned_to VARCHAR(50), " +
                "reply TEXT, user_id INT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)"
            );
            try { db.execute("ALTER TABLE support_tickets ADD COLUMN assigned_to VARCHAR(50) DEFAULT NULL"); } catch (Exception ignored) {}
            // أفضل الإداريين بعدد التذاكر المغلقة المعيّنة لهم
            List<Map<String, Object>> best = db.queryForList(
                "SELECT au.name, au.username, au.role, " +
                "COUNT(st.id) as total_assigned, " +
                "SUM(CASE WHEN st.status='closed' THEN 1 ELSE 0 END) as closed_count, " +
                "SUM(CASE WHEN st.status='open' THEN 1 ELSE 0 END) as open_count " +
                "FROM admin_users au " +
                "LEFT JOIN support_tickets st ON st.assigned_to = au.username " +
                "GROUP BY au.id, au.name, au.username, au.role " +
                "ORDER BY closed_count DESC LIMIT 10"
            );
            response.put("success", true);
            response.put("admins", best);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/invite-codes — عرض أكواد الدعوة
    // ============================================
    @GetMapping("/invite-codes")
    public Map<String, Object> getInviteCodes(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS invite_codes (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "code VARCHAR(30) UNIQUE NOT NULL, " +
                "reward_type VARCHAR(20) DEFAULT 'points', " +
                "reward_value DECIMAL(8,2) DEFAULT 0, " +
                "role_granted VARCHAR(20) DEFAULT NULL, " +
                "max_uses INT DEFAULT 1, " +
                "used_count INT DEFAULT 0, " +
                "is_active TINYINT DEFAULT 1, " +
                "expires_at TIMESTAMP DEFAULT NULL, " +
                "created_by VARCHAR(50), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            List<Map<String, Object>> codes = db.queryForList(
                "SELECT * FROM invite_codes ORDER BY created_at DESC"
            );
            response.put("success", true);
            response.put("codes", codes);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/admin/invite-codes — إنشاء كود دعوة
    // Body: { code, rewardType, rewardValue, roleGranted, maxUses, expiresAt }
    // ============================================
    @PostMapping("/invite-codes")
    public Map<String, Object> createInviteCode(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS invite_codes (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "code VARCHAR(30) UNIQUE NOT NULL, " +
                "reward_type VARCHAR(20) DEFAULT 'points', " +
                "reward_value DECIMAL(8,2) DEFAULT 0, " +
                "role_granted VARCHAR(20) DEFAULT NULL, " +
                "max_uses INT DEFAULT 1, " +
                "used_count INT DEFAULT 0, " +
                "is_active TINYINT DEFAULT 1, " +
                "expires_at TIMESTAMP DEFAULT NULL, " +
                "created_by VARCHAR(50), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            String code        = (String) data.get("code");
            String rewardType  = data.get("rewardType")  != null ? (String) data.get("rewardType")  : "points";
            Double rewardValue = data.get("rewardValue")  != null ? ((Number) data.get("rewardValue")).doubleValue() : 0;
            String roleGranted = (String) data.get("roleGranted");
            Integer maxUses    = data.get("maxUses") != null ? ((Number) data.get("maxUses")).intValue() : 1;
            String expiresAt   = (String) data.get("expiresAt");
            db.update(
                "INSERT INTO invite_codes (code, reward_type, reward_value, role_granted, max_uses, expires_at, created_by) VALUES (?,?,?,?,?,?,?)",
                code, rewardType, rewardValue, roleGranted, maxUses,
                expiresAt != null && !expiresAt.isEmpty() ? expiresAt : null,
                "admin"
            );
            db.update("INSERT INTO admin_logs (admin_name, action, details) VALUES (?,?,?)",
                "admin", "إنشاء كود دعوة", "الكود: " + code);
            response.put("success", true);
            response.put("message", "تم إنشاء كود الدعوة بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/admin/invite-codes/{id}/toggle — تفعيل/تعطيل كود
    // ============================================
    @PutMapping("/invite-codes/{id}/toggle")
    public Map<String, Object> toggleInviteCode(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("UPDATE invite_codes SET is_active = NOT is_active WHERE id=?", id);
            response.put("success", true);
            response.put("message", "تم تغيير حالة الكود");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/admin/invite-codes/use — استخدام كود دعوة
    // Body: { code }  — يُستدعى عند تسجيل مستخدم جديد
    // ============================================
    @PostMapping("/invite-codes/use")
    public Map<String, Object> useInviteCode(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String code   = (String) data.get("code");
            String userId = (String) data.get("userId");
            List<Map<String, Object>> codes = db.queryForList(
                "SELECT * FROM invite_codes WHERE code=? AND is_active=1 AND (expires_at IS NULL OR expires_at > NOW()) AND used_count < max_uses",
                code
            );
            if (codes.isEmpty()) {
                response.put("success", false);
                response.put("message", "الكود غير صحيح أو منتهي");
                return response;
            }
            Map<String, Object> ic = codes.get(0);
            db.update("UPDATE invite_codes SET used_count=used_count+1 WHERE id=?", ic.get("id"));
            // تطبيق المكافأة
            String rewardType  = (String) ic.get("reward_type");
            double rewardValue = ic.get("reward_value") != null ? ((Number) ic.get("reward_value")).doubleValue() : 0;
            if ("points".equals(rewardType) && userId != null) {
                db.update("UPDATE users SET loyalty_points=loyalty_points+? WHERE id=?", (int) rewardValue, userId);
            } else if ("wallet".equals(rewardType) && userId != null) {
                db.update("UPDATE users SET wallet_balance=wallet_balance+? WHERE id=?", rewardValue, userId);
            }
            response.put("success", true);
            response.put("message", "تم تطبيق كود الدعوة!");
            response.put("rewardType",  rewardType);
            response.put("rewardValue", rewardValue);
            response.put("roleGranted", ic.get("role_granted"));
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/admin-users/{id}/permissions — قراءة صلاحيات مدير
    // ============================================
    @GetMapping("/admin-users/{id}/permissions")
    public Map<String, Object> getAdminPermissions(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            try { db.execute("ALTER TABLE admin_users ADD COLUMN permissions TEXT DEFAULT NULL"); } catch (Exception ignored) {}
            List<Map<String, Object>> result = db.queryForList(
                "SELECT id, name, username, role, permissions FROM admin_users WHERE id=?", id
            );
            if (result.isEmpty()) { response.put("success", false); response.put("message", "المدير غير موجود"); return response; }
            response.put("success", true);
            response.put("admin", result.get(0));
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/admin/admin-users/{id}/permissions — تحديث صلاحيات مدير (سوبر أدمن فقط)
    // Body: { permissions: "restaurants,orders,support" }
    // ============================================
    @PutMapping("/admin-users/{id}/permissions")
    public Map<String, Object> updateAdminPermissions(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            try { db.execute("ALTER TABLE admin_users ADD COLUMN permissions TEXT DEFAULT NULL"); } catch (Exception ignored) {}
            String permissions = (String) data.get("permissions");
            String role        = data.get("role") != null ? (String) data.get("role") : null;
            if (role != null) {
                db.update("UPDATE admin_users SET permissions=?, role=? WHERE id=?", permissions, role, id);
            } else {
                db.update("UPDATE admin_users SET permissions=? WHERE id=?", permissions, id);
            }
            db.update("INSERT INTO admin_logs (admin_name, action, details) VALUES (?,?,?)",
                "admin", "تحديث صلاحيات مدير", "رقم المدير: " + id + " | صلاحيات: " + permissions);
            response.put("success", true);
            response.put("message", "تم تحديث الصلاحيات");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/tickets/assign — توزيع التذاكر على الإداريين
    // ============================================
    @PutMapping("/tickets/{id}/assign")
    public Map<String, Object> assignTicket(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            try { db.execute("ALTER TABLE support_tickets ADD COLUMN assigned_to VARCHAR(50) DEFAULT NULL"); } catch (Exception ignored) {}
            String assignedTo = (String) data.get("assignedTo");
            db.update("UPDATE support_tickets SET assigned_to=?, status='in_progress' WHERE id=?", assignedTo, id);
            response.put("success", true);
            response.put("message", "تم توزيع التذكرة على " + assignedTo);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/drivers/add — إضافة سائق جديد (POST)
    // ============================================
    @PostMapping("/drivers/add")
    public Map<String, Object> addDriver(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String name        = (String) data.get("name");
            String phone       = (String) data.get("phone");
            String vehicleType = data.get("vehicleType") != null ? (String) data.get("vehicleType") : "motorcycle";
            String area        = data.get("area") != null ? (String) data.get("area") : "";
            String username    = (String) data.get("username");
            String password    = (String) data.get("password");
            Integer cityId     = data.get("cityId") != null ? ((Number) data.get("cityId")).intValue() : null;

            if (username == null || password == null || name == null) {
                response.put("success", false);
                response.put("message", "الاسم واسم المستخدم وكلمة المرور مطلوبة");
                return response;
            }

            String hashed = passwordEncoder.encode(password);
            try { db.execute("ALTER TABLE drivers ADD COLUMN city_id INT DEFAULT NULL"); } catch (Exception ignored) {}

            db.update(
                "INSERT INTO drivers (name, phone, vehicle_type, area, username, password, city_id, is_active) VALUES (?,?,?,?,?,?,?,1)",
                name, phone, vehicleType, area, username, hashed, cityId
            );
            db.update("INSERT INTO admin_logs (admin_name, action, details) VALUES (?,?,?)",
                "admin", "إضافة سائق", "اسم السائق: " + name);
            response.put("success", true);
            response.put("message", "تم تسجيل السائق بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // DELETE /api/admin/coupons/{id}
    // حذف كوبون
    // ============================================
    @DeleteMapping("/coupons/{id}")
    public Map<String, Object> deleteCoupon(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("DELETE FROM coupons WHERE id = ?", id);
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "حذف كوبون", "رقم الكوبون: " + id
            );
            response.put("success", true);
            response.put("message", "تم حذف الكوبون");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/logs
    // سجل تصرفات الأدمن
    // ============================================
    @GetMapping("/logs")
    public Map<String, Object> getLogs(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            db.execute(
                "CREATE TABLE IF NOT EXISTS admin_logs (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "admin_name VARCHAR(100), " +
                "action VARCHAR(255), " +
                "details TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            List<Map<String, Object>> logs = db.queryForList(
                "SELECT * FROM admin_logs ORDER BY created_at DESC LIMIT 100"
            );

            response.put("success", true);
            response.put("logs", logs);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/products
    // كل المنتجات مع اسم المطعم
    // ============================================
    @GetMapping("/products")
    public Map<String, Object> getAllProducts() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> products = db.queryForList(
                "SELECT p.*, r.name as restaurant_name FROM products p " +
                "LEFT JOIN restaurants r ON p.restaurant_id = r.id " +
                "ORDER BY r.name, p.name"
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
    // POST /api/admin/products
    // إضافة منتج لأي مطعم (الأدمن)
    // ============================================
    @PostMapping("/products")
    public Map<String, Object> addProductAdmin(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            int restaurantId = ((Number) data.get("restaurant_id")).intValue();
            String name = (String) data.get("name");
            String description = (String) data.get("description");
            double price = ((Number) data.get("price")).doubleValue();
            double oldPrice = data.get("old_price") != null ? ((Number) data.get("old_price")).doubleValue() : 0;

            db.update(
                "INSERT INTO products (restaurant_id, name, description, price, old_price) VALUES (?,?,?,?,?)",
                restaurantId, name, description, price, oldPrice
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
    // GET /api/admin/gamification-stats
    // إحصائيات نظام المكافآت والمستويات
    // ============================================
    @GetMapping("/gamification-stats")
    public Map<String, Object> getGamificationStats(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            // توزيع المستويات
            Long bronze   = db.queryForObject("SELECT COUNT(*) FROM users WHERE loyalty_points < 100", Long.class);
            Long silver   = db.queryForObject("SELECT COUNT(*) FROM users WHERE loyalty_points >= 100 AND loyalty_points < 500", Long.class);
            Long gold     = db.queryForObject("SELECT COUNT(*) FROM users WHERE loyalty_points >= 500 AND loyalty_points < 1000", Long.class);
            Long platinum = db.queryForObject("SELECT COUNT(*) FROM users WHERE loyalty_points >= 1000", Long.class);

            Map<String, Object> levelDist = new HashMap<>();
            levelDist.put("bronze", bronze != null ? bronze : 0);
            levelDist.put("silver", silver != null ? silver : 0);
            levelDist.put("gold",   gold   != null ? gold   : 0);
            levelDist.put("platinum", platinum != null ? platinum : 0);

            // أفضل 10 زبائن بالنقاط
            List<Map<String, Object>> topUsers = db.queryForList(
                "SELECT name, loyalty_points, " +
                "(SELECT COUNT(*) FROM orders WHERE user_id = users.id AND status='delivered') AS orders " +
                "FROM users WHERE loyalty_points > 0 ORDER BY loyalty_points DESC LIMIT 10");

            // إجمالي النقاط في النظام
            Long totalPoints = db.queryForObject("SELECT COALESCE(SUM(loyalty_points),0) FROM users", Long.class);

            response.put("success", true);
            response.put("levelDist", levelDist);
            response.put("topUsers", topUsers);
            response.put("totalPoints", totalPoints != null ? totalPoints : 0);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // DELETE /api/admin/products/{id}
    // حذف منتج (الأدمن)
    // ============================================
    @DeleteMapping("/products/{id}")
    public Map<String, Object> deleteProductAdmin(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("DELETE FROM products WHERE id = ?", id);
            response.put("success", true);
            response.put("message", "تم حذف المنتج");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/admin/restaurants/{id}
    // تعديل بيانات مطعم كاملة
    // ============================================
    @PutMapping("/restaurants/{id}")
    public Map<String, Object> updateRestaurant(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String name         = (String) data.get("name");
            String phone        = (String) data.get("phone");
            String address      = (String) data.get("address");
            String deliveryTime = (String) data.get("deliveryTime");
            Object feeObj       = data.get("deliveryFee");
            Double deliveryFee  = feeObj != null ? ((Number) feeObj).doubleValue() : null;

            db.update(
                "UPDATE restaurants SET " +
                "name          = COALESCE(?, name), " +
                "phone         = COALESCE(?, phone), " +
                "address       = COALESCE(?, address), " +
                "delivery_time = COALESCE(?, delivery_time), " +
                "delivery_fee  = COALESCE(?, delivery_fee) " +
                "WHERE id = ?",
                name, phone, address, deliveryTime, deliveryFee, id
            );

            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "تعديل مطعم", "رقم المطعم: " + id
            );

            response.put("success", true);
            response.put("message", "تم تحديث بيانات المطعم");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/restaurants/{id}/orders
    // طلبات مطعم معين
    // ============================================
    @GetMapping("/restaurants/{id}/orders")
    public Map<String, Object> getRestaurantOrders(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.id, o.status, o.total_price, o.created_at, " +
                "u.name as user_name, d.name as driver_name " +
                "FROM orders o " +
                "JOIN users u ON o.user_id = u.id " +
                "LEFT JOIN drivers d ON o.driver_id = d.id " +
                "WHERE o.restaurant_id = ? " +
                "ORDER BY o.created_at DESC LIMIT 50",
                id
            );
            response.put("success", true);
            response.put("orders", orders);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/admin/drivers/{id}
    // تعديل بيانات سائق
    // ============================================
    @PutMapping("/drivers/{id}")
    public Map<String, Object> updateDriver(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String name         = (String) data.get("name");
            String phone        = (String) data.get("phone");
            String vehicleType  = (String) data.get("vehicleType");
            String vehiclePlate = (String) data.get("vehiclePlate");

            db.update(
                "UPDATE drivers SET " +
                "name          = COALESCE(?, name), " +
                "phone         = COALESCE(?, phone), " +
                "vehicle_type  = COALESCE(?, vehicle_type), " +
                "vehicle_plate = COALESCE(?, vehicle_plate) " +
                "WHERE id = ?",
                name, phone, vehicleType, vehiclePlate, id
            );

            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "تعديل سائق", "رقم السائق: " + id
            );

            response.put("success", true);
            response.put("message", "تم تحديث بيانات السائق");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // PUT /api/admin/drivers/{id}/block
    // حظر أو فك حظر سائق
    // ============================================
    @PutMapping("/drivers/{id}/block")
    public Map<String, Object> toggleDriverBlock(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            Boolean isBlocked = (Boolean) data.get("isBlocked");

            db.update("UPDATE drivers SET is_blocked = ? WHERE id = ?", isBlocked, id);

            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", isBlocked ? "حظر سائق" : "فك حظر سائق", "رقم السائق: " + id
            );

            response.put("success", true);
            response.put("message", isBlocked ? "تم حظر السائق" : "تم فك الحظر عن السائق");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // GET /api/admin/drivers/{id}/orders
    // سجل توصيلات سائق معين
    // ============================================
    @GetMapping("/drivers/{id}/orders")
    public Map<String, Object> getDriverOrders(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.id, o.status, o.total_price, o.delivery_fee, o.created_at, " +
                "r.name as restaurant_name, u.name as user_name " +
                "FROM orders o " +
                "JOIN restaurants r ON o.restaurant_id = r.id " +
                "JOIN users u ON o.user_id = u.id " +
                "WHERE o.driver_id = ? " +
                "ORDER BY o.created_at DESC LIMIT 50",
                id
            );
            response.put("success", true);
            response.put("orders", orders);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }
}