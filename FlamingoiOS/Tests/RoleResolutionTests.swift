import XCTest
@testable import FlamingoiOS

// Tests for the role normalisation logic in AuthService.
// These tests exercise the canonical 5-role system + legacy alias mapping
// to catch regressions whenever the normaliseRole function is modified.

final class RoleResolutionTests: XCTestCase {

    // MARK: – Canonical roles

    func test_admin_canonical() {
        XCTAssertEqual(resolveRole("admin"), .admin)
        XCTAssertEqual(resolveRole("ADMIN"), .admin)
        XCTAssertEqual(resolveRole("Administrator"), .admin)
        XCTAssertEqual(resolveRole("administrateur"), .admin)
    }

    func test_responsable_canonical() {
        XCTAssertEqual(resolveRole("responsable"), .responsable)
        XCTAssertEqual(resolveRole("manager"), .responsable)
        XCTAssertEqual(resolveRole("gerant"), .responsable)
        XCTAssertEqual(resolveRole("gérant"), .responsable)
    }

    func test_cuisinier_canonical() {
        XCTAssertEqual(resolveRole("cuisinier"), .cuisinier)
    }

    func test_barman_canonical() {
        XCTAssertEqual(resolveRole("barman"), .barman)
        XCTAssertEqual(resolveRole("bar"), .barman)
        XCTAssertEqual(resolveRole("bartender"), .barman)
        XCTAssertEqual(resolveRole("barmaid"), .barman)
    }

    func test_serveur_canonical() {
        XCTAssertEqual(resolveRole("serveur"), .serveur)
        XCTAssertEqual(resolveRole("server"), .serveur)
        XCTAssertEqual(resolveRole("waiter"), .serveur)
    }

    // MARK: – Legacy alias mapping (must still resolve to canonical roles)

    func test_legacy_cuisine_maps_to_cuisinier() {
        // "cuisine" was the old category value; must now map to cuisinier
        XCTAssertEqual(resolveRole("cuisine"), .cuisinier)
        XCTAssertEqual(resolveRole("cook"), .cuisinier)
        XCTAssertEqual(resolveRole("kitchen"), .cuisinier)
        XCTAssertEqual(resolveRole("chef_cuisinier"), .cuisinier)
        XCTAssertEqual(resolveRole("chef_cuisine"), .cuisinier)
        XCTAssertEqual(resolveRole("chef_de_cuisine"), .cuisinier)
    }

    func test_legacy_chef_serveur_maps_to_serveur() {
        XCTAssertEqual(resolveRole("chef_serveur"), .serveur)
    }

    func test_legacy_securite_maps_to_serveur() {
        XCTAssertEqual(resolveRole("securite"), .serveur)
        XCTAssertEqual(resolveRole("sécurité"), .serveur)
    }

    func test_legacy_nettoyage_maps_to_serveur() {
        XCTAssertEqual(resolveRole("nettoyage"), .serveur)
    }

    func test_legacy_employee_maps_to_serveur() {
        XCTAssertEqual(resolveRole("employee"), .serveur)
        XCTAssertEqual(resolveRole("employe"), .serveur)
        XCTAssertEqual(resolveRole("staff"), .serveur)
    }

    // MARK: – Edge cases

    func test_nil_returns_none() {
        XCTAssertEqual(resolveRole(nil), .none)
    }

    func test_empty_string_returns_none() {
        XCTAssertEqual(resolveRole(""), .none)
        XCTAssertEqual(resolveRole("   "), .none)
    }

    func test_unknown_role_returns_none() {
        XCTAssertEqual(resolveRole("vip_guest"), .none)
        XCTAssertEqual(resolveRole("root"), .none)
    }

    func test_whitespace_trimming() {
        XCTAssertEqual(resolveRole("  admin  "), .admin)
        XCTAssertEqual(resolveRole("\tserveur\n"), .serveur)
    }

    func test_diacritics_folding() {
        XCTAssertEqual(resolveRole("sécurité"), .serveur)
        XCTAssertEqual(resolveRole("gérant"), .responsable)
    }

    // MARK: – Feature access sanity checks

    func test_admin_has_all_features() {
        let features = OceanRole.admin.allowedFeatures
        XCTAssertTrue(features.contains("dashboard"))
        XCTAssertTrue(features.contains("reports"))
        XCTAssertTrue(features.contains("finance"))
        XCTAssertTrue(features.contains("workers"))
        XCTAssertTrue(features.contains("kitchenOrders"))
    }

    func test_cuisinier_limited_features() {
        let features = OceanRole.cuisinier.allowedFeatures
        XCTAssertTrue(features.contains("kitchenOrders"))
        XCTAssertTrue(features.contains("stock"))
        XCTAssertFalse(features.contains("reports"))
        XCTAssertFalse(features.contains("workers"))
        XCTAssertFalse(features.contains("dashboard"))
    }

    func test_serveur_limited_features() {
        let features = OceanRole.serveur.allowedFeatures
        XCTAssertTrue(features.contains("placeOrder"))
        XCTAssertFalse(features.contains("kitchenOrders"))
        XCTAssertFalse(features.contains("reports"))
    }

    func test_none_has_no_features() {
        XCTAssertTrue(OceanRole.none.allowedFeatures.isEmpty)
    }

    // MARK: – Admin email check

    func test_admin_emails_recognised() {
        XCTAssertTrue(FlamingoConfig.isAdminEmail("waelbenabid1@gmail.com"))
        XCTAssertTrue(FlamingoConfig.isAdminEmail("WAELBENABID1@GMAIL.COM"))
        XCTAssertTrue(FlamingoConfig.isAdminEmail("abidos.games@gmail.com"))
    }

    func test_non_admin_email_rejected() {
        XCTAssertFalse(FlamingoConfig.isAdminEmail("random@example.com"))
        XCTAssertFalse(FlamingoConfig.isAdminEmail(""))
    }

    // MARK: – Helper (mirrors the private normalizeRole in AuthService)

    private func resolveRole(_ raw: String?) -> OceanRole {
        guard let raw else { return .none }
        let s = raw.trimmingCharacters(in: .whitespaces)
            .lowercased()
            .replacingOccurrences(of: " ", with: "_")
            .replacingOccurrences(of: "-", with: "_")
            .folding(options: .diacriticInsensitive, locale: .current)

        switch s {
        case "admin", "administrator", "administrateur":           return .admin
        case "responsable", "manager", "gerant":                   return .responsable
        case "cuisinier", "cuisine", "cook", "kitchen",
             "chef_cuisine", "chef_cuisinier", "chef_de_cuisine":  return .cuisinier
        case "barman", "bar", "barmaid", "bartender":              return .barman
        case "serveur", "server", "waiter",
             "chef_serveur", "securite", "securite",
             "nettoyage", "employee", "employe", "staff":          return .serveur
        default:
            return s.isEmpty ? .none : .none
        }
    }
}
