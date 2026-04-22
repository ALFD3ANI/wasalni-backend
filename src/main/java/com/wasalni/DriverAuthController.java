package com.wasalni;

// ============================================
// DriverAuthController - تسجيل دخول السائقين
// يتعامل مع:
// POST /api/driver/auth/register - تسجيل سائق جديد
// POST /api/driver/auth/login    - تسجيل دخول السائق
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/driver/auth")
public class DriverAuthController {

    // للتعامل مع قاعدة البيانات
    @Autowired
    JdbcTemplate db;

    // لتشفير والتحقق من كلمات المرور
    @Autowired
    PasswordEncoder passwordEncoder;

    // لإنشاء التوكن
    @Autowired
    JwtUtil jwtUtil;

    // ============================================
    // POST /api/driver/auth/register
    // تسجيل سائق جديد
    // ============================================
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            // استخراج البيانات
            String name = (String) data.get("name");
            String email = (String) data.get("email");
            String phone = (String) data.get("phone");
            String password = (String) data.get("password");
            String vehicleType = (String) data.get("vehicleType");
            String vehiclePlate = (String) data.get("vehiclePlate");

            // التحقق من وجود الحقول الأساسية
            if (name == null || email == null || phone == null || password == null) {
                response.put("success", false);
                response.put("message", "جميع الحقول مطلوبة");
                return response;
            }

            // التحقق من أن الإيميل أو الجوال مو مسجل مسبقاً
            List<Map<String, Object>> existing = db.queryForList(
                "SELECT id FROM drivers WHERE email = ? OR phone = ?", email, phone
            );

            if (!existing.isEmpty()) {
                response.put("success", false);
                response.put("message", "الإيميل أو رقم الجوال مسجل مسبقاً");
                return response;
            }

            // تشفير كلمة المرور
            String hashedPassword = passwordEncoder.encode(password);

            // حفظ السائق في قاعدة البيانات
            db.update(
                "INSERT INTO drivers (name, email, phone, password, vehicle_type, vehicle_plate) VALUES (?, ?, ?, ?, ?, ?)",
                name, email, phone, hashedPassword, vehicleType, vehiclePlate
            );

            // جلب بيانات السائق الجديد
            Map<String, Object> newDriver = db.queryForMap(
                "SELECT id, name, email, phone, vehicle_type, vehicle_plate FROM drivers WHERE email = ?",
                email
            );

            // إنشاء توكن للسائق
            String token = jwtUtil.generateToken(newDriver.get("id").toString(), "driver");

            // إرجاع البيانات
            response.put("success", true);
            response.put("message", "تم إنشاء حساب السائق بنجاح");
            response.put("token", token);
            response.put("driver", newDriver);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/driver/auth/login
    // تسجيل دخول سائق موجود
    // ============================================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            // استخراج البيانات
            String email = (String) data.get("email");
            String password = (String) data.get("password");

            // التحقق من وجود البيانات
            if (email == null || password == null) {
                response.put("success", false);
                response.put("message", "الإيميل وكلمة المرور مطلوبان");
                return response;
            }

            // البحث عن السائق في قاعدة البيانات
            List<Map<String, Object>> drivers = db.queryForList(
                "SELECT id, name, email, phone, password, is_active, is_blocked, vehicle_type, vehicle_plate, rating, wallet_balance FROM drivers WHERE email = ?",
                email
            );

            // التحقق من وجود الحساب
            if (drivers.isEmpty()) {
                response.put("success", false);
                response.put("message", "الإيميل أو كلمة المرور غير صحيحة");
                return response;
            }

            Map<String, Object> driver = drivers.get(0);

            // التحقق من أن الحساب مو محظور
            if ((Boolean) driver.get("is_blocked")) {
                response.put("success", false);
                response.put("message", "هذا الحساب محظور، تواصل مع الدعم");
                return response;
            }

            // التحقق من أن الحساب مفعل
            if (!(Boolean) driver.get("is_active")) {
                response.put("success", false);
                response.put("message", "حسابك قيد المراجعة، سيتم التفعيل قريباً");
                return response;
            }

            // التحقق من كلمة المرور
            String storedPassword = (String) driver.get("password");
            if (!passwordEncoder.matches(password, storedPassword)) {
                response.put("success", false);
                response.put("message", "الإيميل أو كلمة المرور غير صحيحة");
                return response;
            }

            // إنشاء توكن للسائق
            String token = jwtUtil.generateToken(driver.get("id").toString(), "driver");

            // إزالة كلمة المرور من البيانات المرجعة
            driver.remove("password");

            // إرجاع البيانات
            response.put("success", true);
            response.put("message", "تم تسجيل الدخول بنجاح");
            response.put("token", token);
            response.put("driver", driver);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }
}