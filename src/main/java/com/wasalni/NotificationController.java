package com.wasalni;

// ======================================================
// NotificationController.java — نظام الإشعارات
// يتيح إرسال وقراءة الإشعارات للزبائن والسائقين
// ======================================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    // أداة التواصل مع قاعدة البيانات
    @Autowired
    private JdbcTemplate db;

    // ======================================================
    // 1. الزبون أو السائق يجيب كل إشعاراته
    // GET /api/notifications/{userId}
    // ======================================================
    @GetMapping("/{userId}")
    public Map<String, Object> getNotifications(@PathVariable int userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> notifications = db.queryForList(
                "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC",
                userId
            );
            response.put("success", true);
            response.put("notifications", notifications);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 2. يحسب عدد الإشعارات الغير مقروءة
    // GET /api/notifications/{userId}/unread-count
    // ======================================================
    @GetMapping("/{userId}/unread-count")
    public Map<String, Object> getUnreadCount(@PathVariable int userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            int count = db.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0",
                Integer.class, userId
            );
            response.put("success", true);
            response.put("unread_count", count);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 3. يحدد إشعار واحد كمقروء
    // PUT /api/notifications/read/{id}
    // ======================================================
    @PutMapping("/read/{id}")
    public Map<String, Object> markAsRead(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("UPDATE notifications SET is_read = 1 WHERE id = ?", id);
            response.put("success", true);
            response.put("message", "تم تحديد الإشعار كمقروء");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 4. يحدد كل الإشعارات كمقروءة دفعة وحدة
    // PUT /api/notifications/{userId}/read-all
    // ======================================================
    @PutMapping("/{userId}/read-all")
    public Map<String, Object> markAllAsRead(@PathVariable int userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("UPDATE notifications SET is_read = 1 WHERE user_id = ?", userId);
            response.put("success", true);
            response.put("message", "تم تحديد كل الإشعارات كمقروءة");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 5. الأدمن يرسل إشعار لمستخدم معين
    // POST /api/notifications/send
    // Body: { user_id, title, message, type }
    // ======================================================
    @PostMapping("/send")
    public Map<String, Object> sendNotification(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update(
                "INSERT INTO notifications (user_id, title, message, type) VALUES (?,?,?,?)",
                body.get("user_id"),
                body.get("title"),
                body.get("message"),
                body.get("type")
            );
            response.put("success", true);
            response.put("message", "تم إرسال الإشعار بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 6. الزبون يحذف إشعار
    // DELETE /api/notifications/delete/{id}
    // ======================================================
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteNotification(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update("DELETE FROM notifications WHERE id = ?", id);
            response.put("success", true);
            response.put("message", "تم حذف الإشعار");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }
}