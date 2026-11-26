package com.asadfareed.swiftdrop.data

import com.asadfareed.swiftdrop.core.DemoCity

/** Fictional restaurants and menus for the demo city. All names are original. */
data class MenuItem(
    val id: String,
    val name: String,
    val description: String,
    val price: Long,
    val emoji: String,
    val popular: Boolean = false,
)

data class MenuSection(val title: String, val items: List<MenuItem>)

data class Restaurant(
    val id: String,
    val name: String,
    val cuisine: String,
    val category: String,
    val emoji: String,
    /** Card gradient (ARGB). */
    val colors: Pair<Long, Long>,
    val rating: Double,
    val reviews: Int,
    val prepMinutes: IntRange,
    /** Street-corner node in [DemoCity] where the restaurant sits. */
    val nodeId: String,
    val menu: List<MenuSection>,
)

data class DeliveryAddress(val label: String, val line: String, val nodeId: String)

object Catalog {

    val home = DeliveryAddress("Home", "14 Canal View Road", DemoCity.id(11, 6))
    const val COURIER_START = "r5c4"

    val categories = listOf("All", "Burgers", "Desi", "Pizza", "Sushi", "Healthy", "Desserts")

    val restaurants = listOf(
        Restaurant(
            "ember", "Ember & Oak", "Flame-grilled burgers", "Burgers", "🍔", 0xFFFF7A45 to 0xFFFF3D5A,
            4.8, 2_140, 15..25, DemoCity.id(2, 1),
            listOf(
                MenuSection("Popular", listOf(
                    MenuItem("e1", "Smash Double", "Two smashed patties, cheddar, pickles, house sauce", 890, "🍔", popular = true),
                    MenuItem("e2", "Firebird", "Crispy chicken thigh, hot honey, slaw", 790, "🍗", popular = true),
                )),
                MenuSection("Sides", listOf(
                    MenuItem("e3", "Loaded Fries", "Cheese sauce, jalapeños, crispy onions", 390, "🍟"),
                    MenuItem("e4", "Onion Rings", "Beer-battered, smoky mayo", 290, "🧅"),
                )),
                MenuSection("Drinks", listOf(
                    MenuItem("e5", "Salted Caramel Shake", "Thick, cold, dangerously good", 450, "🥤"),
                )),
            ),
        ),
        Restaurant(
            "saffron", "Saffron Lane", "Biryani & karahi", "Desi", "🍛", 0xFFF59E0B to 0xFFEA580C,
            4.9, 3_870, 20..30, DemoCity.id(4, 7),
            listOf(
                MenuSection("Popular", listOf(
                    MenuItem("s1", "Chicken Biryani", "Aged basmati, saffron, slow-cooked chicken", 650, "🍛", popular = true),
                    MenuItem("s2", "Karahi (half)", "Tomato-ginger chicken karahi, fresh coriander", 1_250, "🍲", popular = true),
                )),
                MenuSection("Breads", listOf(
                    MenuItem("s3", "Garlic Naan", "Tandoor-baked, brushed with garlic butter", 120, "🫓"),
                )),
                MenuSection("Drinks", listOf(MenuItem("s4", "Mint Lassi", "Yoghurt, mint, a pinch of salt", 250, "🥛"))),
            ),
        ),
        Restaurant(
            "nonna", "Slice of Nonna", "Wood-fired pizza", "Pizza", "🍕", 0xFFEF4444 to 0xFFB91C1C,
            4.7, 1_560, 18..28, DemoCity.id(1, 5),
            listOf(
                MenuSection("Pizzas", listOf(
                    MenuItem("n1", "Margherita", "San Marzano, fior di latte, basil", 1_190, "🍕", popular = true),
                    MenuItem("n2", "Hot Honey Pepperoni", "Pepperoni cups, chilli honey", 1_490, "🌶️", popular = true),
                )),
                MenuSection("Sweet", listOf(MenuItem("n3", "Tiramisu", "Espresso-soaked, mascarpone", 590, "🍰"))),
            ),
        ),
        Restaurant(
            "kaze", "Kaze Sushi Bar", "Rolls & bowls", "Sushi", "🍣", 0xFF0EA5E9 to 0xFF6366F1,
            4.8, 980, 20..35, DemoCity.id(8, 7),
            listOf(
                MenuSection("Rolls", listOf(
                    MenuItem("k1", "Salmon Avocado Roll", "8 pieces, sesame, spicy mayo", 1_350, "🍣", popular = true),
                    MenuItem("k2", "Tempura Prawn Roll", "8 pieces, eel sauce", 1_450, "🍤"),
                )),
                MenuSection("Bowls", listOf(MenuItem("k3", "Teriyaki Chicken Bowl", "Sticky rice, greens, pickles", 1_150, "🍱"))),
            ),
        ),
        Restaurant(
            "green", "Green Bowl Co.", "Salads & smoothies", "Healthy", "🥗", 0xFF22C55E to 0xFF0D9488,
            4.6, 720, 10..20, DemoCity.id(10, 3),
            listOf(
                MenuSection("Bowls", listOf(
                    MenuItem("g1", "Harvest Bowl", "Quinoa, roast veg, feta, tahini", 950, "🥗", popular = true),
                    MenuItem("g2", "Grilled Chicken Caesar", "Romaine, parmesan, sourdough crumbs", 890, "🥬"),
                )),
                MenuSection("Smoothies", listOf(MenuItem("g3", "Berry Blast", "Mixed berries, banana, oat milk", 480, "🫐"))),
            ),
        ),
        Restaurant(
            "cloud", "Sugar Cloud", "Desserts & coffee", "Desserts", "🧁", 0xFFEC4899 to 0xFF8B5CF6,
            4.9, 1_210, 10..15, DemoCity.id(6, 2),
            listOf(
                MenuSection("Treats", listOf(
                    MenuItem("c1", "Molten Lava Cake", "Warm chocolate centre, vanilla scoop", 550, "🍫", popular = true),
                    MenuItem("c2", "Cinnamon Roll", "Cream-cheese frosting", 390, "🥐"),
                )),
                MenuSection("Coffee", listOf(MenuItem("c3", "Iced Spanish Latte", "Double shot, condensed milk", 520, "☕"))),
            ),
        ),
    )

    fun restaurant(id: String): Restaurant = restaurants.first { it.id == id }

    fun item(id: String): Pair<Restaurant, MenuItem> =
        restaurants.firstNotNullOf { r -> r.menu.flatMap { it.items }.firstOrNull { it.id == id }?.let { r to it } }
}
