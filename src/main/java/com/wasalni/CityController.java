package com.wasalni;

// ============================================
// CityController - التحكم بالمدن وطلبات الانضمام
// ويتعامل مع:
// GET    /api/cities                      - كل المدن النشطة
// GET    /api/admin/cities                - كل المدن للأدمن
// POST   /api/admin/cities                - إضافة مدينة
// PUT    /api/admin/cities/{id}           - تعديل مدينة
// PUT    /api/admin/cities/{id}/toggle    - تفعيل/تعطيل مدينة
// DELETE /api/admin/cities/{id}           - حذف مدينة
// POST   /api/join/restaurant             - طلب انضمام مطعم
// POST   /api/join/driver                 - طلب انضمام سائق
// GET    /api/admin/join-requests         - كل طلبات الانضمام
// PUT    /api/admin/join-requests/{id}/approve - قبول طلب
// PUT    /api/admin/join-requests/{id}/reject  - رفض طلب
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
public class CityController {

    // للتعامل مع قاعدة البيانات
    @Autowired
    JdbcTemplate db;

    // لتشفير كلمات المرور
    @Autowired
    PasswordEncoder passwordEncoder;

    // لإنشاء التوكن
    @Autowired
    JwtUtil jwtUtil;

    // ============================================
    // GET /api/cities
    // عرض المدن النشطة فقط - للزبون والجميع
    // ============================================
    @GetMapping("/api/cities")
    public Map<String, Object> getActiveCities() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> cities = db.queryForList(
                "SELECT id, name, name_en, latitude, longitude, radius_km FROM cities WHERE is_active = 1 ORDER BY name"
            );

            response.put("success", true);
            response.put("cities", cities);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/cities
    // عرض كل المدن للأدمن
    // ============================================
    @GetMapping("/api/admin/cities")
    public Map<String, Object> getAllCities(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> cities = db.queryForList(
                "SELECT c.*, " +
                "(SELECT COUNT(*) FROM restaurants WHERE city_id = c.id) as restaurants_count, " +
                "(SELECT COUNT(*) FROM drivers WHERE city_id = c.id) as drivers_count " +
                "FROM cities c ORDER BY c.name"
            );

            response.put("success", true);
            response.put("cities", cities);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/admin/cities
    // إضافة مدينة جديدة
    // ============================================
    @PostMapping("/api/admin/cities")
    public Map<String, Object> addCity(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = (String) data.get("name");
            String nameEn = (String) data.get("nameEn");
            Double latitude = ((Number) data.get("latitude")).doubleValue();
            Double longitude = ((Number) data.get("longitude")).doubleValue();
            Double radiusKm = data.get("radiusKm") != null ? ((Number) data.get("radiusKm")).doubleValue() : 30.0;

            db.update(
                "INSERT INTO cities (name, name_en, latitude, longitude, radius_km) VALUES (?, ?, ?, ?, ?)",
                name, nameEn, latitude, longitude, radiusKm
            );

            // تسجيل في سجل الأدمن
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "إضافة مدينة", "اسم المدينة: " + name
            );

            response.put("success", true);
            response.put("message", "تم إضافة المدينة بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/cities/{id}
    // تعديل مدينة
    // ============================================
    @PutMapping("/api/admin/cities/{id}")
    public Map<String, Object> updateCity(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = (String) data.get("name");
            String nameEn = (String) data.get("nameEn");
            Double latitude = ((Number) data.get("latitude")).doubleValue();
            Double longitude = ((Number) data.get("longitude")).doubleValue();
            Double radiusKm = ((Number) data.get("radiusKm")).doubleValue();

            db.update(
                "UPDATE cities SET name = ?, name_en = ?, latitude = ?, longitude = ?, radius_km = ? WHERE id = ?",
                name, nameEn, latitude, longitude, radiusKm, id
            );

            response.put("success", true);
            response.put("message", "تم تعديل المدينة بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/cities/{id}/toggle
    // تفعيل أو تعطيل مدينة
    // ============================================
    @PutMapping("/api/admin/cities/{id}/toggle")
    public Map<String, Object> toggleCity(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            Boolean isActive = (Boolean) data.get("isActive");

            db.update("UPDATE cities SET is_active = ? WHERE id = ?", isActive, id);

            response.put("success", true);
            response.put("message", isActive ? "تم تفعيل المدينة" : "تم تعطيل المدينة");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // DELETE /api/admin/cities/{id}
    // حذف مدينة
    // ============================================
    @DeleteMapping("/api/admin/cities/{id}")
    public Map<String, Object> deleteCity(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            // التحقق من عدم وجود مطاعم أو سائقين مربوطين بالمدينة
            Map<String, Object> count = db.queryForMap(
                "SELECT (SELECT COUNT(*) FROM restaurants WHERE city_id = ?) as r_count, " +
                "(SELECT COUNT(*) FROM drivers WHERE city_id = ?) as d_count",
                id, id
            );

            long rCount = ((Number) count.get("r_count")).longValue();
            long dCount = ((Number) count.get("d_count")).longValue();

            if (rCount > 0 || dCount > 0) {
                response.put("success", false);
                response.put("message", "لا يمكن حذف المدينة لوجود " + rCount + " مطعم و " + dCount + " سائق مرتبطين بها");
                return response;
            }

            db.update("DELETE FROM cities WHERE id = ?", id);

            response.put("success", true);
            response.put("message", "تم حذف المدينة بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/join/restaurant
    // طلب انضمام مطعم جديد
    // ============================================
    @PostMapping("/api/join/restaurant")
    public Map<String, Object> joinRestaurant(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            // إنشاء جدول طلبات الانضمام إذا لم يكن موجوداً
            db.execute(
                "CREATE TABLE IF NOT EXISTS join_requests (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "type VARCHAR(20) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "email VARCHAR(100), " +
                "phone VARCHAR(20), " +
                "city_id INT, " +
                "password VARCHAR(255), " +
                "description TEXT, " +
                "address VARCHAR(255), " +
                "image VARCHAR(500), " +
                "commercial_reg VARCHAR(50), " +
                "vehicle_type VARCHAR(50), " +
                "vehicle_plate VARCHAR(20), " +
                "status VARCHAR(20) DEFAULT 'pending', " +
                "reject_reason TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            String name = (String) data.get("name");
            String email = (String) data.get("email");
            String phone = (String) data.get("phone");
            String password = (String) data.get("password");
            Integer cityId = ((Number) data.get("cityId")).intValue();
            String description = (String) data.get("description");
            String address = (String) data.get("address");
            String image = (String) data.get("image");
            String commercialReg = (String) data.get("commercialReg");

            // التحقق من الحقول الأساسية
            if (name == null || email == null || phone == null || password == null || cityId == null) {
                response.put("success", false);
                response.put("message", "جميع الحقول الأساسية مطلوبة");
                return response;
            }

            // التحقق من عدم وجود طلب سابق بنفس الإيميل
            List<Map<String, Object>> existing = db.queryForList(
                "SELECT id FROM join_requests WHERE email = ? AND type = 'restaurant' AND status = 'pending'",
                email
            );

            if (!existing.isEmpty()) {
                response.put("success", false);
                response.put("message", "يوجد طلب انضمام سابق قيد المراجعة بنفس الإيميل");
                return response;
            }

            // التحقق من عدم وجود مطعم بنفس الإيميل
            List<Map<String, Object>> existingRest = db.queryForList(
                "SELECT id FROM restaurants WHERE username = ?", email
            );

            if (!existingRest.isEmpty()) {
                response.put("success", false);
                response.put("message", "هذا الإيميل مسجل مسبقاً كمطعم");
                return response;
            }

            // تشفير كلمة المرور
            String hashedPassword = passwordEncoder.encode(password);

            db.update(
                "INSERT INTO join_requests (type, name, email, phone, city_id, password, description, address, image, commercial_reg) " +
                "VALUES ('restaurant', ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                name, email, phone, cityId, hashedPassword, description, address, image, commercialReg
            );

            response.put("success", true);
            response.put("message", "تم إرسال طلب الانضمام بنجاح! سيتم مراجعته والرد عليك قريباً");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/join/driver
    // طلب انضمام سائق جديد
    // ============================================
    @PostMapping("/api/join/driver")
    public Map<String, Object> joinDriver(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String name = (String) data.get("name");
            String email = (String) data.get("email");
            String phone = (String) data.get("phone");
            String password = (String) data.get("password");
            Integer cityId = ((Number) data.get("cityId")).intValue();
            String vehicleType = (String) data.get("vehicleType");
            String vehiclePlate = (String) data.get("vehiclePlate");

            // التحقق من الحقول الأساسية
            if (name == null || email == null || phone == null || password == null || cityId == null) {
                response.put("success", false);
                response.put("message", "جميع الحقول الأساسية مطلوبة");
                return response;
            }

            // التحقق من عدم وجود طلب سابق
            List<Map<String, Object>> existing = db.queryForList(
                "SELECT id FROM join_requests WHERE email = ? AND type = 'driver' AND status = 'pending'",
                email
            );

            if (!existing.isEmpty()) {
                response.put("success", false);
                response.put("message", "يوجد طلب انضمام سابق قيد المراجعة");
                return response;
            }

            // التحقق من عدم وجود سائق بنفس الإيميل
            List<Map<String, Object>> existingDriver = db.queryForList(
                "SELECT id FROM drivers WHERE email = ?", email
            );

            if (!existingDriver.isEmpty()) {
                response.put("success", false);
                response.put("message", "هذا الإيميل مسجل مسبقاً كسائق");
                return response;
            }

            // تشفير كلمة المرور
            String hashedPassword = passwordEncoder.encode(password);

            db.update(
                "INSERT INTO join_requests (type, name, email, phone, city_id, password, vehicle_type, vehicle_plate) " +
                "VALUES ('driver', ?, ?, ?, ?, ?, ?, ?)",
                name, email, phone, cityId, hashedPassword, vehicleType, vehiclePlate
            );

            response.put("success", true);
            response.put("message", "تم إرسال طلب الانضمام بنجاح! سيتم مراجعته والرد عليك قريباً");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/admin/join-requests
    // عرض كل طلبات الانضمام للأدمن
    // ============================================
    @GetMapping("/api/admin/join-requests")
    public Map<String, Object> getJoinRequests(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> requests = db.queryForList(
                "SELECT jr.*, c.name as city_name FROM join_requests jr " +
                "LEFT JOIN cities c ON jr.city_id = c.id " +
                "ORDER BY jr.created_at DESC"
            );

            response.put("success", true);
            response.put("requests", requests);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/join-requests/{id}/approve
    // قبول طلب انضمام — ينشئ حساب تلقائي
    // ============================================
    @PutMapping("/api/admin/join-requests/{id}/approve")
    public Map<String, Object> approveRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            // جلب بيانات الطلب
            List<Map<String, Object>> requests = db.queryForList(
                "SELECT * FROM join_requests WHERE id = ? AND status = 'pending'", id
            );

            if (requests.isEmpty()) {
                response.put("success", false);
                response.put("message", "الطلب غير موجود أو تمت معالجته مسبقاً");
                return response;
            }

            Map<String, Object> req = requests.get(0);
            String type = (String) req.get("type");

            if ("restaurant".equals(type)) {
                // إنشاء حساب مطعم تلقائي
                db.update(
                    "INSERT INTO restaurants (name, username, password, phone, address, description, image, city_id, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)",
                    req.get("name"), req.get("email"), req.get("password"),
                    req.get("phone"), req.get("address"), req.get("description"),
                    req.get("image"), req.get("city_id")
                );
            } else if ("driver".equals(type)) {
                // إنشاء حساب سائق تلقائي — بدون city_id لأنه قد لا يكون في جدول السائقين
                db.update(
                    "INSERT INTO drivers (name, email, phone, password, vehicle_type, vehicle_plate, is_active, is_available) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 1, 1)",
                    req.get("name"), req.get("email"), req.get("phone"),
                    req.get("password"), req.get("vehicle_type"), req.get("vehicle_plate")
                );
            }

            // تحديث حالة الطلب إلى مقبول
            db.update("UPDATE join_requests SET status = 'approved' WHERE id = ?", id);

            // تسجيل في سجل الأدمن
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "قبول طلب انضمام " + type, "الاسم: " + req.get("name")
            );

            response.put("success", true);
            response.put("message", "تم قبول الطلب وإنشاء الحساب بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/admin/join-requests/{id}/reject
    // رفض طلب انضمام مع سبب
    // ============================================
    @PutMapping("/api/admin/join-requests/{id}/reject")
    public Map<String, Object> rejectRequest(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String reason = (String) data.get("reason");

            db.update(
                "UPDATE join_requests SET status = 'rejected', reject_reason = ? WHERE id = ? AND status = 'pending'",
                reason, id
            );

            // تسجيل في سجل الأدمن
            db.update(
                "INSERT INTO admin_logs (admin_name, action, details) VALUES (?, ?, ?)",
                "admin", "رفض طلب انضمام", "رقم الطلب: " + id + " السبب: " + reason
            );

            response.put("success", true);
            response.put("message", "تم رفض الطلب");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/restaurants/city/{cityId}
    // عرض مطاعم مدينة معينة — للزبون
    // ============================================
    @GetMapping("/api/restaurants/city/{cityId}")
    public Map<String, Object> getRestaurantsByCity(@PathVariable int cityId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT r.*, c.name as category_name FROM restaurants r " +
                "LEFT JOIN categories c ON r.category_id = c.id " +
                "WHERE r.city_id = ? AND r.is_active = 1 ORDER BY r.rating DESC",
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
}