package com.wasalni;

// ============================================
// AuthController - تسجيل الدخول والتسجيل
// يتعامل مع:
// POST /api/auth/register     - تسجيل حساب جديد
// POST /api/auth/login        - تسجيل الدخول بالإيميل
// POST /api/auth/send-otp     - إرسال رمز تحقق OTP
// POST /api/auth/verify-otp   - التحقق من الرمز
// POST /api/auth/login-phone  - تسجيل الدخول برقم الجوال
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.Random;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/auth")
public class AuthController {

    // ============================================
    // الأدوات المستخدمة
    // ============================================

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
    // POST /api/auth/send-otp
    // إرسال رمز تحقق OTP مؤقت (6 أرقام، صالح 5 دقائق)
    // يُعرض الرمز في الـ response لأن المشروع بدون SMS/Email حقيقي
    // ============================================
    @PostMapping("/send-otp")
    public Map<String, Object> sendOtp(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = (String) data.get("email");
            String phone = (String) data.get("phone");
            String type  = data.get("type") != null ? (String) data.get("type") : "login";

            if (email == null && phone == null) {
                response.put("success", false);
                response.put("message", "أدخل الإيميل أو رقم الجوال");
                return response;
            }

            // إنشاء الجدول إذا لم يكن موجوداً
            db.execute(
                "CREATE TABLE IF NOT EXISTS otp_codes (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "email VARCHAR(255), " +
                "phone VARCHAR(20), " +
                "code VARCHAR(6) NOT NULL, " +
                "type VARCHAR(20) DEFAULT 'login', " +
                "expires_at TIMESTAMP NOT NULL, " +
                "is_used TINYINT DEFAULT 0, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            // رمز عشوائي 6 أرقام
            String code = String.format("%06d", new Random().nextInt(999999));

            // حذف الرموز القديمة للمستخدم نفسه
            if (email != null) {
                db.update("DELETE FROM otp_codes WHERE email = ?", email);
            } else {
                db.update("DELETE FROM otp_codes WHERE phone = ?", phone);
            }

            // حفظ الرمز الجديد مع انتهاء صلاحية بعد 5 دقائق
            db.update(
                "INSERT INTO otp_codes (email, phone, code, type, expires_at) " +
                "VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 5 MINUTE))",
                email, phone, code, type
            );

            response.put("success", true);
            response.put("message", "تم إرسال رمز التحقق");
            // للعرض التجريبي فقط — في الإنتاج لا يُعرض الرمز
            response.put("code_demo", code);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/auth/verify-otp
    // التحقق من صحة رمز OTP
    // ============================================
    @PostMapping("/verify-otp")
    public Map<String, Object> verifyOtp(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = (String) data.get("email");
            String phone = (String) data.get("phone");
            String code  = (String) data.get("code");

            if (code == null || code.isEmpty()) {
                response.put("success", false);
                response.put("message", "أدخل رمز التحقق");
                return response;
            }

            // البحث عن رمز صالح غير منتهي ولم يُستخدم
            String field = email != null ? "email" : "phone";
            String val   = email != null ? email : phone;
            List<Map<String, Object>> otps = db.queryForList(
                "SELECT * FROM otp_codes WHERE " + field + " = ? AND code = ? AND is_used = 0 AND expires_at > NOW()",
                val, code
            );

            if (otps.isEmpty()) {
                response.put("success", false);
                response.put("message", "الرمز غير صحيح أو انتهت صلاحيته");
                return response;
            }

            // تحديد الرمز كمستخدم لمنع إعادة الاستخدام
            db.update("UPDATE otp_codes SET is_used = 1 WHERE id = ?", otps.get(0).get("id"));

            response.put("success", true);
            response.put("message", "تم التحقق بنجاح ✅");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/auth/login-phone
    // تسجيل الدخول برقم الجوال عبر OTP
    // إذا المستخدم جديد يُنشئ له حساب تلقائياً
    // ============================================
    @PostMapping("/login-phone")
    public Map<String, Object> loginPhone(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        try {
            String phone = (String) data.get("phone");
            String code  = (String) data.get("code");

            if (phone == null || code == null) {
                response.put("success", false);
                response.put("message", "رقم الجوال والرمز مطلوبان");
                return response;
            }

            // التحقق من الـ OTP
            List<Map<String, Object>> otps = db.queryForList(
                "SELECT * FROM otp_codes WHERE phone = ? AND code = ? AND is_used = 0 AND expires_at > NOW()",
                phone, code
            );

            if (otps.isEmpty()) {
                response.put("success", false);
                response.put("message", "رمز التحقق غير صحيح أو منتهي الصلاحية");
                return response;
            }

            // تحديد الرمز كمستخدم
            db.update("UPDATE otp_codes SET is_used = 1 WHERE id = ?", otps.get(0).get("id"));

            // البحث عن المستخدم برقم الجوال
            List<Map<String, Object>> users = db.queryForList(
                "SELECT id, name, email, phone FROM users WHERE phone = ?", phone
            );

            Map<String, Object> user;
            if (users.isEmpty()) {
                // إنشاء حساب تلقائي للمستخدم الجديد
                String autoName  = "مستخدم " + phone.substring(Math.max(0, phone.length() - 4));
                String autoEmail = phone + "@wasalni.app";
                String autoPass  = passwordEncoder.encode(phone + "_ws2024");
                db.update(
                    "INSERT INTO users (name, email, phone, password) VALUES (?, ?, ?, ?)",
                    autoName, autoEmail, phone, autoPass
                );
                user = db.queryForMap(
                    "SELECT id, name, email, phone FROM users WHERE phone = ?", phone
                );
            } else {
                user = users.get(0);
            }

            String token = jwtUtil.generateToken(user.get("id").toString(), "user");

            response.put("success", true);
            response.put("message", "تم تسجيل الدخول بنجاح");
            response.put("token", token);
            response.put("id",    user.get("id"));
            response.put("name",  user.get("name"));
            response.put("email", user.get("email"));
            response.put("phone", user.get("phone"));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // POST /api/auth/register
    // تسجيل زبون جديد
    // ============================================
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            // استخراج البيانات من الطلب
            String name = (String) data.get("name");
            String email = (String) data.get("email");
            String phone = (String) data.get("phone");
            String password = (String) data.get("password");

            // التحقق من أن كل الحقول موجودة
            if (name == null || email == null || phone == null || password == null) {
                response.put("success", false);
                response.put("message", "جميع الحقول مطلوبة");
                return response;
            }

            // التحقق من أن الإيميل مو مسجل مسبقاً
            List<Map<String, Object>> existing = db.queryForList(
                "SELECT id FROM users WHERE email = ? OR phone = ?", email, phone
            );

            if (!existing.isEmpty()) {
                response.put("success", false);
                response.put("message", "الإيميل أو رقم الجوال مسجل مسبقاً");
                return response;
            }

            // تشفير كلمة المرور قبل الحفظ
            String hashedPassword = passwordEncoder.encode(password);

            // حفظ الزبون في قاعدة البيانات
            db.update(
                "INSERT INTO users (name, email, phone, password) VALUES (?, ?, ?, ?)",
                name, email, phone, hashedPassword
            );

            // جلب رقم الزبون الجديد
            Map<String, Object> newUser = db.queryForMap(
                "SELECT id, name, email, phone FROM users WHERE email = ?", email
            );

            // إنشاء توكن للزبون
            String token = jwtUtil.generateToken(newUser.get("id").toString(), "user");

            // إرجاع البيانات للفرونت
            response.put("success", true);
            response.put("message", "تم إنشاء الحساب بنجاح");
            response.put("token", token);
            response.put("user", newUser);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/auth/login
    // تسجيل دخول زبون موجود
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

            // البحث عن الزبون في قاعدة البيانات
            List<Map<String, Object>> users = db.queryForList(
                "SELECT id, name, email, phone, password, is_blocked FROM users WHERE email = ?",
                email
            );

            // التحقق من وجود الحساب
            if (users.isEmpty()) {
                response.put("success", false);
                response.put("message", "الإيميل أو كلمة المرور غير صحيحة");
                return response;
            }

            Map<String, Object> user = users.get(0);

            // التحقق من أن الحساب مو محظور
            if ((Boolean) user.get("is_blocked")) {
                response.put("success", false);
                response.put("message", "هذا الحساب محظور، تواصل مع الدعم");
                return response;
            }

            // التحقق من كلمة المرور
            String storedPassword = (String) user.get("password");
            if (!passwordEncoder.matches(password, storedPassword)) {
                response.put("success", false);
                response.put("message", "الإيميل أو كلمة المرور غير صحيحة");
                return response;
            }

            // إنشاء توكن جديد
            String token = jwtUtil.generateToken(user.get("id").toString(), "user");

            // إزالة كلمة المرور من البيانات المرجعة
            user.remove("password");

            // إرجاع البيانات
            response.put("success", true);
            response.put("message", "تم تسجيل الدخول بنجاح");
            response.put("token", token);
            response.put("user", user);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }
}