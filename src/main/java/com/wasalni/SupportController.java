package com.wasalni;

// ======================================================
// SupportController.java — نظام الدعم الفني
// يتيح للزبون فتح تذكرة دعم والأدمن يرد عليها
// ======================================================

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
@CrossOrigin(origins = "*")
public class SupportController {

    // أداة التواصل مع قاعدة البيانات
    @Autowired
    private JdbcTemplate db;

    // ======================================================
    // 1. الزبون يفتح تذكرة دعم جديدة
    // POST /api/support/create
    // Body: { user_id, subject, message }
    // ======================================================
    @PostMapping("/create")
    public Map<String, Object> createTicket(@RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update(
                "INSERT INTO support_tickets (user_id, subject, message, status) VALUES (?,?,?,'open')",
                body.get("user_id"),
                body.get("subject"),
                body.get("message")
            );
            response.put("success", true);
            response.put("message", "تم فتح تذكرة الدعم بنجاح");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 2. الزبون يشوف تذاكره
    // GET /api/support/user/{userId}
    // ======================================================
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserTickets(@PathVariable int userId) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> tickets = db.queryForList(
                "SELECT * FROM support_tickets WHERE user_id = ? ORDER BY created_at DESC",
                userId
            );
            response.put("success", true);
            response.put("tickets", tickets);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 3. الأدمن يشوف كل التذاكر
    // GET /api/support/admin/all
    // ======================================================
    @GetMapping("/admin/all")
    public Map<String, Object> getAllTickets() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Map<String, Object>> tickets = db.queryForList(
                "SELECT t.*, u.name as user_name, u.phone as user_phone " +
                "FROM support_tickets t " +
                "LEFT JOIN users u ON t.user_id = u.id " +
                "ORDER BY t.created_at DESC"
            );
            response.put("success", true);
            response.put("tickets", tickets);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 4. الأدمن يرد على تذكرة
    // PUT /api/support/admin/reply/{id}
    // Body: { reply }
    // ======================================================
    @PutMapping("/admin/reply/{id}")
    public Map<String, Object> replyTicket(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update(
                "UPDATE support_tickets SET admin_reply = ?, status = 'replied', updated_at = NOW() WHERE id = ?",
                body.get("reply"), id
            );
            response.put("success", true);
            response.put("message", "تم الرد على التذكرة");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 5. الأدمن يغلق تذكرة
    // PUT /api/support/admin/close/{id}
    // ======================================================
    @PutMapping("/admin/close/{id}")
    public Map<String, Object> closeTicket(@PathVariable int id) {
        Map<String, Object> response = new HashMap<>();
        try {
            db.update(
                "UPDATE support_tickets SET status = 'closed', updated_at = NOW() WHERE id = ?",
                id
            );
            response.put("success", true);
            response.put("message", "تم إغلاق التذكرة");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }

    // ======================================================
    // 6. الأدمن يشوف إحصائيات التذاكر
    // GET /api/support/admin/stats
    // ======================================================
    @GetMapping("/admin/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> response = new HashMap<>();
        try {
            // عدد التذاكر المفتوحة
            int open = db.queryForObject(
                "SELECT COUNT(*) FROM support_tickets WHERE status = 'open'",
                Integer.class
            );
            // عدد التذاكر التي تم الرد عليها
            int replied = db.queryForObject(
                "SELECT COUNT(*) FROM support_tickets WHERE status = 'replied'",
                Integer.class
            );
            // عدد التذاكر المغلقة
            int closed = db.queryForObject(
                "SELECT COUNT(*) FROM support_tickets WHERE status = 'closed'",
                Integer.class
            );

            response.put("success", true);
            response.put("open", open);
            response.put("replied", replied);
            response.put("closed", closed);
            response.put("total", open + replied + closed);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "خطأ: " + e.getMessage());
        }
        return response;
    }
}