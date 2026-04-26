package com.wasalni;

// ======================================================
// CouponController.java — نظام الكوبونات
// يتيح للزبون التحقق من كوبون قبل الطلب
// ويتيح للأدمن إدارة الكوبونات كاملاً
// ======================================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/coupons")
@CrossOrigin(origins = "*")
public class CouponController {

    // أداة التواصل مع قاعدة البيانات
    @Autowired
    private JdbcTemplate db;

    // ======================================================
    // 1. الزبون يتحقق من كوبون قبل الطلب
    // GET /api/coupons/check?code=WASALNI10&amount=50
    // ======================================================
    @GetMapping("/check")
    public Map<String, Object> checkCoupon(
            @RequestParam String code,
            @RequestParam double amount) {

        Map<String, Object> response = new HashMap<>();

        try {
            // نبحث عن الكوبون في قاعدة البيانات
            List<Map<String, Object>> results = db.queryForList(
                "SELECT * FROM coupons WHERE code = ? AND is_active = 1", code
            );

            // إذا ما وجد الكوبون
            if (results.isEmpty()) {
                response.put("success", false);
                response.put("message", "الكوبون غير صحيح أو منتهي");
                return response;
            }

            Map<String, Object> coupon = results.get(0);

            // نتحقق إن الكوبون ما انتهت صلاحيته
            if (coupon.get("expires_at") != null) {
                java.sql.Timestamp expiresAt = (java.sql.Timestamp) coupon.get("expires_at");
                if (expiresAt.before(new java.sql.Timestamp(System.currentTimeMillis()))) {
                    response.put("success", false);
                    response.put("message", "انتهت صلاحية هذا الكوبون");
                    return response;
                }
            }

            // نتحقق إن عدد الاستخدامات ما تجاوز الحد
            int maxUses = coupon.get("max_uses") != null ? (int) coupon.get("max_uses") : Integer.MAX_VALUE;
            int usedCount = (int) coupon.get("used_count");
            if (usedCount >= maxUses) {
                response.put("success", false);
                response.put("message", "تم استنفاد هذا الكوبون");
                return response;
            }

            // نتحقق إن مبلغ الطلب يتجاوز الحد الأدنى
            double minOrder = ((java.math.BigDecimal) coupon.get("min_order")).doubleValue();
            if (amount < minOrder) {
                response.put("success", false);
                response.put("message", "الحد الأدنى للطلب هو " + minOrder + " ريال");
                return response;
            }

            // إضافة عمود max_discount_amount إذا لم يكن موجوداً
            try { db.execute("ALTER TABLE coupons ADD COLUMN max_discount_amount DECIMAL(8,2) DEFAULT NULL"); } catch (Exception ignored) {}

            // نحسب قيمة الخصم
            String type = (String) coupon.get("discount_type");
            double value = ((java.math.BigDecimal) coupon.get("discount_value")).doubleValue();
            double discount = 0;

            if (type.equals("percentage")) {
                discount = amount * (value / 100);
                // تطبيق الحد الأقصى للخصم إن وجد
                Object maxDiscObj = coupon.get("max_discount_amount");
                if (maxDiscObj != null) {
                    double maxDiscount = ((Number) maxDiscObj).doubleValue();
                    if (maxDiscount > 0 && discount > maxDiscount) discount = maxDiscount;
                }
            } else {
                discount = value;
            }

            // الخصم لا يتجاوز قيمة الطلب
            if (discount > amount) discount = amount;

            // نرجع النتيجة للزبون
            response.put("success", true);
            response.put("message", "الكوبون صحيح ✅");
            response.put("discount_type", type);
            response.put("discount_value", value);
            response.put("discount_amount", discount);
            response.put("original_amount", amount);
            response.put("final_amount", amount - discount);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }

        return response;
    }

    // ======================================================
    // 2. الأدمن يشوف كل الكوبونات
    // GET /api/coupons/admin/all
    // ======================================================
    @GetMapping("/admin/all")
    public Map<String, Object> getAllCoupons() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> coupons = db.queryForList(
                "SELECT * FROM coupons ORDER BY created_at DESC"
            );
            response.put("success", true);
            response.put("coupons", coupons);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 3. الأدمن يضيف كوبون جديد
    // POST /api/coupons/admin/add
    // Body: { code, discount_type, discount_value, min_order, max_uses, expires_at }
    // ======================================================
    @PostMapping("/admin/add")
    public Map<String, Object> addCoupon(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            try { db.execute("ALTER TABLE coupons ADD COLUMN max_discount_amount DECIMAL(8,2) DEFAULT NULL"); } catch (Exception ignored) {}
            db.update(
                "INSERT INTO coupons (code, discount_type, discount_value, min_order, max_uses, expires_at, max_discount_amount) VALUES (?,?,?,?,?,?,?)",
                body.get("code"),
                body.get("discount_type"),
                body.get("discount_value"),
                body.get("min_order"),
                body.get("max_uses"),
                body.get("expires_at"),
                body.get("max_discount_amount")
            );
            response.put("success", true);
            response.put("message", "تم إضافة الكوبون بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 4. الأدمن يوقف أو يفعّل كوبون
    // PUT /api/coupons/admin/toggle/{id}
    // ======================================================
    @PutMapping("/admin/toggle/{id}")
    public Map<String, Object> toggleCoupon(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            // نعكس حالة الكوبون (1 يصير 0 والعكس)
            db.update("UPDATE coupons SET is_active = NOT is_active WHERE id = ?", id);
            response.put("success", true);
            response.put("message", "تم تغيير حالة الكوبون");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 5. الأدمن يحذف كوبون
    // DELETE /api/coupons/admin/delete/{id}
    // ======================================================
    @DeleteMapping("/admin/delete/{id}")
    public Map<String, Object> deleteCoupon(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("DELETE FROM coupons WHERE id = ?", id);
            response.put("success", true);
            response.put("message", "تم حذف الكوبون");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }
}