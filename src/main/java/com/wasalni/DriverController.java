package com.wasalni;

// ============================================
// DriverController - إدارة السائقين
// يتعامل مع:
// GET  /api/driver/profile          - عرض ملف السائق
// PUT  /api/driver/profile          - تعديل ملف السائق
// PUT  /api/driver/availability     - تغيير حالة التوفر
// PUT  /api/driver/location         - تحديث الموقع
// GET  /api/driver/orders           - الطلبات المتاحة
// PUT  /api/driver/orders/{id}      - تحديث حالة الطلب
// GET  /api/driver/orders/history   - سجل التوصيلات
// GET  /api/driver/earnings         - الأرباح
// ============================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/driver")
public class DriverController {

    // للتعامل مع قاعدة البيانات
    @Autowired
    JdbcTemplate db;

    // لقراءة التوكن
    @Autowired
    JwtUtil jwtUtil;

    // ============================================
    // دالة مساعدة - استخراج رقم السائق من التوكن
    // ============================================
    private String getDriverIdFromToken(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtUtil.getSubject(token);
    }

    // ============================================
    // GET /api/driver/profile
    // عرض ملف السائق الشخصي
    // ============================================
    @GetMapping("/profile")
    public Map<String, Object> getProfile(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String driverId = getDriverIdFromToken(authHeader);

            Map<String, Object> driver = db.queryForMap(
                "SELECT id, name, email, phone, avatar, vehicle_type, vehicle_plate, " +
                "is_available, rating, total_deliveries, wallet_balance, created_at " +
                "FROM drivers WHERE id = ?",
                driverId
            );

            response.put("success", true);
            response.put("driver", driver);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/driver/profile
    // تعديل ملف السائق
    // ============================================
    @PutMapping("/profile")
    public Map<String, Object> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String driverId = getDriverIdFromToken(authHeader);

            String name = (String) data.get("name");
            String phone = (String) data.get("phone");
            String avatar = (String) data.get("avatar");
            String vehicleType = (String) data.get("vehicleType");
            String vehiclePlate = (String) data.get("vehiclePlate");

            db.update(
                "UPDATE drivers SET " +
                "name = COALESCE(?, name), " +
                "phone = COALESCE(?, phone), " +
                "avatar = COALESCE(?, avatar), " +
                "vehicle_type = COALESCE(?, vehicle_type), " +
                "vehicle_plate = COALESCE(?, vehicle_plate) " +
                "WHERE id = ?",
                name, phone, avatar, vehicleType, vehiclePlate, driverId
            );

            response.put("success", true);
            response.put("message", "تم تحديث الملف الشخصي بنجاح");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/driver/availability
    // تغيير حالة توفر السائق (متاح/غير متاح)
    // ============================================
    @PutMapping("/availability")
    public Map<String, Object> updateAvailability(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String driverId = getDriverIdFromToken(authHeader);
            Boolean isAvailable = (Boolean) data.get("isAvailable");

            db.update(
                "UPDATE drivers SET is_available = ? WHERE id = ?",
                isAvailable, driverId
            );

            response.put("success", true);
            response.put("message", isAvailable ? "أنت متاح الآن لاستقبال الطلبات" : "أنت غير متاح الآن");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // PUT /api/driver/location
    // تحديث موقع السائق لحظة بلحظة
    // ============================================
    @PutMapping("/location")
    public Map<String, Object> updateLocation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String driverId = getDriverIdFromToken(authHeader);
            Double latitude = ((Number) data.get("latitude")).doubleValue();
            Double longitude = ((Number) data.get("longitude")).doubleValue();

            // تحديث الموقع إذا موجود، وإلا إضافة جديد
            List<Map<String, Object>> existing = db.queryForList(
                "SELECT id FROM driver_locations WHERE driver_id = ?", driverId
            );

            if (existing.isEmpty()) {
                db.update(
                    "INSERT INTO driver_locations (driver_id, latitude, longitude) VALUES (?, ?, ?)",
                    driverId, latitude, longitude
                );
            } else {
                db.update(
                    "UPDATE driver_locations SET latitude = ?, longitude = ? WHERE driver_id = ?",
                    latitude, longitude, driverId
                );
            }

            response.put("success", true);
            response.put("message", "تم تحديث الموقع");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/driver/orders
    // الطلبات الجاهزة للتوصيل (حالة ready)
    // ============================================
    @GetMapping("/orders")
    public Map<String, Object> getAvailableOrders(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            // جلب الطلبات الجاهزة للتوصيل بدون سائق
            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.*, r.name as restaurant_name, r.address as restaurant_address, " +
                "u.name as user_name, u.phone as user_phone " +
                "FROM orders o " +
                "JOIN restaurants r ON o.restaurant_id = r.id " +
                "JOIN users u ON o.user_id = u.id " +
                "WHERE o.status = 'ready' AND o.driver_id IS NULL " +
                "ORDER BY o.created_at ASC"
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
    // PUT /api/driver/orders/{id}
    // تحديث حالة الطلب من السائق
    // الحالات: picked_up / delivered
    // ============================================
    @PutMapping("/orders/{id}")
    public Map<String, Object> updateOrderStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable int id,
            @RequestBody Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();

        try {
            String driverId = getDriverIdFromToken(authHeader);
            String status = (String) data.get("status");

            // إذا السائق يقبل الطلب - يضيف نفسه للطلب
            if (status.equals("picked_up")) {
                db.update(
                    "UPDATE orders SET driver_id = ?, status = ? WHERE id = ? AND status = 'ready'",
                    driverId, status, id
                );
            } else {
                // تحديث الحالة
                db.update(
                    "UPDATE orders SET status = ? WHERE id = ? AND driver_id = ?",
                    status, id, driverId
                );

                // إذا الطلب وصل - نحدث إحصائيات السائق
                if (status.equals("delivered")) {
                    db.update(
                        "UPDATE drivers SET total_deliveries = total_deliveries + 1 WHERE id = ?",
                        driverId
                    );
                }
            }

            // إضافة سجل في تتبع الطلب
            db.update(
                "INSERT INTO order_tracking (order_id, status, message) VALUES (?, ?, ?)",
                id, status, "تم تحديث حالة الطلب إلى: " + status
            );

            response.put("success", true);
            response.put("message", "تم تحديث حالة الطلب");

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }

    // ============================================
    // GET /api/driver/orders/history
    // سجل توصيلات السائق
    // ============================================
    @GetMapping("/orders/history")
    public Map<String, Object> getDeliveryHistory(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String driverId = getDriverIdFromToken(authHeader);

            List<Map<String, Object>> orders = db.queryForList(
                "SELECT o.*, r.name as restaurant_name, u.name as user_name " +
                "FROM orders o " +
                "JOIN restaurants r ON o.restaurant_id = r.id " +
                "JOIN users u ON o.user_id = u.id " +
                "WHERE o.driver_id = ? AND o.status = 'delivered' " +
                "ORDER BY o.created_at DESC",
                driverId
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
    // GET /api/driver/earnings
    // إحصائيات أرباح السائق
    // ============================================
    @GetMapping("/earnings")
    public Map<String, Object> getEarnings(@RequestHeader("Authorization") String authHeader) {
        Map<String, Object> response = new HashMap<>();

        try {
            String driverId = getDriverIdFromToken(authHeader);

            // إجمالي التوصيلات والأرباح
            Map<String, Object> stats = db.queryForMap(
                "SELECT " +
                "COUNT(*) as total_deliveries, " +
                "SUM(delivery_fee) as total_earnings, " +
                "AVG(delivery_fee) as avg_earning_per_delivery " +
                "FROM orders WHERE driver_id = ? AND status = 'delivered'",
                driverId
            );

            // أرباح اليوم
            Map<String, Object> todayStats = db.queryForMap(
                "SELECT COUNT(*) as today_deliveries, SUM(delivery_fee) as today_earnings " +
                "FROM orders WHERE driver_id = ? AND status = 'delivered' " +
                "AND DATE(created_at) = CURDATE()",
                driverId
            );

            // رصيد المحفظة
            Map<String, Object> wallet = db.queryForMap(
                "SELECT wallet_balance FROM drivers WHERE id = ?", driverId
            );

            response.put("success", true);
            response.put("stats", stats);
            response.put("today", todayStats);
            response.put("walletBalance", wallet.get("wallet_balance"));

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "حدث خطأ: " + e.getMessage());
        }

        return response;
    }
}