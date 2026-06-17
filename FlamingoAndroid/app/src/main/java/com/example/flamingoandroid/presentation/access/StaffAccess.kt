package com.example.flamingoandroid.presentation.access

import com.example.flamingoandroid.presentation.activities.AdminSection

object StaffAccess {
    const val ROLE_ADMIN = "admin"
    const val ROLE_RESPONSABLE = "responsable"
    const val ROLE_SERVEUR = "serveur"
    const val ROLE_CHEF_SERVEUR = "chef_serveur"
    const val ROLE_CUISINE = "cuisine"
    const val ROLE_SECURITE = "securite"
    const val ROLE_NETTOYAGE = "nettoyage"
    const val ROLE_EMPLOYEE = "employee"
    const val ROLE_NONE = "none"

    const val FEATURE_DASHBOARD = "dashboard"
    const val FEATURE_RESERVATIONS = "reservations"
    const val FEATURE_ARRIVALS = "arrivals"
    const val FEATURE_WORKERS = "workers"
    const val FEATURE_STOCK = "stock"
    const val FEATURE_REPORTS = "reports"
    const val FEATURE_SETTINGS = "settings"
    const val FEATURE_MENU_TABLES = "menuTables"
    const val FEATURE_KITCHEN_ORDERS = "kitchenOrders"
    const val FEATURE_PLACE_ORDER = "placeOrder"
    const val FEATURE_PAYMENT     = "payment"

    private val featureAccess: Map<String, Set<String>> = mapOf(
        ROLE_ADMIN to setOf(
            FEATURE_DASHBOARD,
            FEATURE_RESERVATIONS,
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
            FEATURE_STOCK,
            FEATURE_REPORTS,
            FEATURE_SETTINGS,
            FEATURE_MENU_TABLES,
            FEATURE_KITCHEN_ORDERS,
            FEATURE_PLACE_ORDER,
            FEATURE_PAYMENT,
        ),
        ROLE_RESPONSABLE to setOf(
            FEATURE_DASHBOARD,
            FEATURE_RESERVATIONS,
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
            FEATURE_STOCK,
            FEATURE_SETTINGS,
            FEATURE_MENU_TABLES,
            FEATURE_KITCHEN_ORDERS,
            FEATURE_PLACE_ORDER,
            FEATURE_PAYMENT,
        ),
        ROLE_SERVEUR to setOf(
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
            FEATURE_PLACE_ORDER,
        ),
        ROLE_CHEF_SERVEUR to setOf(
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
            FEATURE_STOCK,
        ),
        ROLE_CUISINE to setOf(
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
            FEATURE_STOCK,
            FEATURE_KITCHEN_ORDERS,
        ),
        ROLE_SECURITE to setOf(
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
        ),
        ROLE_NETTOYAGE to setOf(
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
        ),
        ROLE_EMPLOYEE to setOf(
            FEATURE_ARRIVALS,
            FEATURE_WORKERS,
        ),
        ROLE_NONE to emptySet(),
    )

    fun normalize(role: String?): String {
        val raw = role?.trim().orEmpty()
        if (raw.isBlank()) return ROLE_NONE

        val normalized = raw.lowercase()
            .replace(Regex("[\\s\\-]+"), "_")

        return when (normalized) {
            "chef_cuisinier",
            "chef_cuisiner",
            "cuisinier",
            "cook",
            "kitchen",
            "chef_cuisine" -> ROLE_CUISINE
            "chef_serveur",
            "chef_serveur_" -> ROLE_CHEF_SERVEUR
            "server",
            "waiter" -> ROLE_SERVEUR
            "manager" -> ROLE_RESPONSABLE
            "securité",
            "sécurité" -> ROLE_SECURITE
            "nettoyage" -> ROLE_NETTOYAGE
            ROLE_ADMIN,
            ROLE_RESPONSABLE,
            ROLE_SERVEUR,
            ROLE_CHEF_SERVEUR,
            ROLE_CUISINE,
            ROLE_SECURITE,
            ROLE_EMPLOYEE,
            ROLE_NONE -> normalized
            else -> normalized
        }
    }

    fun hasFeature(role: String, feature: String): Boolean {
        return featureAccess[normalize(role)]?.contains(feature) == true
    }

    fun allowedFeatures(role: String): Set<String> {
        return featureAccess[normalize(role)] ?: emptySet()
    }

    fun defaultSection(role: String): AdminSection {
        return when (normalize(role)) {
            ROLE_ADMIN -> AdminSection.DASHBOARD
            ROLE_RESPONSABLE -> AdminSection.DASHBOARD
            ROLE_SERVEUR -> AdminSection.DAILY_CHECK
            ROLE_CHEF_SERVEUR -> AdminSection.DAILY_CHECK
            ROLE_CUISINE -> AdminSection.INVENTORY
            ROLE_SECURITE -> AdminSection.DAILY_CHECK
            ROLE_NETTOYAGE -> AdminSection.DAILY_CHECK
            ROLE_EMPLOYEE -> AdminSection.DAILY_CHECK
            else -> AdminSection.DASHBOARD
        }
    }

    fun roleLabel(role: String): String {
        return when (normalize(role)) {
            ROLE_ADMIN -> "Administrateur"
            ROLE_RESPONSABLE -> "Responsable"
            ROLE_SERVEUR -> "Serveur"
            ROLE_CHEF_SERVEUR -> "Chef serveur"
            ROLE_CUISINE -> "Cuisine"
            ROLE_SECURITE -> "Sécurité"
            ROLE_NETTOYAGE -> "Nettoyage"
            ROLE_EMPLOYEE -> "Employé"
            else -> "Personnel"
        }
    }
}
