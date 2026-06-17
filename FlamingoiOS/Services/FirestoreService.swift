import Foundation
import FirebaseFirestore

// MARK: – FirestoreService

/**
 * FirestoreService — real-time Firestore integration for FlamingoiOS.
 *
 * Architecture:
 *  • Single @Observable class — compatible with SwiftUI's @Environment injection.
 *  • One Snapshot Listener per collection; all listeners started/stopped together.
 *  • All published data is automatically decoded via `Codable` + FirebaseFirestore's
 *    `data(as:)` helper — no manual field mapping required.
 *
 * Collections managed:
 *  ┌────────────────────┬──────────────────────────────────────────┐
 *  │ reservations       │ Full list + today's arrivals (filtered)  │
 *  │ workers            │ Staff list with roles + attendance       │
 *  │ inventory          │ All stock items                          │
 *  │ table_orders       │ Real-time order queue for kitchen / bar  │
 *  │ attendance         │ Today's check-in records                 │
 *  └────────────────────┴──────────────────────────────────────────┘
 *
 * Usage:
 *   // In App:
 *   @State private var store = FirestoreService()
 *   store.startListening()
 *
 *   // In View:
 *   @Environment(FirestoreService.self) private var store
 *   Text("\(store.reservations.count) réservations")
 */
@Observable
@MainActor
final class FirestoreService {

    // ── Published state ────────────────────────────────────────────

    var reservations:    [Reservation]       = []
    var workers:         [Worker]           = []
    var inventory:       [InventoryItem]    = []
    var kitchenOrders:   [KitchenOrder]     = []
    var menuCategories:  [MenuCategoryInfo] = []
    var menuItems:       [MenuItemInfo]     = []
    var todayArrivals:   [Reservation]      = []
    var isLoading:       Bool               = false
    var lastError:       String?            = nil

    // ── Private ────────────────────────────────────────────────────

    private let db       = Firestore.firestore()
    private var handles: [ListenerRegistration] = []

    private var todayString: String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }

    // ── Lifecycle ──────────────────────────────────────────────────

    /** Call once after sign-in to activate all real-time listeners. */
    func startListening() {
        guard handles.isEmpty else { return }
        isLoading = true
        listenToReservations()
        listenToWorkers()
        listenToInventory()
        listenToKitchenOrders()
        listenToTodayArrivals()
        listenToMenuCategories()
        listenToMenuItems()
    }

    /** Call on sign-out to release all listeners and clear data. */
    func stopListening() {
        handles.forEach { $0.remove() }
        handles.removeAll()
        reservations   = []
        workers        = []
        inventory      = []
        kitchenOrders  = []
        menuCategories = []
        menuItems      = []
        todayArrivals  = []
        isLoading      = false
    }

    // MARK: – Private Snapshot Listeners ───────────────────────────

    private func listenToReservations() {
        let h = db.collection("reservations")
            .order(by: "date", descending: true)
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if let error {
                    self.lastError = error.localizedDescription
                    return
                }
                self.reservations = snap?.documents.compactMap {
                    try? $0.data(as: Reservation.self)
                } ?? []
                self.isLoading = false
            }
        handles.append(h)
    }

    private func listenToWorkers() {
        let h = db.collection("workers")
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if let error { self.lastError = error.localizedDescription; return }
                self.workers = snap?.documents.compactMap {
                    try? $0.data(as: Worker.self)
                } ?? []
            }
        handles.append(h)
    }

    private func listenToInventory() {
        let h = db.collection("inventory")
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if let error { self.lastError = error.localizedDescription; return }
                self.inventory = snap?.documents.compactMap {
                    try? $0.data(as: InventoryItem.self)
                } ?? []
            }
        handles.append(h)
    }

    /**
     * Kitchen orders — covers both web status ("pending","preparing") and iOS status ("inProgress").
     * Sorted in memory to avoid composite-index requirements.
     */
    private func listenToKitchenOrders() {
        let todayStart = Timestamp(date: Calendar.current.startOfDay(for: Date()))
        let h = db.collection(FlamingoConfig.Collections.tableOrders)
            .whereField("created_at", isGreaterThanOrEqualTo: todayStart)
            .whereField("status", in: ["pending", "preparing", "inProgress", "ready"])
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if let error { self.lastError = error.localizedDescription; return }
                self.kitchenOrders = (snap?.documents.compactMap {
                    try? $0.data(as: KitchenOrder.self)
                } ?? []).sorted { $0.resolvedCreatedAt < $1.resolvedCreatedAt }
                self.isLoading = false
            }
        handles.append(h)
    }

    private func listenToMenuCategories() {
        let h = db.collection("menu_categories")
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if let error { self.lastError = error.localizedDescription; return }
                self.menuCategories = snap?.documents.compactMap {
                    try? $0.data(as: MenuCategoryInfo.self)
                } ?? []
            }
        handles.append(h)
    }

    private func listenToMenuItems() {
        let h = db.collection("menu_items")
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if let error { self.lastError = error.localizedDescription; return }
                self.menuItems = snap?.documents.compactMap {
                    try? $0.data(as: MenuItemInfo.self)
                } ?? []
            }
        handles.append(h)
    }

    /**
     * Today's arrivals — real-time view of today's reservations.
     * The kitchen / security staff use this for client check-in.
     */
    private func listenToTodayArrivals() {
        let today = todayString
        let h = db.collection("reservations")
            .whereField("date", isEqualTo: today)
            .addSnapshotListener { [weak self] snap, error in
                guard let self else { return }
                if let error { self.lastError = error.localizedDescription; return }
                self.todayArrivals = snap?.documents.compactMap {
                    try? $0.data(as: Reservation.self)
                } ?? []
            }
        handles.append(h)
    }

    // MARK: – Mutations: Reservations ──────────────────────────────

    func addReservation(_ r: Reservation) async throws {
        var data = try Firestore.Encoder().encode(r)
        data["createdAt"] = Timestamp()
        data["updatedAt"] = Timestamp()
        try await db.collection("reservations").addDocument(data: data)
    }

    func updateReservation(id: String, _ r: Reservation) async throws {
        var data = try Firestore.Encoder().encode(r)
        data["updatedAt"] = Timestamp()
        try await db.collection("reservations").document(id).setData(data)
    }

    func updateReservationStatus(id: String, status: String) async throws {
        try await db.collection("reservations").document(id)
            .updateData(["status": status, "updatedAt": Timestamp()])
    }

    func deleteReservation(id: String) async throws {
        try await db.collection("reservations").document(id).delete()
    }

    // MARK: – Mutations: Kitchen Orders ────────────────────────────

    func addKitchenOrder(_ order: KitchenOrder) async throws {
        var data = try Firestore.Encoder().encode(order)
        data["createdAt"] = Timestamp()
        data["status"]    = "pending"
        try await db.collection(FlamingoConfig.Collections.tableOrders).addDocument(data: data)
    }

    func updateOrderStatus(id: String, status: KitchenOrderStatus) async throws {
        try await db.collection(FlamingoConfig.Collections.tableOrders).document(id)
            .updateData(["status": status.rawValue, "updatedAt": Timestamp()])
    }

    func deleteOrder(id: String) async throws {
        try await db.collection(FlamingoConfig.Collections.tableOrders).document(id).delete()
    }

    // MARK: – Mutations: Attendance / Arrivals ─────────────────────

    func checkInArrival(reservationId: String) async throws {
        try await updateReservationStatus(id: reservationId, status: "confirmed")
    }

    func markAbsent(reservationId: String) async throws {
        try await updateReservationStatus(id: reservationId, status: "absent")
    }

    // MARK: – Mutations: Inventory ─────────────────────────────────

    func updateStock(itemId: String, newQuantity: Int) async throws {
        try await db.collection("inventory").document(itemId)
            .updateData(["stockQuantity": newQuantity, "quantity": newQuantity])
    }

    func addInventoryItem(_ item: InventoryItem) async throws {
        let data = try Firestore.Encoder().encode(item)
        try await db.collection("inventory").addDocument(data: data)
    }

    func deleteInventoryItem(id: String) async throws {
        try await db.collection("inventory").document(id).delete()
    }

    // MARK: – Computed helpers ─────────────────────────────────────

    /** Active kitchen orders (pending or in-progress), separated by category. */
    var cuisineOrders: [KitchenOrder] {
        kitchenOrders.filter { ($0.category ?? "cuisine") == "cuisine" }
    }

    var barOrders: [KitchenOrder] {
        kitchenOrders.filter { $0.category == "bar" }
    }

    /** Low-stock items for dashboard alerts. */
    var lowStockItems: [InventoryItem] {
        inventory.filter { $0.isLowStock }
    }

    // MARK: – Role-based kitchen filtering

    /** item_id → target_role lookup built from menu categories. */
    private var itemRoleLookup: [String: String] {
        let categoryRoles = Dictionary(uniqueKeysWithValues:
            menuCategories.compactMap { c -> (String, String)? in
                guard !c.id.isEmpty, let role = c.targetRole else { return nil }
                return (c.id, role)
            }
        )
        var result: [String: String] = [:]
        for item in menuItems {
            guard !item.id.isEmpty, let catId = item.categoryId,
                  let role = categoryRoles[catId] else { continue }
            result[item.id] = role
        }
        return result
    }

    /** Orders filtered so each order only contains items relevant to the given role. */
    func filteredKitchenOrders(for role: OceanRole) -> [KitchenOrder] {
        guard role == .cuisinier || role == .barman else { return kitchenOrders }
        let roleStr = role.rawValue
        let lookup  = itemRoleLookup
        return kitchenOrders.compactMap { order in
            let kept = order.items.filter { item in
                guard let itemId = item.itemId, !itemId.isEmpty else { return true }
                guard let itemRole = lookup[itemId] else { return true }
                return itemRole == roleStr
            }
            guard !kept.isEmpty else { return nil }
            var copy = order
            copy.items = kept
            return copy
        }
    }

    /** Sorted (name, total quantity) pairs across all filtered active orders. */
    func kitchenItemTotals(for role: OceanRole) -> [(name: String, quantity: Int)] {
        let orders = filteredKitchenOrders(for: role)
        var totals: [String: (name: String, quantity: Int)] = [:]
        for order in orders {
            for item in order.items {
                guard !item.name.isEmpty else { continue }
                let key  = item.itemId ?? item.name
                let prev = totals[key]?.quantity ?? 0
                totals[key] = (name: item.name, quantity: prev + item.quantity)
            }
        }
        return totals.values.sorted { $0.quantity > $1.quantity }
    }
}
