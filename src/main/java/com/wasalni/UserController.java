package com.wasalni;

// ============================================
// UserController - إدارة الزبائن
// يتعامل مع:
// GET    /api/user/profile        - عرض الملف الشخصي
// PUT    /api/user/profile        - تعديل الملف الشخصي
// GET    /api/user/addresses      - عرض العناوين
// POST   /api/user/addresses      - إضافة عنوان جديد
// PUT    /api/user/addresses/{id} - تعديل عنوان
// DELETE /api/user/addresses/{id} - حذف عنوان
// GET    /api/user/orders         - سجل الطلبات
// GET    /api/user/favorites      - المطاعم المفضلة
// POST   /api/user/favorites      - إضافة للمفضلة
// DELETE /api/user/favorites/{id} - حذف من المفضلة
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/user")
public class UserController {

    // للتعامل مع قاعدة البيانات
    @Autowired
    JdbcTemplate db;

    // لتشفير كلمات المرور
    @Autowired
    PasswordEncoder passwordEncoder;

    // لقراءة التوكن
    @Autowired
    JwtUtil jwtUtil;

    // ============================================
    // دالة مساعدة - استخراج رقم الزبون من التوكن
    // ============================================
    private String getUserIdFromToken(String authHeader) {
        // التوكن يكون بالشكل: "Bearer eyJhbGci..."
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getSubject(token);
    }

    // ============================================
    // GET /api/user/profile
    // عرض الملف الشخصي للزبون
    // ============================================
    @GetMapping("/profile")
    public Map<String, Object> getProfile(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            // استخراج رقم الزبون من التوكن
            String userId = getUserIdFromToken(authHeader);

            // جلب بيانات الزبون من قاعدة البيانات
            Map<String, Object> user = db.queryForMap(
                "SELECT id, name, email, phone, avatar, loyalty_points, wallet_balance, created_at FROM users WHERE id = ?",
                userId
            );

            response.put("success", true);
            response.put("user", user);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/user/profile
    // تعديل الملف الشخصي للزبون
    // ============================================
    @PutMapping("/profile")
    public Map<String, Object> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // استخراج البيانات المراد تعديلها
            String name = (String) data.get("name");
            String phone = (String) data.get("phone");
            String avatar = (String) data.get("avatar");

            // تحديث البيانات في قاعدة البيانات
            db.update(
                "UPDATE users SET name = COALESCE(?, name), phone = COALESCE(?, phone), avatar = COALESCE(?, avatar) WHERE id = ?",
                name, phone, avatar, userId
            );

            // جلب البيانات المحدثة
            Map<String, Object> updatedUser = db.queryForMap(
                "SELECT id, name, email, phone, avatar, loyalty_points, wallet_balance FROM users WHERE id = ?",
                userId
            );

            response.put("success", true);
            response.put("message", "تم تحديث الملف الشخصي بنجاح");
            response.put("user", updatedUser);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/user/addresses
    // عرض كل عناوين الزبون
    // ============================================
    @GetMapping("/addresses")
    public Map<String, Object> getAddresses(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // جلب كل العناوين
            List<Map<String, Object>> addresses = db.queryForList(
                "SELECT * FROM addresses WHERE user_id = ? ORDER BY is_default DESC",
                userId
            );

            response.put("success", true);
            response.put("addresses", addresses);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/user/addresses
    // إضافة عنوان جديد للزبون
    // ============================================
    @PostMapping("/addresses")
    public Map<String, Object> addAddress(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            String label = (String) data.get("label");
            String address = (String) data.get("address");
            Double latitude = (Double) data.get("latitude");
            Double longitude = (Double) data.get("longitude");
            Boolean isDefault = data.get("isDefault") != null && (Boolean) data.get("isDefault");

            // إذا العنوان افتراضي، نلغي العناوين الافتراضية الأخرى
            if (isDefault) {
                db.update("UPDATE addresses SET is_default = false WHERE user_id = ?", userId);
            }

            // إضافة العنوان الجديد
            db.update(
                "INSERT INTO addresses (user_id, label, address, latitude, longitude, is_default) VALUES (?, ?, ?, ?, ?, ?)",
                userId, label, address, latitude, longitude, isDefault
            );

            response.put("success", true);
            response.put("message", "تم إضافة العنوان بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // DELETE /api/user/addresses/{id}
    // حذف عنوان
    // ============================================
    @DeleteMapping("/addresses/{id}")
    public Map<String, Object> deleteAddress(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // حذف العنوان - نتأكد إنه تابع للزبون الحالي
            int rows = db.update(
                "DELETE FROM addresses WHERE id = ? AND user_id = ?",
                id, userId
            );

            if (rows > 0) {
                response.put("success", true);
                response.put("message", "تم حذف العنوان بنجاح");
            } else {
                response.put("success", false);
                response.put("message", "العنوان غير موجود");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/user/orders
    // سجل طلبات الزبون
    // ============================================
    @GetMapping("/orders")
    public Map<String, Object> getOrders(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // جلب كل الطلبات مع اسم المطعم
            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.*, r.name as restaurant_name, r.image as restaurant_image " +
                "FROM orders o " +
                "JOIN restaurants r ON o.restaurant_id = r.id " +
                "WHERE o.user_id = ? " +
                "ORDER BY o.created_at DESC",
                userId
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
    // GET /api/user/favorites
    // المطاعم المفضلة للزبون
    // ============================================
    @GetMapping("/favorites")
    public Map<String, Object> getFavorites(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // جلب المطاعم المفضلة مع تفاصيلها
            List<Map<String, Object>> favorites = db.queryForList(
                "SELECT f.id as favorite_id, r.* " +
                "FROM favorites f " +
                "JOIN restaurants r ON f.restaurant_id = r.id " +
                "WHERE f.user_id = ? " +
                "ORDER BY f.created_at DESC",
                userId
            );

            response.put("success", true);
            response.put("favorites", favorites);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // POST /api/user/favorites
    // إضافة مطعم للمفضلة
    // ============================================
    @PostMapping("/favorites")
    public Map<String, Object> addFavorite(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);
            Integer restaurantId = (Integer) data.get("restaurantId");

            // التحقق من عدم وجوده مسبقاً في المفضلة
            List<Map<String, Object>> existing = db.queryForList(
                "SELECT id FROM favorites WHERE user_id = ? AND restaurant_id = ?",
                userId, restaurantId
            );

            if (!existing.isEmpty()) {
                response.put("success", false);
                response.put("message", "المطعم موجود في المفضلة مسبقاً");
                return response;
            }

            // إضافة للمفضلة
            db.update(
                "INSERT INTO favorites (user_id, restaurant_id) VALUES (?, ?)",
                userId, restaurantId
            );

            response.put("success", true);
            response.put("message", "تم إضافة المطعم للمفضلة");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/user/gamification
    // بيانات المستوى والشارات للزبون
    // ============================================
    @GetMapping("/gamification")
    public Map<String, Object> getGamification(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();
        try {
            String userId = getUserIdFromToken(authHeader);

            // جلب النقاط وعدد الطلبات المكتملة
            Map<String, Object> user = db.queryForMap(
                "SELECT u.loyalty_points, " +
                "(SELECT COUNT(*) FROM orders WHERE user_id = u.id AND status = 'delivered') AS total_orders " +
                "FROM users u WHERE u.id = ?", userId);

            long points      = ((Number) user.get("loyalty_points")).longValue();
            long totalOrders = ((Number) user.get("total_orders")).longValue();

            // تحديد المستوى
            String level, levelAr, levelEmoji, nextLevel;
            long   pointsToNext, levelMin, levelMax;

            if (points >= 1000) {
                level = "platinum"; levelAr = "بلاتيني"; levelEmoji = "💎";
                pointsToNext = 0; nextLevel = "الأعلى"; levelMin = 1000; levelMax = 1000;
            } else if (points >= 500) {
                level = "gold"; levelAr = "ذهبي"; levelEmoji = "🥇";
                pointsToNext = 1000 - points; nextLevel = "بلاتيني 💎"; levelMin = 500; levelMax = 1000;
            } else if (points >= 100) {
                level = "silver"; levelAr = "فضي"; levelEmoji = "🥈";
                pointsToNext = 500 - points; nextLevel = "ذهبي 🥇"; levelMin = 100; levelMax = 500;
            } else {
                level = "bronze"; levelAr = "برونزي"; levelEmoji = "🥉";
                pointsToNext = 100 - points; nextLevel = "فضي 🥈"; levelMin = 0; levelMax = 100;
            }

            // نسبة التقدم
            long progressPct = level.equals("platinum") ? 100
                : Math.min(100, (points - levelMin) * 100 / (levelMax - levelMin));

            // الشارات
            List<Map<String, Object>> badges = new ArrayList<>();
            if (totalOrders >= 1) {
                Map<String, Object> b = new HashMap<>();
                b.put("icon", "🛵"); b.put("name", "أول طلب"); b.put("desc", "أكملت أول طلب!"); badges.add(b);
            }
            if (totalOrders >= 5) {
                Map<String, Object> b = new HashMap<>();
                b.put("icon", "⭐"); b.put("name", "زبون نشط"); b.put("desc", "٥ طلبات مكتملة"); badges.add(b);
            }
            if (totalOrders >= 10) {
                Map<String, Object> b = new HashMap<>();
                b.put("icon", "🌟"); b.put("name", "هاوي الطعام"); b.put("desc", "١٠ طلبات مكتملة"); badges.add(b);
            }
            if (points >= 100) {
                Map<String, Object> b = new HashMap<>();
                b.put("icon", "💰"); b.put("name", "جامع النقاط"); b.put("desc", "١٠٠ نقطة أو أكثر"); badges.add(b);
            }
            if (totalOrders >= 20) {
                Map<String, Object> b = new HashMap<>();
                b.put("icon", "👑"); b.put("name", "VIP"); b.put("desc", "٢٠ طلباً مكتملاً"); badges.add(b);
            }

            response.put("success", true);
            response.put("points", points);
            response.put("totalOrders", totalOrders);
            response.put("level", level);
            response.put("levelAr", levelAr);
            response.put("levelEmoji", levelEmoji);
            response.put("pointsToNext", pointsToNext);
            response.put("nextLevel", nextLevel);
            response.put("progressPct", progressPct);
            response.put("badges", badges);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }
        return response;
    }

    // ============================================
    // DELETE /api/user/favorites/{id}
    // حذف مطعم من المفضلة
    // ============================================
    @DeleteMapping("/favorites/{restaurantId}")
    public Map<String, Object> removeFavorite(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int restaurantId) {
        Map<String, Object> response = new HashMap<>();

        try {
            String userId = getUserIdFromToken(authHeader);

            // حذف من المفضلة
            int rows = db.update(
                "DELETE FROM favorites WHERE user_id = ? AND restaurant_id = ?",
                userId, restaurantId
            );

            if (rows > 0) {
                response.put("success", true);
                response.put("message", "تم حذف المطعم من المفضلة");
            } else {
                response.put("success", false);
                response.put("message", "المطعم غير موجود في المفضلة");
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }
}