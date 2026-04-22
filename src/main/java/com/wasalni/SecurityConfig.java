package com.wasalni;

// ============================================
// SecurityConfig - إعدادات الأمان
// يحدد أي API يحتاج توكن وأيها مفتوح للجميع
// ============================================

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ============================================
    // إعدادات الوصول للـ API
    // ============================================
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // تعطيل CSRF لأننا نستخدم JWT
            .csrf(csrf -> csrf.disable())

            // إعدادات الجلسة - بدون جلسة لأننا نستخدم توكن
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // تحديد من يقدر يوصل لأي API
            .authorizeHttpRequests(auth -> auth
                // APIs مفتوحة للجميع بدون توكن
                .requestMatchers(
                    "/api/auth/**",        // تسجيل الدخول والتسجيل
                  "/api/auth/**",
                  "/api/admin/**",
                  "/api/restaurant/login",
                  "/api/driver/login",
                  "/api/driver/register",
                  "/api/restaurants",
                  "/api/restaurants/**",
                  "/api/products",
                  "/api/products/**",
                  "/api/health",
                  "/api/debug"
                  ).permitAll()

                // باقي الـ APIs تحتاج توكن
                .anyRequest().authenticated()
            );

        return http.build();
    }

    // ============================================
    // BCrypt - لتشفير كلمات المرور
    // يحول كلمة المرور لنص مشفر لا يمكن فكه
    // ============================================
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}