package com.wasalni;

// ============================================
// SecurityConfig - إعدادات الأمان
// وحدد أي API وحتاج توكن وأيها مفتوح للجميع
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
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/**",
                    "/api/admin/**",
                    "/api/restaurants/**",
                    "/api/orders",
                    "/api/orders/**",
                    "/api/products/**",
                    "/api/restaurant/auth/**",
                    "/api/driver/auth/**",
                    "/api/driver/track/**",
                    "/api/driver/**",
                    "/api/cities",
                    "/api/cities/**",
                    "/api/join/**",
                    "/api/health",
                    "/api/debug"
                ).permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }

    // ============================================
    // BCrypt - لتشفير كلمات المرور
    // ============================================
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}