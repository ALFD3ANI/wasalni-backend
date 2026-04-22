package com.wasalni;

// ============================================
// JwtUtil - أداة إنشاء وقراءة التوكن
// التوكن هو مفتاح سري يعطى للمستخدم بعد تسجيل الدخول
// يستخدمه في كل طلب عشان نعرف هو مين
// ============================================

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // المفتاح السري لتشفير التوكن - لا تشاركه مع أحد
    private static final String SECRET_KEY = "wasalni_secret_key_2024_bader_alanazi_very_long_key";

    // مدة صلاحية التوكن - 7 أيام بالميلي ثانية
    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000L;

    // ============================================
    // إنشاء المفتاح من النص السري
    // ============================================
    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
    }

    // ============================================
    // إنشاء توكن جديد للمستخدم
    // subject = رقم المستخدم أو إيميله
    // role = دوره (user/restaurant/driver/admin)
    // ============================================
    public String generateToken(String subject, String role) {
        return Jwts.builder()
                .setSubject(subject)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(getSigningKey())
                .compact();
    }

    // ============================================
    // استخراج رقم المستخدم من التوكن
    // ============================================
    public String getSubject(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    // ============================================
    // استخراج دور المستخدم من التوكن
    // ============================================
    public String getRole(String token) {
        return (String) Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role");
    }

    // ============================================
    // التحقق من صحة التوكن وعدم انتهاء صلاحيته
    // ============================================
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // التوكن منتهي أو غير صحيح
            return false;
        }
    }
}