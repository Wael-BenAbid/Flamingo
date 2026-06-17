import Foundation
import Observation
import FirebaseAuth

/**
 * FlamingoStore — thin wrapper around FirestoreService.
 *
 * Keeps the existing API surface (reservations, workers, inventory, addX methods)
 * so all SwiftUI views continue to work without modification.
 * Data is now live from Firestore via Snapshot Listeners instead of in-memory arrays.
 *
 * Injection:
 *   In FlamingoiOSApp:
 *     @State private var firestoreService = FirestoreService()
 *     ContentView().environment(firestoreService)
 *
 *   In any view:
 *     @Environment(FirestoreService.self) private var store
 */
@Observable
@MainActor
final class FlamingoStore {

    // ── Live data — delegated to FirestoreService ─────────────────

    /// Access through the FirestoreService environment object in views.
    /// These pass-through properties allow legacy code that references FlamingoStore
    /// to continue compiling while the real data lives in FirestoreService.
    private let service: FirestoreService
    private var authHandle: AuthStateDidChangeListenerHandle?

    var reservations: [Reservation]   { service.reservations   }
    var workers:      [Worker]        { service.workers        }
    var inventory:    [InventoryItem] { service.inventory      }
    var kitchenOrders:[KitchenOrder]  { service.kitchenOrders  }
    var todayArrivals:[Reservation]   { service.todayArrivals  }
    var lowStockItems:[InventoryItem] { service.lowStockItems  }
    var isLoading:    Bool            { service.isLoading      }
    var lastError:    String?         { service.lastError      }

    func filteredKitchenOrders(for role: OceanRole) -> [KitchenOrder] {
        service.filteredKitchenOrders(for: role)
    }

    func kitchenItemTotals(for role: OceanRole) -> [(name: String, quantity: Int)] {
        service.kitchenItemTotals(for: role)
    }

    init(service: FirestoreService = FirestoreService()) {
        self.service = service
        // Auto-start/stop Firestore listeners when Firebase auth state changes.
        // This ensures no permission errors accumulate after sign-out.
        authHandle = Auth.auth().addStateDidChangeListener { [weak service] _, user in
            Task { @MainActor in
                if user != nil {
                    await service?.startListening()
                } else {
                    await service?.stopListening()
                }
            }
        }
    }

    deinit {
        if let h = authHandle { Auth.auth().removeStateDidChangeListener(h) }
    }

    // ── Mutations: Reservations ────────────────────────────────────

    func addReservation(_ reservation: Reservation) {
        Task {
            try? await service.addReservation(reservation)
        }
    }

    func updateReservationStatus(id: String, status: String) {
        Task {
            try? await service.updateReservationStatus(id: id, status: status)
        }
    }

    func deleteReservation(id: String) {
        Task {
            try? await service.deleteReservation(id: id)
        }
    }

    // ── Mutations: Workers ─────────────────────────────────────────

    func addWorker(_ worker: Worker) {
        // Workers are created through AuthService.createStaffAccount
        // which sets the Firestore document; the Snapshot Listener picks it up.
        // No client-side optimistic insert needed.
    }

    // ── Mutations: Inventory ───────────────────────────────────────

    func addInventoryItem(_ item: InventoryItem) {
        Task {
            try? await service.addInventoryItem(item)
        }
    }

    func updateStock(itemId: String, quantity: Int) {
        Task {
            try? await service.updateStock(itemId: itemId, newQuantity: quantity)
        }
    }

    // ── Mutations: Kitchen Orders ──────────────────────────────────

    func addKitchenOrder(_ order: KitchenOrder) {
        Task {
            try? await service.addKitchenOrder(order)
        }
    }

    func advanceOrderStatus(order: KitchenOrder) {
        let next: KitchenOrderStatus
        switch order.orderStatus {
        case .pending:              next = .preparing
        case .preparing, .inProgress: next = .ready
        case .ready:                next = .served
        case .served:               return
        }
        Task {
            try? await service.updateOrderStatus(id: order.id, status: next)
        }
    }

    func setOrderStatus(id: String, status: KitchenOrderStatus) {
        Task {
            try? await service.updateOrderStatus(id: id, status: status)
        }
    }

    // ── Mutations: Arrivals ────────────────────────────────────────

    func checkIn(reservationId: String) {
        Task {
            try? await service.checkInArrival(reservationId: reservationId)
        }
    }

    func markAbsent(reservationId: String) {
        Task {
            try? await service.markAbsent(reservationId: reservationId)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────

    func stopListening() {
        Task { await service.stopListening() }
    }
}
