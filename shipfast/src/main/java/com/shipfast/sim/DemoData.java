package com.shipfast.sim;

import com.shipfast.ShipFastApp;
import com.shipfast.catalog.Category;
import com.shipfast.catalog.Product;
import com.shipfast.common.Money;
import com.shipfast.customer.Address;
import com.shipfast.customer.Customer;
import com.shipfast.customer.LoyaltyTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seed catalogue and customer base used by the simulator. */
final class DemoData {

    static final String LIMITED_SKU = "AP-SNKR-LTD";

    record SeedProduct(String sku, String name, Category category, String price, int grams, int stock, boolean onSale) {
    }

    static final List<SeedProduct> PRODUCTS = List.of(
            new SeedProduct("EL-HDPH-01", "Wireless Headphones", Category.ELECTRONICS, "89.99", 350, 1500, true),
            new SeedProduct("EL-CHRG-02", "USB-C Charger 65W", Category.ELECTRONICS, "29.99", 180, 1500, false),
            new SeedProduct("EL-SPKR-03", "Bluetooth Speaker", Category.ELECTRONICS, "49.50", 700, 1500, true),
            new SeedProduct("EL-CABL-04", "Braided Cable 3-Pack", Category.ELECTRONICS, "12.00", 150, 3000, false),
            new SeedProduct("EL-WTCH-05", "Fitness Watch", Category.ELECTRONICS, "129.00", 120, 60, true),
            new SeedProduct("HM-MUG-01", "Stoneware Mug", Category.HOME, "8.50", 400, 3000, false),
            new SeedProduct("HM-LAMP-02", "LED Desk Lamp", Category.HOME, "34.99", 1200, 1500, true),
            new SeedProduct("HM-TOWL-03", "Bath Towel Set", Category.HOME, "27.40", 1500, 1500, false),
            new SeedProduct("HM-CNDL-04", "Soy Candle", Category.HOME, "14.25", 450, 1500, false),
            new SeedProduct("HM-PILL-05", "Throw Pillow", Category.HOME, "22.00", 600, 1500, false),
            new SeedProduct("AP-HOOD-01", "Fleece Hoodie", Category.APPAREL, "45.00", 650, 1500, true),
            new SeedProduct("AP-SOCK-02", "Wool Socks 3-Pack", Category.APPAREL, "18.60", 200, 3000, false),
            new SeedProduct("AP-CAP-03", "Baseball Cap", Category.APPAREL, "21.50", 150, 1500, false),
            new SeedProduct(LIMITED_SKU, "Limited Runner 'Midnight'", Category.APPAREL, "160.00", 900, 40, false),
            new SeedProduct("GR-COFF-01", "Coffee Beans 1kg", Category.GROCERY, "24.90", 1000, 1500, false),
            new SeedProduct("GR-TEA-02", "Green Tea 100ct", Category.GROCERY, "11.30", 250, 3000, false),
            new SeedProduct("GR-CHOC-03", "Dark Chocolate Bar", Category.GROCERY, "3.75", 100, 5000, false),
            new SeedProduct("GR-NUTS-04", "Trail Mix 500g", Category.GROCERY, "9.80", 500, 3000, false),
            new SeedProduct("TY-BLOK-01", "Building Blocks 500pc", Category.TOYS, "39.99", 1100, 1500, true),
            new SeedProduct("TY-PUZL-02", "1000pc Puzzle", Category.TOYS, "17.50", 800, 1500, false),
            new SeedProduct("TY-PLSH-03", "Plush Bear", Category.TOYS, "15.00", 300, 1500, false));

    /** Cheap consumables that B2B accounts buy by the case. */
    static final List<String> BULK_SKUS = List.of(
            "GR-CHOC-03", "GR-TEA-02", "GR-NUTS-04", "HM-MUG-01", "AP-SOCK-02", "EL-CABL-04");

    private static final String[] FIRST = {
            "Ava", "Liam", "Noah", "Mia", "Zoe", "Arjun", "Sofia", "Mateo", "Priya", "Chen", "Omar", "Lena",
            "Kai", "Nora", "Diego", "Aisha", "Jonas", "Yuki", "Elena", "Sam"};
    private static final String[] LAST = {
            "Nguyen", "Garcia", "Smith", "Patel", "Kim", "Johnson", "Silva", "Brown", "Singh", "Okafor",
            "Muller", "Rossi", "Tanaka", "Lopez", "Walker", "Haddad"};
    private static final String[] REGIONS = {
            "CA", "CA", "CA", "TX", "TX", "NY", "NY", "WA", "IL", "FL", "MA", "CO", "OR", "NV", "AZ", "GA", "AK", "HI"};

    private DemoData() {
    }

    static void loadCatalog(ShipFastApp app) {
        for (SeedProduct seed : PRODUCTS) {
            app.catalog().add(new Product(seed.sku(), seed.name(), seed.category(), Money.of(seed.price()), seed.grams()));
            app.inventory().receive(seed.sku(), seed.stock());
        }
    }

    static List<Customer> loadCustomers(ShipFastApp app, Random random, int count) {
        List<Customer> customers = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String first = FIRST[random.nextInt(FIRST.length)];
            String last = LAST[random.nextInt(LAST.length)];
            boolean business = random.nextDouble() < 0.04;
            String name = business ? last + " & Co. Supplies" : first + " " + last;
            String region = REGIONS[random.nextInt(REGIONS.length)];
            double tierRoll = random.nextDouble();
            LoyaltyTier tier = tierRoll < 0.7 ? LoyaltyTier.STANDARD : tierRoll < 0.9 ? LoyaltyTier.SILVER : LoyaltyTier.GOLD;
            String token = random.nextDouble() < 0.02 ? "tok_decline_insufficient_funds_" + i : "tok_visa_" + i;
            Customer customer = new Customer(
                    String.format("cus-%04d", i),
                    name,
                    (first + "." + last + i + "@example.com").toLowerCase(),
                    Address.us(random.nextInt(9000) + 100 + " Main St", "Springfield", region,
                            String.format("%05d", random.nextInt(99999))),
                    token,
                    tier,
                    business);
            customers.add(app.customers().save(customer));
        }
        return customers;
    }
}
