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
            db.execute(
                "CREATE TABLE IF NOT EXISTS cities (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "name_en VARCHAR(100), " +
                "latitude DECIMAL(10,7), " +
                "longitude DECIMAL(10,7), " +
                "radius_km INT DEFAULT 30, " +
                "is_active TINYINT DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            // بذر المدن الافتراضية إذا كانت الجدول فارغاً
            int cityCount = db.queryForObject("SELECT COUNT(*) FROM cities", Integer.class);
            if (cityCount == 0) {
                String[][] seedCities = {
                    {"الرياض", "Riyadh", "24.7136", "46.6753"},
                    {"جدة", "Jeddah", "21.4858", "39.1925"},
                    {"مكة المكرمة", "Mecca", "21.3891", "39.8579"},
                    {"المدينة المنورة", "Medina", "24.5247", "39.5692"},
                    {"الدمام", "Dammam", "26.4207", "50.0888"},
                    {"الخبر", "Khobar", "26.2172", "50.1971"},
                    {"الطائف", "Taif", "21.2854", "40.4150"},
                    {"تبوك", "Tabuk", "28.3998", "36.5715"},
                    {"أبها", "Abha", "18.2164", "42.5053"},
                    {"القصيم", "Qassim", "26.3260", "43.9750"}
                };
                for (String[] c : seedCities) {
                    db.update("INSERT INTO cities (name, name_en, latitude, longitude, is_active) VALUES (?,?,?,?,1)",
                        c[0], c[1], c[2], c[3]);
                }
            }

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
            // إنشاء جدول طلبات الانضمام مع جميع الحقول الجديدة
            db.execute(
                "CREATE TABLE IF NOT EXISTS join_requests (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "type VARCHAR(20) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "legal_name VARCHAR(150), " +
                "email VARCHAR(100), " +
                "phone VARCHAR(20), " +
                "manager_phone VARCHAR(20), " +
                "city_id INT, " +
                "password VARCHAR(255), " +
                "description TEXT, " +
                "address VARCHAR(255), " +
                "image VARCHAR(500), " +
                "commercial_reg VARCHAR(50), " +
                "cr_doc_url VARCHAR(500), " +
                "health_cert_url VARCHAR(500), " +
                "vehicle_type VARCHAR(50), " +
                "vehicle_plate VARCHAR(20), " +
                "vehicle_model VARCHAR(100), " +
                "vehicle_year VARCHAR(10), " +
                "license_img_url VARCHAR(500), " +
                "id_img_url VARCHAR(500), " +
                "insurance_doc_url VARCHAR(500), " +
                "age INT, " +
                "status VARCHAR(20) DEFAULT 'pending', " +
                "reject_reason TEXT, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            // إضافة الأعمدة الجديدة إن لم تكن موجودة (للجداول القديمة)
            String[] newCols = {
                "ALTER TABLE join_requests ADD COLUMN legal_name VARCHAR(150)",
                "ALTER TABLE join_requests ADD COLUMN manager_phone VARCHAR(20)",
                "ALTER TABLE join_requests ADD COLUMN cr_doc_url VARCHAR(500)",
                "ALTER TABLE join_requests ADD COLUMN health_cert_url VARCHAR(500)",
                "ALTER TABLE join_requests ADD COLUMN vehicle_model VARCHAR(100)",
                "ALTER TABLE join_requests ADD COLUMN vehicle_year VARCHAR(10)",
                "ALTER TABLE join_requests ADD COLUMN license_img_url VARCHAR(500)",
                "ALTER TABLE join_requests ADD COLUMN id_img_url VARCHAR(500)",
                "ALTER TABLE join_requests ADD COLUMN insurance_doc_url VARCHAR(500)",
                "ALTER TABLE join_requests ADD COLUMN age INT",
                "ALTER TABLE join_requests ADD COLUMN manager_phone VARCHAR(20)"
            };
            for (String col : newCols) { try { db.execute(col); } catch (Exception ignored) {} }

            String name         = (String) data.get("name");
            String legalName    = (String) data.get("legalName");
            String email        = (String) data.get("email");
            String phone        = (String) data.get("phone");
            String managerPhone = (String) data.get("managerPhone");
            String password     = (String) data.get("password");
            Integer cityId      = data.get("cityId") != null ? ((Number) data.get("cityId")).intValue() : null;
            String description  = (String) data.get("description");
            String address      = (String) data.get("address");
            String image        = (String) data.get("image");
            String commercialReg  = (String) data.get("commercialReg");
            String crDocUrl       = (String) data.get("crDocUrl");
            String healthCertUrl  = (String) data.get("healthCertUrl");

            // التحقق من الحقول الإلزامية للمطعم
            if (name == null || name.isEmpty() ||
                email == null || email.isEmpty() ||
                phone == null || phone.isEmpty() ||
                password == null || password.isEmpty() ||
                commercialReg == null || commercialReg.isEmpty() ||
                address == null || address.isEmpty() ||
                cityId == null) {
                response.put("success", false);
                response.put("message", "جميع الحقول الإلزامية مطلوبة (الاسم، الإيميل، الجوال، العنوان، رقم السجل التجاري)");
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
                "INSERT INTO join_requests (type, name, legal_name, email, phone, manager_phone, city_id, password, description, address, image, commercial_reg, cr_doc_url, health_cert_url) " +
                "VALUES ('restaurant', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                name, legalName, email, phone, managerPhone, cityId, hashedPassword, description, address, image, commercialReg, crDocUrl, healthCertUrl
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
            String name           = (String) data.get("name");
            String email          = (String) data.get("email");
            String phone          = (String) data.get("phone");
            String password       = (String) data.get("password");
            Integer cityId        = data.get("cityId") != null ? ((Number) data.get("cityId")).intValue() : null;
            String vehicleType    = (String) data.get("vehicleType");
            String vehiclePlate   = (String) data.get("vehiclePlate");
            String vehicleModel   = (String) data.get("vehicleModel");
            String vehicleYear    = (String) data.get("vehicleYear");
            String licenseImgUrl  = (String) data.get("licenseImgUrl");
            String idImgUrl       = (String) data.get("idImgUrl");
            String insuranceDocUrl= (String) data.get("insuranceDocUrl");
            Integer age           = data.get("age") != null ? ((Number) data.get("age")).intValue() : null;

            // التحقق من الحقول الإلزامية للسائق
            if (name == null || name.isEmpty() ||
                email == null || email.isEmpty() ||
                phone == null || phone.isEmpty() ||
                password == null || password.isEmpty() ||
                vehiclePlate == null || vehiclePlate.isEmpty() ||
                cityId == null) {
                response.put("success", false);
                response.put("message", "جميع الحقول الإلزامية مطلوبة");
                return response;
            }

            // التحقق من الحد الأدنى للسن
            if (age != null && age < 18) {
                response.put("success", false);
                response.put("message", "يجب أن يكون عمرك 18 سنة أو أكثر للانضمام كسائق");
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
                "INSERT INTO join_requests (type, name, email, phone, city_id, password, vehicle_type, vehicle_plate, vehicle_model, vehicle_year, license_img_url, id_img_url, insurance_doc_url, age) " +
                "VALUES ('driver', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                name, email, phone, cityId, hashedPassword, vehicleType, vehiclePlate, vehicleModel, vehicleYear, licenseImgUrl, idImgUrl, insuranceDocUrl, age
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
            db.execute(
                "CREATE TABLE IF NOT EXISTS cities (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "name VARCHAR(100) NOT NULL, " +
                "name_en VARCHAR(100), " +
                "latitude DECIMAL(10,7), " +
                "longitude DECIMAL(10,7), " +
                "radius_km INT DEFAULT 30, " +
                "is_active TINYINT DEFAULT 1, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
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
            List<Map<String, Object>> requests = db.queryForList(
                "SELECT jr.*, " +
                "(SELECT c.name FROM cities c WHERE c.id = jr.city_id LIMIT 1) as city_name " +
                "FROM join_requests jr ORDER BY jr.created_at DESC"
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
                // إضافة الأعمدة الجديدة للمطاعم إن لم تكن موجودة
                String[] rCols = {
                    "ALTER TABLE restaurants ADD COLUMN legal_name VARCHAR(150)",
                    "ALTER TABLE restaurants ADD COLUMN commercial_reg VARCHAR(50)",
                    "ALTER TABLE restaurants ADD COLUMN cr_doc_url VARCHAR(500)",
                    "ALTER TABLE restaurants ADD COLUMN health_cert_url VARCHAR(500)",
                    "ALTER TABLE restaurants ADD COLUMN manager_phone VARCHAR(20)"
                };
                for (String col : rCols) { try { db.execute(col); } catch (Exception ignored) {} }

                // إنشاء حساب مطعم تلقائي مع الحقول الجديدة
                db.update(
                    "INSERT INTO restaurants (name, username, password, phone, address, description, image, city_id, " +
                    "legal_name, commercial_reg, cr_doc_url, health_cert_url, manager_phone, is_active) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)",
                    req.get("name"), req.get("email"), req.get("password"),
                    req.get("phone"), req.get("address"), req.get("description"),
                    req.get("image"), req.get("city_id"),
                    req.get("legal_name"), req.get("commercial_reg"), req.get("cr_doc_url"),
                    req.get("health_cert_url"), req.get("manager_phone")
                );
            } else if ("driver".equals(type)) {
                // إضافة الأعمدة الجديدة للسائقين إن لم تكن موجودة
                String[] dCols = {
                    "ALTER TABLE drivers ADD COLUMN vehicle_model VARCHAR(100)",
                    "ALTER TABLE drivers ADD COLUMN vehicle_year VARCHAR(10)",
                    "ALTER TABLE drivers ADD COLUMN license_img_url VARCHAR(500)",
                    "ALTER TABLE drivers ADD COLUMN id_img_url VARCHAR(500)",
                    "ALTER TABLE drivers ADD COLUMN insurance_doc_url VARCHAR(500)",
                    "ALTER TABLE drivers ADD COLUMN age INT"
                };
                for (String col : dCols) { try { db.execute(col); } catch (Exception ignored) {} }

                // إنشاء حساب سائق تلقائي مع الحقول الجديدة
                db.update(
                    "INSERT INTO drivers (name, email, phone, password, vehicle_type, vehicle_plate, " +
                    "vehicle_model, vehicle_year, license_img_url, id_img_url, insurance_doc_url, age, is_active, is_available) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1)",
                    req.get("name"), req.get("email"), req.get("phone"),
                    req.get("password"), req.get("vehicle_type"), req.get("vehicle_plate"),
                    req.get("vehicle_model"), req.get("vehicle_year"),
                    req.get("license_img_url"), req.get("id_img_url"), req.get("insurance_doc_url"),
                    req.get("age")
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