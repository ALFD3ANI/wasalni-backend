package com.wasalni;

// ============================================
// RestaurantAuthController - تسجيل دخول المطاعم
// يتعامل مع:
// POST /api/restaurant/auth/login - تسجيل دخول المطعم
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/restaurant/auth")
public class RestaurantAuthController {

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
    // POST /api/restaurant/auth/login
    // تسجيل دخول المطعم بالـ username وكلمة المرور
    // ============================================
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            // استخراج البيانات
            String username = (String) data.get("username");
            String password = (String) data.get("password");

            // التحقق من وجود البيانات
            if (username == null || password == null) {
                response.put("success", false);
                response.put("message", "اسم المستخدم وكلمة المرور مطلوبان");
                return response;
            }

            // البحث عن المطعم في قاعدة البيانات
            List<Map<String, Object>> restaurants = db.queryForList(
                "SELECT id, name, username, password, is_active FROM restaurants WHERE username = ?",
                username
            );

            // التحقق من وجود المطعم
            if (restaurants.isEmpty()) {
                response.put("success", false);
                response.put("message", "اسم المستخدم أو كلمة المرور غير صحيحة");
                return response;
            }

            Map<String, Object> restaurant = restaurants.get(0);

            // التحقق من أن المطعم مفعل
            if (!(Boolean) restaurant.get("is_active")) {
                response.put("success", false);
                response.put("message", "هذا الحساب غير مفعل، تواصل مع الإدارة");
                return response;
            }

            // التحقق من كلمة المرور
            String storedPassword = (String) restaurant.get("password");
            if (!passwordEncoder.matches(password, storedPassword)) {
                response.put("success", false);
                response.put("message", "اسم المستخدم أو كلمة المرور غير صحيحة");
                return response;
            }

            // إنشاء توكن للمطعم
            String token = jwtUtil.generateToken(restaurant.get("id").toString(), "restaurant");

            // إزالة كلمة المرور من البيانات المرجعة
            restaurant.remove("password");

            // إرجاع البيانات
            response.put("success", true);
            response.put("message", "تم تسجيل الدخول بنجاح");
            response.put("token", token);
            response.put("restaurant", restaurant);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }
}