package com.wasalni;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class RestaurantController {

    @GetMapping("/restaurants")
    public List<Map<String, Object>> getRestaurants() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("name", "برغر هاوس");
        r1.put("category", "برغر");
        r1.put("rating", 4.8);
        r1.put("delivery", 0);
        r1.put("time", "20-30");
        list.add(r1);

        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("name", "شاورما الأصيل");
        r2.put("category", "شاورما");
        r2.put("rating", 4.9);
        r2.put("delivery", 0);
        r2.put("time", "15-25");
        list.add(r2);

        return list;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> res = new HashMap<>();
        res.put("status", "OK");
        res.put("message", "Wasalni API is running!");
        res.put("developer", "Bader AL-anazi");
        return res;
    }
}