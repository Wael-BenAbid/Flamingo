import Foundation
import FirebaseFirestore   // Provides @DocumentID and FirestoreDecoder

// MARK: – Reservation

/// Maps to the `reservations` Firestore collection.
/// Dates are stored as "yyyy-MM-dd" strings to match Android.
struct Reservation: Identifiable, Hashable, Codable {
    @DocumentID var documentId: String?

    /// Stable SwiftUI id — uses the Firestore document ID when available.
    var id: String { documentId ?? _localId.uuidString }
    var _localId = UUID()

    var firstName:      String
    var lastName:       String
    var phone:          String
    var adults:         Int
    var children:       Int
    var date:           String          // "yyyy-MM-dd"
    var time:           String
    var positionType:   String
    var positionNumber: String?
    var status:         String          // "pending" | "confirmed" | "cancelled" | "absent"
    var notes:          String?
    var totalPrice:     Double?
    var createdAt:      Timestamp?
    var updatedAt:      Timestamp?

    // ── Computed helpers ─────────────────────────────────────────────

    var reservationStatus: ReservationStatus {
        ReservationStatus(rawValue: status) ?? .pending
    }

    var fullName: String { "\(firstName) \(lastName)" }

    var dateValue: Date? {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.date(from: date)
    }

    // ── CodingKeys to bridge Firestore field names ────────────────────

    enum CodingKeys: String, CodingKey {
        case documentId, firstName, lastName, phone, adults, children
        case date, time, positionType, positionNumber, status, notes
        case totalPrice, createdAt, updatedAt
    }
}

enum ReservationStatus: String, CaseIterable, Identifiable {
    case pending   = "pending"
    case confirmed = "confirmed"
    case cancelled = "cancelled"
    case absent    = "absent"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .pending:   return "En attente"
        case .confirmed: return "Confirmé"
        case .cancelled: return "Annulé"
        case .absent:    return "Absent"
        }
    }

    var icon: String {
        switch self {
        case .pending:   return "clock.fill"
        case .confirmed: return "checkmark.circle.fill"
        case .cancelled: return "xmark.circle.fill"
        case .absent:    return "person.slash.fill"
        }
    }
}

// MARK: – Worker

/// Maps to the `workers` Firestore collection.
struct Worker: Identifiable, Hashable, Codable {
    @DocumentID var documentId: String?

    var id: String { documentId ?? _localId.uuidString }
    private var _localId = UUID()

    var fullName:         String
    var category:         String        // Raw string: "Serveur", "Cuisine", etc.
    var role:             String?       // "serveur", "cuisine", "admin" …
    var dailyWage:        Double
    var startDate:        String?       // "yyyy-MM-dd"
    var isActive:         Bool?
    var currentPresence:  String?       // "present" | "absent" | "half"
    var totalEarned:      Double?
    var attendanceCount:  Double?

    var workerCategory: WorkerCategory {
        WorkerCategory(rawValue: category) ?? .serveur
    }

    enum CodingKeys: String, CodingKey {
        case documentId, fullName, category, role, dailyWage, startDate
        case isActive, currentPresence, totalEarned, attendanceCount
    }
}

// Canonical 5 worker categories — mirrors WORKER_CATEGORIES in shared/constants.ts.
enum WorkerCategory: String, CaseIterable, Identifiable {
    case responsable = "Responsable"
    case cuisinier   = "Cuisinier"
    case barman      = "Barman"
    case serveur     = "Serveur"

    var id: String { rawValue }
}

// MARK: – InventoryItem

/// Maps to the `inventory` Firestore collection.
struct InventoryItem: Identifiable, Hashable, Codable {
    @DocumentID var documentId: String?

    var id: String { documentId ?? _localId.uuidString }
    private var _localId = UUID()

    var name:          String
    var category:      String           // Raw string: "Boissons", "Nourriture" …
    var stockQuantity: Int
    var minStock:      Int
    var buyPrice:      Double
    var sellPrice:     Double
    var supplier:      String?
    var location:      String?

    var inventoryCategory: InventoryCategory {
        InventoryCategory(rawValue: category) ?? .autres
    }

    var isLowStock: Bool { stockQuantity <= minStock }

    enum CodingKeys: String, CodingKey {
        case documentId, name, category, stockQuantity, minStock
        case buyPrice, sellPrice, supplier, location
    }
}

enum InventoryCategory: String, CaseIterable, Identifiable {
    case boissons   = "Boissons"
    case nourriture = "Nourriture"
    case glaces     = "Glaces"
    case nettoyage  = "Produits nettoyage"
    case autres     = "Autres"

    var id: String { rawValue }
}

// MARK: – KitchenOrder

/// Maps to the `kitchenOrders` (or `orders`) Firestore collection.
/// Used by the Kitchen / Bar dashboards for real-time order tracking.
struct KitchenOrder: Identifiable, Hashable, Codable {
    @DocumentID var documentId: String?

    var id: String { documentId ?? _localId.uuidString }
    private var _localId = UUID()

    var tableNumber:    Int?        // iOS schema: integer tableNumber
    var tableNumberStr: String?     // Web schema: string table_number
    var items:          [KitchenOrderItem]
    var status:         String      // "pending" | "preparing" | "inProgress" | "ready" | "served"
    var category:       String?
    var notes:          String?
    var createdAt:      Timestamp?  // iOS field name
    var createdAtWeb:   Timestamp?  // Web field name (created_at)
    var updatedAt:      Timestamp?

    /// Display label — prefers web string form (e.g. "Cabane 5") over integer form.
    var tableLabel: String {
        if let s = tableNumberStr, !s.isEmpty { return s }
        if let n = tableNumber, n > 0 { return "Table \(n)" }
        return "—"
    }

    /// Creation date resolved from either schema field.
    var resolvedCreatedAt: Date {
        (createdAt ?? createdAtWeb)?.dateValue() ?? Date.distantFuture
    }

    var orderStatus: KitchenOrderStatus {
        KitchenOrderStatus(rawValue: status) ?? .pending
    }

    enum CodingKeys: String, CodingKey {
        case documentId, items, status, category, notes, updatedAt
        case tableNumber    = "tableNumber"
        case tableNumberStr = "table_number"
        case createdAt      = "createdAt"
        case createdAtWeb   = "created_at"
    }
}

// MARK: – Menu category / item info (for role-based kitchen filtering)

struct MenuCategoryInfo: Identifiable, Codable {
    @DocumentID var documentId: String?
    var id: String { documentId ?? "" }
    var targetRole: String?

    enum CodingKeys: String, CodingKey {
        case documentId
        case targetRole = "target_role"
    }
}

struct MenuItemInfo: Identifiable, Codable {
    @DocumentID var documentId: String?
    var id: String { documentId ?? "" }
    var categoryId: String?

    enum CodingKeys: String, CodingKey {
        case documentId
        case categoryId = "category_id"
    }
}

struct KitchenOrderItem: Hashable, Codable {
    var name:     String
    var quantity: Int
    var note:     String?
    var itemId:   String?

    enum CodingKeys: String, CodingKey {
        case name, quantity, note
        case itemId = "item_id"
    }
}

enum KitchenOrderStatus: String, CaseIterable, Identifiable {
    case pending    = "pending"
    case preparing  = "preparing"   // web status alias for inProgress
    case inProgress = "inProgress"
    case ready      = "ready"
    case served     = "served"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .pending:    return "En attente"
        case .preparing:  return "En préparation"
        case .inProgress: return "En préparation"
        case .ready:      return "Prêt"
        case .served:     return "Servi"
        }
    }

    var icon: String {
        switch self {
        case .pending:    return "clock.fill"
        case .preparing:  return "flame.fill"
        case .inProgress: return "flame.fill"
        case .ready:      return "checkmark.circle.fill"
        case .served:     return "tray.full.fill"
        }
    }
}

// MARK: – Attendance (today's check-in / check-out)

struct AttendanceRecord: Identifiable, Hashable, Codable {
    @DocumentID var documentId: String?

    var id: String { documentId ?? _localId.uuidString }
    private var _localId = UUID()

    var workerId:  String
    var date:      String   // "yyyy-MM-dd"
    var status:    String   // "present" | "absent" | "half"
    var time:      String?
    var createdAt: Timestamp?

    enum CodingKeys: String, CodingKey {
        case documentId, workerId, date, status, time, createdAt
    }
}
