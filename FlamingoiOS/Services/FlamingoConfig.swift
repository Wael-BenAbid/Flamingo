import Foundation

// Single source of truth for app-level constants on iOS.
// The admin email list must match ADMIN_EMAILS in shared/constants.ts
// and the Cloud Functions ALLOWED_ADMIN_EMAILS environment variable.
enum FlamingoConfig {
    static let adminEmails: [String] = [
        "waelbenabid1@gmail.com",
        "abidos.games@gmail.com",
        "admin@gmail.com",
    ]

    static func isAdminEmail(_ email: String) -> Bool {
        adminEmails.contains(email.lowercased().trimmingCharacters(in: .whitespaces))
    }

    // Firestore collection names — canonical, shared with Web/Android.
    enum Collections {
        static let reservations  = "reservations"
        static let workers       = "workers"
        static let attendance    = "attendance"
        static let inventory     = "inventory"
        static let tableOrders   = "table_orders"
        static let menuCategories = "menu_categories"
        static let menuItems     = "menu_items"
        static let advances      = "advances"
        static let penalties     = "penalties"
        static let payments      = "payments"
        static let sales         = "sales"
        static let settings      = "settings"
    }
}
