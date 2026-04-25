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
}