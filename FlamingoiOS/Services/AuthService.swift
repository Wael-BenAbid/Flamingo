import Foundation
import FirebaseAuth
import FirebaseFirestore

// Canonical 5-role system — mirrors USER_ROLES in shared/constants.ts and Firestore rules.
enum OceanRole: String, CaseIterable {
    case admin       = "admin"
    case responsable = "responsable"
    case cuisinier   = "cuisinier"
    case barman      = "barman"
    case serveur     = "serveur"
    case none        = "none"

    var label: String {
        switch self {
        case .admin:       return "Administrateur"
        case .responsable: return "Responsable"
        case .cuisinier:   return "Cuisinier"
        case .barman:      return "Barman"
        case .serveur:     return "Serveur"
        case .none:        return "Aucun accès"
        }
    }

    // Feature access matrix — mirrors STAFF_FEATURE_ACCESS in shared/constants.ts.
    var allowedFeatures: Set<String> {
        switch self {
        case .admin:
            return ["dashboard", "reservations", "arrivals", "workers", "stock",
                    "reports", "settings", "menuTables", "kitchenOrders", "placeOrder", "finance"]
        case .responsable:
            return ["dashboard", "reservations", "arrivals", "workers", "stock",
                    "settings", "menuTables", "kitchenOrders", "placeOrder"]
        case .cuisinier:
            return ["arrivals", "stock", "kitchenOrders"]
        case .barman:
            return ["arrivals", "stock", "kitchenOrders"]
        case .serveur:
            return ["arrivals", "placeOrder", "workers", "menuTables"]
        case .none:
            return []
        }
    }
}

// Maps any raw Firestore / Custom Claims string to a canonical OceanRole.
// Handles legacy role names (cuisine → cuisinier, chef_serveur → serveur, etc.)
// so the iOS app stays compatible during Firestore data migration.
private func normalizeRole(_ raw: String?) -> OceanRole {
    guard let raw else { return .none }
    let s = raw.trimmingCharacters(in: .whitespaces)
        .lowercased()
        .replacingOccurrences(of: " ", with: "_")
        .replacingOccurrences(of: "-", with: "_")
        .folding(options: .diacriticInsensitive, locale: .current)

    switch s {
    case "admin", "administrator", "administrateur":
        return .admin
    case "responsable", "manager", "gerant":
        return .responsable
    case "cuisinier", "cuisine", "cook", "kitchen",
         "chef_cuisine", "chef_cuisinier", "chef_de_cuisine":
        return .cuisinier
    case "barman", "bar", "barmaid", "bartender":
        return .barman
    case "serveur", "server", "waiter",
         "chef_serveur",
         "securite", "securite", "nettoyage",
         "employee", "employe", "staff":
        return .serveur
    default:
        return .none
    }
}

// Admin email whitelist — read from FlamingoConfig (single source of truth for iOS).
private var adminEmails: Set<String> { Set(FlamingoConfig.adminEmails) }

@MainActor
final class AuthService: ObservableObject {
    static let shared = AuthService()

    @Published var currentUser: User?      = nil
    @Published var currentRole: OceanRole = .none
    @Published var isLoading: Bool        = false
    @Published var errorMessage: String?  = nil

    private let auth = Auth.auth()
    private let db   = Firestore.firestore()
    private var authStateHandle: AuthStateDidChangeListenerHandle?

    private init() {
        authStateHandle = auth.addStateDidChangeListener { [weak self] _, user in
            Task { await self?.handleAuthStateChange(user: user) }
        }
    }

    deinit {
        if let handle = authStateHandle { auth.removeStateDidChangeListener(handle) }
    }

    // MARK: – Sign In

    func signIn(email: String, password: String) async {
        isLoading = true
        errorMessage = nil
        do {
            let result = try await auth.signIn(withEmail: email, password: password)
            // forceRefresh = true so newly-assigned Custom Claims are visible immediately.
            currentRole = await resolveRole(for: result.user, forceRefresh: true)
        } catch {
            errorMessage = friendlyError(error)
        }
        isLoading = false
    }

    func signOut() {
        try? auth.signOut()
        currentUser = nil
        currentRole = .none
    }

    // MARK: – Role resolution
    // Order: admin email whitelist → Custom Claims → Firestore fallback.
    private func resolveRole(for user: User, forceRefresh: Bool) async -> OceanRole {
        if let email = user.email, FlamingoConfig.isAdminEmail(email) {
            return .admin
        }
        if let claimRole = await getRoleFromClaims(user: user, forceRefresh: forceRefresh) {
            return claimRole
        }
        return await getRoleFromFirestore(uid: user.uid, email: user.email)
    }

    private func getRoleFromClaims(user: User, forceRefresh: Bool) async -> OceanRole? {
        do {
            let result = try await user.getIDTokenResult(forcingRefresh: forceRefresh)
            guard let raw = result.claims["role"] as? String else { return nil }
            let role = normalizeRole(raw)
            return role == .none ? nil : role
        } catch {
            return nil
        }
    }

    private func getRoleFromFirestore(uid: String, email: String?) async -> OceanRole {
        do {
            let byUid = try? await db.collection("workers")
                .whereField("uid", isEqualTo: uid).limit(to: 1).getDocuments()
                .documents.first

            let doc: DocumentSnapshot?
            if byUid != nil {
                doc = byUid
            } else if let email {
                doc = try? await db.collection("workers")
                    .whereField("email", isEqualTo: email).limit(to: 1).getDocuments()
                    .documents.first
            } else {
                doc = nil
            }

            guard let d = doc else { return .none }
            let raw = (d.data()?["role"] as? String)
                   ?? (d.data()?["category"] as? String)
            return normalizeRole(raw)
        } catch {
            return .none
        }
    }

    // MARK: – Helpers

    private func handleAuthStateChange(user: User?) async {
        currentUser = user
        if let user {
            currentRole = await resolveRole(for: user, forceRefresh: false)
        } else {
            currentRole = .none
        }
    }

    private func friendlyError(_ error: Error) -> String {
        let code = AuthErrorCode(rawValue: (error as NSError).code)
        switch code {
        case .wrongPassword, .invalidCredential: return "Email ou mot de passe incorrect."
        case .userNotFound:                      return "Aucun compte trouvé avec cet email."
        case .networkError:                      return "Vérifiez votre connexion internet."
        default:                                 return error.localizedDescription
        }
    }
}
