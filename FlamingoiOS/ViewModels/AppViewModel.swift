import Foundation
import Observation

@Observable
@MainActor
final class AppViewModel {
    var selectedTab: AppTab = .dashboard
    let store = FlamingoStore()
}

enum AppTab: String, CaseIterable, Identifiable {
    case dashboard    = "Dashboard"
    case reservations = "Réservations"
    case arrivals     = "Arrivées"
    case kitchen      = "Cuisine"
    case placeOrder   = "Commande"
    case workers      = "Employés"
    case inventory    = "Stock"
    case menuTables   = "Menu"
    case reports      = "Rapports"
    case finance      = "Financier"
    case settings     = "Paramètres"

    var id: String { rawValue }

    // Which feature key this tab requires (mirrors STAFF_FEATURE_ACCESS in constants.ts)
    var requiredFeature: String? {
        switch self {
        case .dashboard:    return "dashboard"
        case .reservations: return "reservations"
        case .arrivals:     return "arrivals"
        case .kitchen:      return "kitchenOrders"
        case .placeOrder:   return "placeOrder"
        case .workers:      return "workers"
        case .inventory:    return "stock"
        case .menuTables:   return "menuTables"
        case .reports:      return "reports"
        case .finance:      return "finance"
        case .settings:     return nil   // always visible
        }
    }
}
