package com.wasalni;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class RestaurantController {

    // ══════════════════════════════════════
    //  GET /api/health
    // ══════════════════════════════════════
    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> res = new HashMap<>();
        res.put("status", "OK");
        res.put("message", "Wasalni API is running!");
        res.put("developer", "Bader AL-anazi");
        return res;
    }

    // ══════════════════════════════════════
    //  GET /api/restaurants
    // ══════════════════════════════════════
    @GetMapping("/restaurants")
    public List<Map<String, Object>> getRestaurants() {
        List<Map<String, Object>> list = new ArrayList<>();

        // 1 - برغر هاوس
        Map<String, Object> r1 = new HashMap<>();
        r1.put("id", 1);
        r1.put("name", "برغر هاوس");
        r1.put("cat", "burger");
        r1.put("emoji", "🍔");
        r1.put("bg", "food1");
        r1.put("rating", 4.8);
        r1.put("time", "20-30");
        r1.put("delivery", 0);
        r1.put("promo", "خصم 20%");
        r1.put("tags", Arrays.asList("برغر", "أمريكي"));
        r1.put("products", getBurgerProducts());
        list.add(r1);

        // 2 - بيتزا نابولي
        Map<String, Object> r2 = new HashMap<>();
        r2.put("id", 2);
        r2.put("name", "بيتزا نابولي");
        r2.put("cat", "pizza");
        r2.put("emoji", "🍕");
        r2.put("bg", "food2");
        r2.put("rating", 4.6);
        r2.put("time", "30-40");
        r2.put("delivery", 5);
        r2.put("promo", "");
        r2.put("tags", Arrays.asList("بيتزا", "إيطالي"));
        r2.put("products", getPizzaProducts());
        list.add(r2);

        // 3 - شاورما الأصيل
        Map<String, Object> r3 = new HashMap<>();
        r3.put("id", 3);
        r3.put("name", "شاورما الأصيل");
        r3.put("cat", "shawarma");
        r3.put("emoji", "🌯");
        r3.put("bg", "food3");
        r3.put("rating", 4.9);
        r3.put("time", "15-25");
        r3.put("delivery", 0);
        r3.put("promo", "الأكثر طلباً");
        r3.put("tags", Arrays.asList("شاورما", "عربي"));
        r3.put("products", getShawarmaProducts());
        list.add(r3);

        // 4 - سوشي ماستر
        Map<String, Object> r4 = new HashMap<>();
        r4.put("id", 4);
        r4.put("name", "سوشي ماستر");
        r4.put("cat", "sushi");
        r4.put("emoji", "🍣");
        r4.put("bg", "food4");
        r4.put("rating", 4.7);
        r4.put("time", "35-45");
        r4.put("delivery", 8);
        r4.put("promo", "");
        r4.put("tags", Arrays.asList("سوشي", "ياباني"));
        r4.put("products", getSushiProducts());
        list.add(r4);

        // 5 - حلويات الأميرة
        Map<String, Object> r5 = new HashMap<>();
        r5.put("id", 5);
        r5.put("name", "حلويات الأميرة");
        r5.put("cat", "dessert");
        r5.put("emoji", "🍰");
        r5.put("bg", "food5");
        r5.put("rating", 4.5);
        r5.put("time", "25-35");
        r5.put("delivery", 0);
        r5.put("promo", "");
        r5.put("tags", Arrays.asList("حلويات", "كيك"));
        r5.put("products", getDessertProducts());
        list.add(r5);

        return list;
    }

    // ══════════════════════════════════════
    //  GET /api/restaurants/{id}
    // ══════════════════════════════════════
    @GetMapping("/restaurants/{id}")
    public Map<String, Object> getRestaurantById(@PathVariable int id) {
        List<Map<String, Object>> all = getRestaurants();
        return all.stream()
                .filter(r -> ((int) r.get("id")) == id)
                .findFirst()
                .orElse(null);
    }

    // ══════════════════════════════════════
    //  POST /api/orders  (استقبال الطلبات)
    // ══════════════════════════════════════
    @PostMapping("/orders")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> orderData) {
        Map<String, Object> response = new HashMap<>();
        int orderId = (int)(Math.random() * 9000) + 1000;
        response.put("orderId", "#" + orderId);
        response.put("status", "confirmed");
        response.put("message", "تم استلام طلبك بنجاح!");
        response.put("estimatedTime", "25-35 دقيقة");
        return response;
    }

    // ══════════════════════════════════════
    //  PRODUCTS DATA
    // ══════════════════════════════════════
    private List<Map<String, Object>> getBurgerProducts() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", 101); p1.put("name", "برغر كلاسيك");
        p1.put("desc", "لحم بقري طازج مع الجبن الأمريكي والخس والطماطم");
        p1.put("emoji", "🍔"); p1.put("price", 25); p1.put("oldPrice", 32);
        p1.put("extras", Arrays.asList("جبن إضافي +3", "صوص خاص +2", "بيض +4"));
        list.add(p1);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("id", 102); p2.put("name", "برغر دبل");
        p2.put("desc", "قرصتين لحم مع الجبن والخضروات الطازجة");
        p2.put("emoji", "🍔"); p2.put("price", 35); p2.put("oldPrice", 0);
        p2.put("extras", Arrays.asList("مشروم +5", "جبن إضافي +3"));
        list.add(p2);

        Map<String, Object> p3 = new HashMap<>();
        p3.put("id", 103); p3.put("name", "بطاطس كبير");
        p3.put("desc", "بطاطس مقلية مقرمشة مع صوص الكاتشب");
        p3.put("emoji", "🍟"); p3.put("price", 12); p3.put("oldPrice", 0);
        p3.put("extras", Arrays.asList("صوص إضافي +2"));
        list.add(p3);

        Map<String, Object> p4 = new HashMap<>();
        p4.put("id", 104); p4.put("name", "كولا كبير");
        p4.put("desc", "مشروب غازي بارد");
        p4.put("emoji", "🥤"); p4.put("price", 8); p4.put("oldPrice", 0);
        p4.put("extras", new ArrayList<>());
        list.add(p4);

        return list;
    }

    private List<Map<String, Object>> getPizzaProducts() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", 201); p1.put("name", "مارغريتا");
        p1.put("desc", "صلصة طماطم مع موتزاريلا طازجة وريحان");
        p1.put("emoji", "🍕"); p1.put("price", 45); p1.put("oldPrice", 0);
        p1.put("extras", Arrays.asList("جبن إضافي +8", "فلفل +3"));
        list.add(p1);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("id", 202); p2.put("name", "بيبروني");
        p2.put("desc", "صلصة طماطم مع بيبروني وموتزاريلا");
        p2.put("emoji", "🍕"); p2.put("price", 55); p2.put("oldPrice", 65);
        p2.put("extras", Arrays.asList("بيبروني إضافي +10"));
        list.add(p2);

        Map<String, Object> p3 = new HashMap<>();
        p3.put("id", 203); p3.put("name", "خضار");
        p3.put("desc", "بيتزا نباتية بالخضروات الطازجة");
        p3.put("emoji", "🍕"); p3.put("price", 40); p3.put("oldPrice", 0);
        p3.put("extras", new ArrayList<>());
        list.add(p3);

        return list;
    }

    private List<Map<String, Object>> getShawarmaProducts() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", 301); p1.put("name", "شاورما دجاج");
        p1.put("desc", "دجاج متبل مشوي مع الثوم والخضار في خبز العيش");
        p1.put("emoji", "🌯"); p1.put("price", 18); p1.put("oldPrice", 0);
        p1.put("extras", Arrays.asList("ثوم إضافي +2", "صوص حار +1"));
        list.add(p1);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("id", 302); p2.put("name", "شاورما لحم");
        p2.put("desc", "لحم غنم متبل مع الخضار والصوص الخاص");
        p2.put("emoji", "🌯"); p2.put("price", 22); p2.put("oldPrice", 0);
        p2.put("extras", Arrays.asList("ثوم إضافي +2"));
        list.add(p2);

        Map<String, Object> p3 = new HashMap<>();
        p3.put("id", 303); p3.put("name", "حمص");
        p3.put("desc", "حمص طازج مع زيت الزيتون");
        p3.put("emoji", "🥣"); p3.put("price", 12); p3.put("oldPrice", 0);
        p3.put("extras", new ArrayList<>());
        list.add(p3);

        return list;
    }

    private List<Map<String, Object>> getSushiProducts() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", 401); p1.put("name", "رولز سلمون");
        p1.put("desc", "رولز سلمون طازج مع الأفوكادو والخيار");
        p1.put("emoji", "🍣"); p1.put("price", 55); p1.put("oldPrice", 0);
        p1.put("extras", Arrays.asList("صوص سبايسي +5"));
        list.add(p1);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("id", 402); p2.put("name", "سشيمي");
        p2.put("desc", "شرائح سمك طازج مع الزنجبيل والواسابي");
        p2.put("emoji", "🍱"); p2.put("price", 65); p2.put("oldPrice", 80);
        p2.put("extras", new ArrayList<>());
        list.add(p2);

        return list;
    }

    private List<Map<String, Object>> getDessertProducts() {
        List<Map<String, Object>> list = new ArrayList<>();

        Map<String, Object> p1 = new HashMap<>();
        p1.put("id", 501); p1.put("name", "كيك شوكولاتة");
        p1.put("desc", "كيك شوكولاتة بلجيكية فاخرة مع الكريمة");
        p1.put("emoji", "🍰"); p1.put("price", 28); p1.put("oldPrice", 0);
        p1.put("extras", Arrays.asList("آيس كريم +8", "كراميل +5"));
        list.add(p1);

        Map<String, Object> p2 = new HashMap<>();
        p2.put("id", 502); p2.put("name", "كنافة");
        p2.put("desc", "كنافة بالجبن مع القطر والفستق");
        p2.put("emoji", "🍯"); p2.put("price", 22); p2.put("oldPrice", 0);
        p2.put("extras", new ArrayList<>());
        list.add(p2);

        return list;
    }
}