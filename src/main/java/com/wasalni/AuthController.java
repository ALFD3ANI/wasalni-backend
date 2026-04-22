package com.wasalni;

// ============================================
// AuthController - تسجيل الدخول والتسجيل
// يتعامل مع:
// POST /api/auth/register - تسجيل حساب جديد
// POST /api/auth/login    - تسجيل الدخول
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

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