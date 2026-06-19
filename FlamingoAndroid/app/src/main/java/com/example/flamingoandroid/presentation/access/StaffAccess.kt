package com.example.flamingoandroid.presentation.access

import com.example.flamingoandroid.presentation.activities.AdminSection

object StaffAccess {
    const val ROLE_ADMIN       = "admin"
    const val ROLE_RESPONSABLE = "responsable"
    const val ROLE_SERVEUR     = "serveur"
    const val ROLE_CUISINIER   = "cuisinier"
    const val ROLE_BARMAN      = "barman"
    const val ROLE_NONE        = "none"

    const val FEATURE_DASHBOARD      = "dashboard"
    const val FEATURE_RESERVATIONS   = "reservations"
    const val FEATURE_ARRIVALS       = "arrivals"
    const val FEATURE_WORKERS        = "workers"
    const val FEATURE_STOCK          = "stock"
    const val FEATURE_REPORTS        = "reports"
    const val FEATURE_SETTINGS       = "settings"
    const val FEATURE_MENU_TABLES    = "menuTables"
    const val FEATURE_KITCHEN_ORDERS = "kitchenOrders"
    const val FEATURE_PLACE_ORDER    = "placeOrder"
    const val FEATURE_PAYMENT        = "payment"

    private val featureAccess: Map<String, Set<String>> = mapOf(
        ROLE_ADMIN to setOf(
            FEATURE_DASHBOARD, FEATURE_RESERVATIONS, FEATURE_ARRIVALS,
            FEATURE_WORKERS, FEATURE_STOCK, FEATURE_REPORTS, FEATURE_SETTINGS,
            FEATURE_MENU_TABLES, FEATURE_KITCHEN_ORDERS, FEATURE_PLACE_ORDER, FEATURE_PAYMENT,
        ),
        ROLE_RESPONSABLE to setOf(
            FEATURE_DASHBOARD, FEATURE_RESERVATIONS, FEATURE_ARRIVALS,
            FEATURE_WORKERS, FEATURE_STOCK, FEATURE_SETTINGS,
            FEATURE_MENU_TABLES, FEATURE_KITCHEN_ORDERS, FEATURE_PLACE_ORDER, FEATURE_PAYMENT,
        ),
        ROLE_SERVEUR to setOf(
            FEATURE_PLACE_ORDER,
        ),
        ROLE_CUISINIER to setOf(
            FEATURE_KITCHEN_ORDERS,
        ),
        ROLE_BARMAN to setOf(
            FEATURE_KITCHEN_ORDERS,
        ),
        ROLE_NONE to emptySet(),
    )

    fun normalize(role: String?): String {
        val raw = role?.trim().orEmpty()
        if (raw.isBlank()) return ROLE_NONE
        val s = raw.lowercase()
            .replace(Regex("[éèê]"), "e")
            .replace('à', 'a').replace('ç', 'c')
            .replace(Regex("[\\s\\-]+"), "_")
        return when (s) {
            "admin"                              -> ROLE_ADMIN
            "responsable", "manager"             -> ROLE_RESPONSABLE
            "cuisinier", "cuisine", "cook",
            "kitchen", "chef_cuisinier",
            "chef_cuisiner", "chef_cuisine",
            "chef_de_cuisine"                    -> ROLE_CUISINIER
            "barman", "bartender", "bar",
            "barmaid"                            -> ROLE_BARMAN
            "serveur", "server", "waiter",
            "chef_serveur", "chef_serveur_",
            "securite", "sécurité", "securité",
            "nettoyage", "employee"              -> ROLE_SERVEUR
            ROLE_ADMIN, ROLE_RESPONSABLE,
            ROLE_SERVEUR, ROLE_CUISINIER,
            ROLE_BARMAN, ROLE_NONE               -> s
            else                                 -> ROLE_NONE
        }
    }

    fun hasFeature(role: String, feature: String): Boolean =
        featureAccess[normalize(role)]?.contains(feature) == true

    fun allowedFeatures(role: String): Set<String> =
        featureAccess[normalize(role)] ?: emptySet()

    fun defaultSection(role: String): AdminSection = when (normalize(role)) {
        ROLE_ADMIN, ROLE_RESPONSABLE -> AdminSection.DASHBOARD
        ROLE_CUISINIER, ROLE_BARMAN  -> AdminSection.KITCHEN_ORDERS
        else                         -> AdminSection.KITCHEN_ORDERS
    }

    fun roleLabel(role: String): String = when (normalize(role)) {
        ROLE_ADMIN       -> "Administrateur"
        ROLE_RESPONSABLE -> "Responsable"
        ROLE_SERVEUR     -> "Serveur"
        ROLE_CUISINIER   -> "Cuisinier"
        ROLE_BARMAN      -> "Barman"
        else             -> "Personnel"
    }
}
