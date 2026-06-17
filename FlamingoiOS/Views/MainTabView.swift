import SwiftUI

struct MainTabView: View {
    @EnvironmentObject private var auth: AuthService
    @Environment(AppViewModel.self) private var model
    @State private var selectedTab: AppTab = .dashboard

    var body: some View {
        @Bindable var bindableModel = model

        TabView(selection: $selectedTab) {
            // Dashboard — admin + responsable
            if auth.currentRole.allowedFeatures.contains("dashboard") {
                DashboardView(store: model.store)
                    .tabItem { Label("Dashboard", systemImage: "rectangle.grid.2x2") }
                    .tag(AppTab.dashboard)
            }

            // Réservations — admin + responsable
            if auth.currentRole.allowedFeatures.contains("reservations") {
                ReservationsView(store: model.store)
                    .tabItem { Label("Réservations", systemImage: "calendar") }
                    .tag(AppTab.reservations)
            }

            // Arrivées du jour — tous les employés
            if auth.currentRole.allowedFeatures.contains("arrivals") {
                DailyArrivalTabPlaceholder()
                    .tabItem { Label("Arrivées", systemImage: "person.badge.clock") }
                    .tag(AppTab.arrivals)
            }

            // Commandes cuisine/bar — cuisinier + barman + admin
            if auth.currentRole.allowedFeatures.contains("kitchenOrders") {
                OrderTicketView(store: model.store)
                    .tabItem { Label("Cuisine", systemImage: "flame") }
                    .tag(AppTab.kitchen)
            }

            // Prise de commande (POS) — serveur + admin + responsable
            if auth.currentRole.allowedFeatures.contains("placeOrder") {
                PlaceOrderView()
                    .tabItem { Label("Commande", systemImage: "cart") }
                    .tag(AppTab.placeOrder)
            }

            // Employés — admin + responsable + serveur
            if auth.currentRole.allowedFeatures.contains("workers") {
                WorkersView(store: model.store)
                    .tabItem { Label("Employés", systemImage: "person.3") }
                    .tag(AppTab.workers)
            }

            // Stock — admin + responsable + cuisinier + barman
            if auth.currentRole.allowedFeatures.contains("stock") {
                InventoryView(store: model.store)
                    .tabItem { Label("Stock", systemImage: "cube.box") }
                    .tag(AppTab.inventory)
            }

            // Menu & Tables — admin + responsable + serveur
            if auth.currentRole.allowedFeatures.contains("menuTables") {
                MenuTablesView()
                    .tabItem { Label("Menu", systemImage: "fork.knife") }
                    .tag(AppTab.menuTables)
            }

            // Rapports — admin seulement
            if auth.currentRole.allowedFeatures.contains("reports") {
                ReportsView()
                    .tabItem { Label("Rapports", systemImage: "chart.bar.doc.horizontal") }
                    .tag(AppTab.reports)
            }

            // Financier — admin seulement
            if auth.currentRole.allowedFeatures.contains("finance") {
                FinanceView()
                    .tabItem { Label("Financier", systemImage: "creditcard") }
                    .tag(AppTab.finance)
            }

            // Paramètres — toujours visible
            SettingsView()
                .tabItem { Label("Paramètres", systemImage: "gearshape") }
                .tag(AppTab.settings)
        }
        .tint(.teal)
        .onChange(of: auth.currentRole) { _, _ in
            // Reset to first accessible tab when role changes (e.g., after login)
            selectedTab = firstAccessibleTab()
        }
    }

    private func firstAccessibleTab() -> AppTab {
        let features = auth.currentRole.allowedFeatures
        if features.contains("dashboard")    { return .dashboard }
        if features.contains("arrivals")     { return .arrivals }
        if features.contains("kitchenOrders") { return .kitchen }
        return .settings
    }
}

// Placeholder for the arrivals tab (can be replaced by a dedicated ArrivalsView)
private struct DailyArrivalTabPlaceholder: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                "Arrivées du jour",
                systemImage: "person.badge.clock.fill",
                description: Text("Vue disponible dans la prochaine mise à jour.")
            )
            .navigationTitle("Arrivées")
        }
    }
}
