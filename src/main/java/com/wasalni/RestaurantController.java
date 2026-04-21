package com.wasalni;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class RestaurantController {

    @Autowired
    JdbcTemplate db;

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> res = new HashMap<>();
        res.put("status", "OK");
        res.put("message", "Wasalni API is running!");
        res.put("developer", "Bader AL-anazi");
        return res;
    }

    @GetMapping("/restaurants")
    public List<Map<String, Object>> getRestaurants() {
        return db.queryForList("SELECT * FROM restaurants");
    }

    @GetMapping("/restaurants/{id}")
    public Map<String, Object> getRestaurantById(@PathVariable int id) {
        List<Map<String, Object>> products = db.queryForList(
            "SELECT * FROM products WHERE restaurant_id = ?", id);
        Map<String, Object> restaurant = db.queryForMap(
            "SELECT * FROM restaurants WHERE id = ?", id);
        restaurant.put("products", products);
        return restaurant;
    }

    @GetMapping("/products")
    public List<Map<String, Object>> getProducts() {
        return db.queryForList("SELECT * FROM products");
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> orderData) {
        db.update(
            "INSERT INTO orders (total_price, status, payment_method) VALUES (?, 'pending', ?)",
            orderData.get("totalPrice"),
            orderData.get("paymentMethod")
        );
        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("message", "تم استلام طلبك بنجاح!");
        response.put("estimatedTime", "25-35 دقيقة");
        return response;
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> getOrders() {
        return db.queryForList("SELECT * FROM orders ORDER BY created_at DESC");
    }
}