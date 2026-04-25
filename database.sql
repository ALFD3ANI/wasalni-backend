-- ============================================
-- قاعدة بيانات مشروع وصّلني
-- المطور: Bader AL-anazi
-- ============================================

-- تعطيل فحص العلاقات مؤقتاً عشان نقدر نحذف بدون أخطاء
SET FOREIGN_KEY_CHECKS = 0;

-- حذف الجداول القديمة إذا موجودة
DROP TABLE IF EXISTS order_items;       -- تفاصيل الطلبات
DROP TABLE IF EXISTS orders;            -- الطلبات
DROP TABLE IF EXISTS products;          -- المنتجات
DROP TABLE IF EXISTS product_extras;    -- إضافات المنتج
DROP TABLE IF EXISTS product_options;   -- خيارات المنتج
DROP TABLE IF EXISTS restaurants;       -- المطاعم
DROP TABLE IF EXISTS categories;        -- التصنيفات
DROP TABLE IF EXISTS users;             -- الزبائن
DROP TABLE IF EXISTS drivers;           -- السائقين
DROP TABLE IF EXISTS addresses;         -- العناوين
DROP TABLE IF EXISTS reviews;           -- التقييمات
DROP TABLE IF EXISTS coupons;           -- الكوبونات
DROP TABLE IF EXISTS notifications;     -- الإشعارات
DROP TABLE IF EXISTS favorites;         -- المفضلة
DROP TABLE IF EXISTS driver_locations;  -- مواقع السائقين
DROP TABLE IF EXISTS payments;          -- المدفوعات
DROP TABLE IF EXISTS order_tracking;    -- تتبع الطلب
DROP TABLE IF EXISTS admin_logs;        -- سجل الأدمن
DROP TABLE IF EXISTS wallet;            -- المحفظة
DROP TABLE IF EXISTS transactions;      -- العمليات المالية
DROP TABLE IF EXISTS refunds;           -- الاسترجاع
DROP TABLE IF EXISTS restaurant_hours;  -- أوقات المطعم
DROP TABLE IF EXISTS restaurant_zones;  -- مناطق التوصيل
DROP TABLE IF EXISTS banners;           -- الإعلانات
DROP TABLE IF EXISTS support_tickets;   -- تذاكر الدعم
DROP TABLE IF EXISTS chat_messages;     -- المحادثات
DROP TABLE IF EXISTS blocked_users;     -- المحظورين
DROP TABLE IF EXISTS device_tokens;     -- توكن الجهاز
DROP TABLE IF EXISTS delivery_fees;     -- رسوم التوصيل
DROP TABLE IF EXISTS loyalty_points;    -- نقاط الولاء

-- تفعيل فحص العلاقات مجدداً
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================
-- 1. جدول الزبائن
-- يخزن بيانات كل زبون مسجل في التطبيق
-- ============================================
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,      -- رقم الزبون التلقائي
    name VARCHAR(255) NOT NULL,             -- اسم الزبون
    email VARCHAR(255) UNIQUE NOT NULL,     -- الإيميل (لا يتكرر)
    phone VARCHAR(20) UNIQUE NOT NULL,      -- رقم الجوال (لا يتكرر)
    password VARCHAR(255) NOT NULL,         -- كلمة المرور مشفرة
    avatar VARCHAR(500),                    -- صورة الزبون
    is_active BOOLEAN DEFAULT TRUE,         -- هل الحساب مفعل؟
    is_blocked BOOLEAN DEFAULT FALSE,       -- هل الحساب محظور؟
    loyalty_points INT DEFAULT 0,           -- نقاط الولاء
    wallet_balance DECIMAL(10,2) DEFAULT 0, -- رصيد المحفظة
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- تاريخ التسجيل
);

-- ============================================
-- 2. جدول السائقين
-- يخزن بيانات كل سائق توصيل
-- ============================================
CREATE TABLE drivers (
    id INT AUTO_INCREMENT PRIMARY KEY,          -- رقم السائق التلقائي
    name VARCHAR(255) NOT NULL,                 -- اسم السائق
    email VARCHAR(255) UNIQUE NOT NULL,         -- الإيميل
    phone VARCHAR(20) UNIQUE NOT NULL,          -- رقم الجوال
    password VARCHAR(255) NOT NULL,             -- كلمة المرور مشفرة
    avatar VARCHAR(500),                        -- صورة السائق
    vehicle_type VARCHAR(100),                  -- نوع المركبة (دراجة/سيارة)
    vehicle_plate VARCHAR(50),                  -- رقم اللوحة
    is_active BOOLEAN DEFAULT TRUE,             -- هل الحساب مفعل؟
    is_available BOOLEAN DEFAULT FALSE,         -- هل السائق متاح الآن؟
    is_blocked BOOLEAN DEFAULT FALSE,           -- هل السائق محظور؟
    wallet_balance DECIMAL(10,2) DEFAULT 0,     -- رصيد أرباح السائق
    rating DECIMAL(3,2) DEFAULT 0,              -- تقييم السائق من 5
    total_deliveries INT DEFAULT 0,             -- عدد التوصيلات الكلية
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- تاريخ التسجيل
);

-- ============================================
-- 3. جدول التصنيفات
-- تصنيفات المطاعم (برغر، بيتزا، مشويات...)
-- ============================================
CREATE TABLE categories (
    id INT AUTO_INCREMENT PRIMARY KEY, -- رقم التصنيف
    name VARCHAR(100) NOT NULL,        -- اسم التصنيف
    icon VARCHAR(500),                 -- أيقونة التصنيف
    is_active BOOLEAN DEFAULT TRUE     -- هل التصنيف ظاهر؟
);

-- ============================================
-- 4. جدول المطاعم
-- بيانات كل مطعم مسجل في المنصة
-- ============================================
CREATE TABLE restaurants (
    id INT AUTO_INCREMENT PRIMARY KEY,      -- رقم المطعم
    name VARCHAR(255) NOT NULL,             -- اسم المطعم
    description TEXT,                       -- وصف المطعم
    image VARCHAR(500),                     -- صورة الشعار
    cover_image VARCHAR(500),               -- صورة الغلاف
    category_id INT,                        -- رقم التصنيف (علاقة مع categories)
    phone VARCHAR(20),                      -- رقم التواصل
    address TEXT,                           -- عنوان المطعم
    latitude DECIMAL(10,8),                 -- خط العرض للموقع
    longitude DECIMAL(11,8),                -- خط الطول للموقع
    rating DECIMAL(3,2) DEFAULT 0,          -- متوسط التقييم
    total_reviews INT DEFAULT 0,            -- عدد التقييمات
    min_order DECIMAL(10,2) DEFAULT 0,      -- أقل طلب مقبول
    delivery_time INT DEFAULT 30,           -- وقت التوصيل بالدقائق
    is_active BOOLEAN DEFAULT TRUE,         -- هل المطعم مفعل؟
    is_open BOOLEAN DEFAULT TRUE,           -- هل المطعم مفتوح الآن؟
    username VARCHAR(100) UNIQUE,           -- يوزرنيم لوحة التحكم
    password VARCHAR(255),                  -- كلمة مرور لوحة التحكم
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES categories(id)
);

-- ============================================
-- 5. جدول أوقات عمل المطعم
-- كل يوم له وقت فتح وإغلاق
-- ============================================
CREATE TABLE restaurant_hours (
    id INT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id INT NOT NULL,             -- رقم المطعم
    day ENUM('Saturday','Sunday','Monday','Tuesday','Wednesday','Thursday','Friday') NOT NULL,
    open_time TIME,                         -- وقت الفتح
    close_time TIME,                        -- وقت الإغلاق
    is_closed BOOLEAN DEFAULT FALSE,        -- هل المطعم مغلق هذا اليوم؟
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);

-- ============================================
-- 6. جدول مناطق التوصيل
-- كل مطعم يحدد المناطق اللي يوصّل فيها
-- ============================================
CREATE TABLE restaurant_zones (
    id INT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id INT NOT NULL,             -- رقم المطعم
    zone_name VARCHAR(100),                 -- اسم المنطقة
    delivery_fee DECIMAL(10,2) DEFAULT 0,   -- رسوم التوصيل للمنطقة
    min_delivery_time INT DEFAULT 20,       -- أقل وقت توصيل بالدقائق
    max_delivery_time INT DEFAULT 60,       -- أكثر وقت توصيل بالدقائق
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);

-- ============================================
-- 7. جدول المنتجات
-- كل وجبة أو منتج في المطعم
-- ============================================
CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    restaurant_id INT NOT NULL,             -- رقم المطعم التابع له
    name VARCHAR(255) NOT NULL,             -- اسم المنتج
    description TEXT,                       -- وصف المنتج
    price DECIMAL(10,2) NOT NULL,           -- السعر
    old_price DECIMAL(10,2) DEFAULT 0,      -- السعر قبل الخصم
    image VARCHAR(500),                     -- صورة المنتج
    category VARCHAR(100),                  -- تصنيف داخل المطعم
    is_available BOOLEAN DEFAULT TRUE,      -- هل المنتج متاح؟
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);

-- ============================================
-- 8. جدول خيارات المنتج
-- مثال: الحجم (صغير/وسط/كبير)
-- ============================================
CREATE TABLE product_options (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,                -- رقم المنتج
    name VARCHAR(100) NOT NULL,             -- اسم الخيار
    is_required BOOLEAN DEFAULT FALSE,      -- هل الاختيار إجباري؟
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- ============================================
-- 9. جدول إضافات المنتج
-- مثال: جبن زيادة (+3 ريال)
-- ============================================
CREATE TABLE product_extras (
    id INT AUTO_INCREMENT PRIMARY KEY,
    option_id INT NOT NULL,                 -- رقم الخيار التابع له
    name VARCHAR(100) NOT NULL,             -- اسم الإضافة
    price DECIMAL(10,2) DEFAULT 0,          -- سعر الإضافة
    FOREIGN KEY (option_id) REFERENCES product_options(id)
);

-- ============================================
-- 10. جدول العناوين
-- عناوين التوصيل المحفوظة للزبون
-- ============================================
CREATE TABLE addresses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,                   -- رقم الزبون
    label VARCHAR(100),                     -- تسمية العنوان (البيت/العمل)
    address TEXT NOT NULL,                  -- نص العنوان
    latitude DECIMAL(10,8),                 -- الموقع الجغرافي
    longitude DECIMAL(11,8),
    is_default BOOLEAN DEFAULT FALSE,       -- هل هو العنوان الافتراضي؟
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================
-- 11. جدول الكوبونات
-- أكواد الخصم
-- ============================================
CREATE TABLE coupons (
    id INT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,           -- كود الخصم
    discount_type ENUM('percentage','fixed') NOT NULL, -- نسبة أو مبلغ ثابت
    discount_value DECIMAL(10,2) NOT NULL,      -- قيمة الخصم
    min_order DECIMAL(10,2) DEFAULT 0,          -- أقل طلب لتفعيل الكوبون
    max_uses INT DEFAULT 0,                     -- عدد مرات الاستخدام الأقصى
    used_count INT DEFAULT 0,                   -- عدد مرات الاستخدام الفعلي
    expires_at TIMESTAMP NULL,                  -- تاريخ انتهاء الكوبون
    is_active BOOLEAN DEFAULT TRUE,             -- هل الكوبون مفعل؟
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 12. جدول الطلبات
-- كل طلب يسويه الزبون
-- ============================================
CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,                   -- رقم الزبون
    restaurant_id INT NOT NULL,             -- رقم المطعم
    driver_id INT,                          -- رقم السائق (يضاف بعد القبول)
    address_id INT,                         -- رقم عنوان التوصيل
    coupon_id INT,                          -- رقم الكوبون (إذا استخدم)
    subtotal DECIMAL(10,2) NOT NULL,        -- مجموع المنتجات بدون توصيل
    delivery_fee DECIMAL(10,2) DEFAULT 0,   -- رسوم التوصيل
    discount DECIMAL(10,2) DEFAULT 0,       -- قيمة الخصم
    total_price DECIMAL(10,2) NOT NULL,     -- المبلغ النهائي
    status ENUM('pending','accepted','preparing','ready','picked_up','delivered','cancelled') DEFAULT 'pending',
    payment_method ENUM('cash','card','wallet','apple_pay','stc_pay') NOT NULL,
    payment_status ENUM('pending','paid','refunded') DEFAULT 'pending',
    notes TEXT,                             -- ملاحظات الزبون
    estimated_time INT DEFAULT 30,          -- الوقت المتوقع بالدقائق
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id),
    FOREIGN KEY (driver_id) REFERENCES drivers(id),
    FOREIGN KEY (address_id) REFERENCES addresses(id),
    FOREIGN KEY (coupon_id) REFERENCES coupons(id)
);

-- ============================================
-- 13. جدول تفاصيل الطلب
-- كل منتج داخل الطلب
-- ============================================
CREATE TABLE order_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,                  -- رقم الطلب
    product_id INT NOT NULL,                -- رقم المنتج
    quantity INT NOT NULL DEFAULT 1,        -- الكمية
    price DECIMAL(10,2) NOT NULL,           -- سعر وقت الطلب
    extras TEXT,                            -- الإضافات المختارة
    notes TEXT,                             -- ملاحظات على المنتج
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- ============================================
-- 14. جدول تتبع الطلب
-- سجل مراحل الطلب من البداية للتوصيل
-- ============================================
CREATE TABLE order_tracking (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,                  -- رقم الطلب
    status VARCHAR(100) NOT NULL,           -- المرحلة الحالية
    message TEXT,                           -- رسالة للزبون
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- ============================================
-- 15. جدول المدفوعات
-- سجل كل عملية دفع
-- ============================================
CREATE TABLE payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,                  -- رقم الطلب
    user_id INT NOT NULL,                   -- رقم الزبون
    amount DECIMAL(10,2) NOT NULL,          -- المبلغ المدفوع
    method VARCHAR(100),                    -- طريقة الدفع
    status ENUM('pending','success','failed','refunded') DEFAULT 'pending',
    transaction_id VARCHAR(255),            -- رقم العملية من بوابة الدفع
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================
-- 16. جدول التقييمات
-- تقييم الزبون للمطعم والسائق بعد التوصيل
-- ============================================
CREATE TABLE reviews (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,                   -- رقم الزبون
    order_id INT NOT NULL,                  -- رقم الطلب
    restaurant_id INT,                      -- رقم المطعم
    driver_id INT,                          -- رقم السائق
    restaurant_rating INT,                  -- تقييم المطعم من 5
    driver_rating INT,                      -- تقييم السائق من 5
    comment TEXT,                           -- تعليق الزبون
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- ============================================
-- 17. جدول الإشعارات
-- إشعارات للزبائن والسائقين
-- ============================================
CREATE TABLE notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,                            -- رقم الزبون (إذا له)
    driver_id INT,                          -- رقم السائق (إذا له)
    title VARCHAR(255) NOT NULL,            -- عنوان الإشعار
    message TEXT NOT NULL,                  -- نص الإشعار
    type VARCHAR(100),                      -- نوع الإشعار (طلب/عرض/تنبيه)
    is_read BOOLEAN DEFAULT FALSE,          -- هل قرأه؟
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 18. جدول المفضلة
-- المطاعم اللي يحفظها الزبون
-- ============================================
CREATE TABLE favorites (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,                   -- رقم الزبون
    restaurant_id INT NOT NULL,             -- رقم المطعم
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id)
);

-- ============================================
-- 19. جدول موقع السائق
-- يتحدث لحظة بلحظة أثناء التوصيل
-- ============================================
CREATE TABLE driver_locations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    driver_id INT NOT NULL UNIQUE,          -- رقم السائق (سجل واحد لكل سائق)
    latitude DECIMAL(10,8),                 -- الموقع الحالي
    longitude DECIMAL(11,8),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES drivers(id)
);

-- ============================================
-- 20. جدول المحادثات
-- محادثة بين الزبون والسائق أثناء الطلب
-- ============================================
CREATE TABLE chat_messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,                  -- رقم الطلب
    sender_type ENUM('user','driver') NOT NULL, -- من أرسل؟
    sender_id INT NOT NULL,                 -- رقم المرسل
    message TEXT NOT NULL,                  -- نص الرسالة
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id)
);

-- ============================================
-- 21. جدول تذاكر الدعم الفني
-- شكاوى ومشاكل الزبائن والسائقين
-- ============================================
CREATE TABLE support_tickets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,                            -- رقم الزبون
    driver_id INT,                          -- رقم السائق
    order_id INT,                           -- رقم الطلب المرتبط
    subject VARCHAR(255),                   -- موضوع المشكلة
    message TEXT NOT NULL,                  -- تفاصيل المشكلة
    status ENUM('open','in_progress','closed') DEFAULT 'open',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 22. جدول الإعلانات
-- بانرات الصفحة الرئيسية
-- ============================================
CREATE TABLE banners (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255),                     -- عنوان الإعلان
    image VARCHAR(500) NOT NULL,            -- صورة الإعلان
    link VARCHAR(500),                      -- رابط عند الضغط
    is_active BOOLEAN DEFAULT TRUE,         -- هل الإعلان ظاهر؟
    sort_order INT DEFAULT 0,               -- ترتيب العرض
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 23. جدول العمليات المالية
-- سجل كل إيداع وسحب في المحافظ
-- ============================================
CREATE TABLE transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,                            -- رقم الزبون
    driver_id INT,                          -- رقم السائق
    type ENUM('credit','debit') NOT NULL,   -- إيداع أو سحب
    amount DECIMAL(10,2) NOT NULL,          -- المبلغ
    description VARCHAR(255),              -- وصف العملية
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 24. جدول الاسترجاع
-- طلبات استرداد المبالغ
-- ============================================
CREATE TABLE refunds (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,                  -- رقم الطلب
    user_id INT NOT NULL,                   -- رقم الزبون
    amount DECIMAL(10,2) NOT NULL,          -- المبلغ المطلوب استرداده
    reason TEXT,                            -- سبب الاسترداد
    status ENUM('pending','approved','rejected') DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

-- ============================================
-- 25. جدول رسوم التوصيل
-- حسب المسافة بالكيلومتر
-- ============================================
CREATE TABLE delivery_fees (
    id INT AUTO_INCREMENT PRIMARY KEY,
    zone_name VARCHAR(100) NOT NULL,        -- اسم النطاق
    min_km DECIMAL(5,2) DEFAULT 0,          -- من كم كيلومتر
    max_km DECIMAL(5,2) DEFAULT 0,          -- إلى كم كيلومتر
    fee DECIMAL(10,2) NOT NULL              -- الرسوم
);

-- ============================================
-- 26. جدول توكن الجهاز
-- لإرسال الإشعارات للجوال
-- ============================================
CREATE TABLE device_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,                            -- رقم الزبون
    driver_id INT,                          -- رقم السائق
    token TEXT NOT NULL,                    -- توكن الجهاز
    platform ENUM('ios','android','web'),   -- نوع الجهاز
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 27. جدول سجل الأدمن
-- كل إجراء يسويه الأدمن يتسجل هنا
-- ============================================
CREATE TABLE admin_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    admin_name VARCHAR(255),                -- اسم الأدمن
    action VARCHAR(255) NOT NULL,           -- الإجراء اللي سواه
    details TEXT,                           -- تفاصيل الإجراء
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 28. جدول المستخدمين المحظورين
-- الزبائن أو السائقين المحظورين مع السبب
-- ============================================
CREATE TABLE blocked_users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,                            -- رقم الزبون المحظور
    driver_id INT,                          -- رقم السائق المحظور
    reason TEXT,                            -- سبب الحظر
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);