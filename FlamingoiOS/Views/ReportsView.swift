import SwiftUI
import FirebaseFirestore

// MARK: – Daily Report model (iOS-local, matches DailyReport in Android)
struct DailyReport: Identifiable {
    let id: String
    let date: String
    let totalRevenue: Double
    let totalExpenses: Double
    let netProfit: Double
    let totalReservations: Int
    let totalArrivals: Int
    let totalClients: Int
    let totalProductSales: Double
    let totalWorkerAdvances: Double
    let totalWorkerPayments: Double
    let activeWorkers: Int
    let staffPresent: Int
    let totalProductUnitsSold: Int
}

// MARK: – ViewModel
@Observable
@MainActor
final class ReportsViewModel {

    var todayReport: DailyReport? = nil
    var isLoading = false
    var errorMessage: String? = nil

    private let db = Firestore.firestore()

    func loadTodayReport() async {
        isLoading = true
        errorMessage = nil
        let today = isoToday()

        do {
            async let reservationsSnap = db.collection("reservations")
                .whereField("date", isEqualTo: today).getDocuments()
            async let salesSnap = db.collection("sales")
                .whereField("date", isEqualTo: today).getDocuments()
            async let workersSnap = db.collection("workers").getDocuments()
            async let advancesSnap = db.collection("advances")
                .whereField("date", isEqualTo: today).getDocuments()
            async let paymentsSnap = db.collection("payments")
                .whereField("date", isEqualTo: today).getDocuments()
            async let positionsSnap = db.collection("positions").getDocuments()

            let res       = try await reservationsSnap
            let sales     = try await salesSnap
            let workers   = try await workersSnap
            let advances  = try await advancesSnap
            let payments  = try await paymentsSnap
            let positions = try await positionsSnap

            let confirmed = res.documents.filter {
                let s = $0["status"] as? String ?? ""
                return s == "confirmed" || s == "checked-in"
            }

            // Build position map for fallback price calculation
            var positionMap: [String: QueryDocumentSnapshot] = [:]
            for doc in positions.documents {
                let key = (doc["type"] as? String ?? "").trimmingCharacters(in: .whitespaces).lowercased()
                if !key.isEmpty { positionMap[key] = doc }
            }

            // Use totalPrice when set, otherwise fall back to position-based calculation
            let reservationRevenue = confirmed.reduce(0.0) { sum, doc in
                let totalPrice = doc["totalPrice"] as? Double ?? 0.0
                if totalPrice > 0 { return sum + totalPrice }
                let posType = (doc["positionType"] as? String ?? "").trimmingCharacters(in: .whitespaces).lowercased()
                let pos = positionMap[posType]
                let adultPx   = pos?["price"] as? Double ?? 0.0
                let childPx   = pos?["childPrice"] as? Double ?? adultPx * 0.5
                let adults    = Double(doc["adults"] as? Int ?? 0)
                let children  = Double(doc["children"] as? Int ?? 0)
                return sum + adults * adultPx + children * childPx
            }

            let productSalesRevenue = sales.documents.reduce(0.0) {
                $0 + (($1["totalPrice"] as? Double) ?? 0.0)
            }
            // Include cost of goods sold (COGS) in expenses
            let productCost = sales.documents.reduce(0.0) { sum, doc in
                let totalCost = doc["totalCost"] as? Double ?? 0.0
                if totalCost > 0 { return sum + totalCost }
                let qty    = Double(doc["quantity"] as? Int ?? 0)
                let unitBuy = doc["unitBuyPrice"] as? Double ?? 0.0
                return sum + qty * unitBuy
            }
            let totalAdvances = advances.documents.reduce(0.0) {
                $0 + (($1["amount"] as? Double) ?? 0.0)
            }
            let totalPayments = payments.documents.reduce(0.0) {
                $0 + (($1["amount"] as? Double) ?? 0.0)
            }
            let totalRevenue  = reservationRevenue + productSalesRevenue
            let totalExpenses = productCost + totalAdvances + totalPayments
            let totalClients  = confirmed.reduce(0) {
                $0 + (($1["adults"] as? Int) ?? 0) + (($1["children"] as? Int) ?? 0)
            }
            let unitsSold = sales.documents.reduce(0) {
                $0 + (($1["quantity"] as? Int) ?? 0)
            }
            let staffPresent  = workers.documents.filter {
                ($0["currentPresence"] as? String) == "present"
            }.count
            let activeWorkers = workers.documents.filter {
                ($0["isActive"] as? Bool) == true
            }.count

            todayReport = DailyReport(
                id: today,
                date: today,
                totalRevenue: totalRevenue,
                totalExpenses: totalExpenses,
                netProfit: totalRevenue - totalExpenses,
                totalReservations: res.documents.count,
                totalArrivals: confirmed.count,
                totalClients: totalClients,
                totalProductSales: productSalesRevenue,
                totalWorkerAdvances: totalAdvances,
                totalWorkerPayments: totalPayments,
                activeWorkers: activeWorkers,
                staffPresent: staffPresent,
                totalProductUnitsSold: unitsSold
            )
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func isoToday() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }
}

// MARK: – View
struct ReportsView: View {

    @State private var vm = ReportsViewModel()

    var body: some View {
        NavigationStack {
            Group {
                if vm.isLoading {
                    ProgressView("Chargement...")
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if let r = vm.todayReport {
                    ScrollView {
                        LazyVStack(spacing: 16) {
                            revenueSection(r)
                            clientSection(r)
                            staffSection(r)
                            financialSection(r)
                        }
                        .padding()
                    }
                } else {
                    ContentUnavailableView(
                        "Aucun rapport",
                        systemImage: "doc.text.magnifyingglass",
                        description: Text("Aucune donnée pour aujourd'hui.")
                    )
                }
            }
            .navigationTitle("Rapport du jour")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { Task { await vm.loadTodayReport() } } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .alert("Erreur", isPresented: .constant(vm.errorMessage != nil)) {
                Button("OK") { vm.errorMessage = nil }
            } message: {
                Text(vm.errorMessage ?? "")
            }
        }
        .task { await vm.loadTodayReport() }
    }

    // MARK: – Sections

    @ViewBuilder
    private func revenueSection(_ r: DailyReport) -> some View {
        ReportCard(title: "Revenus", icon: "chart.bar.fill", color: .teal) {
            KpiRow(label: "Chiffre d'affaires", value: formatted(r.totalRevenue), highlight: true)
            KpiRow(label: "Réservations", value: formatted(r.totalRevenue - r.totalProductSales))
            KpiRow(label: "Ventes produits", value: formatted(r.totalProductSales))
            Divider()
            KpiRow(label: "Dépenses", value: formatted(r.totalExpenses), color: .red)
            KpiRow(label: "Bénéfice net", value: formatted(r.netProfit),
                   color: r.netProfit >= 0 ? .green : .red, highlight: true)
        }
    }

    @ViewBuilder
    private func clientSection(_ r: DailyReport) -> some View {
        ReportCard(title: "Clients", icon: "person.2.fill", color: .blue) {
            KpiRow(label: "Réservations totales", value: "\(r.totalReservations)")
            KpiRow(label: "Arrivées confirmées", value: "\(r.totalArrivals)")
            KpiRow(label: "Clients accueillis", value: "\(r.totalClients)", highlight: true)
        }
    }

    @ViewBuilder
    private func staffSection(_ r: DailyReport) -> some View {
        ReportCard(title: "Équipe", icon: "person.3.fill", color: .orange) {
            KpiRow(label: "Employés actifs", value: "\(r.activeWorkers)")
            KpiRow(label: "Présents aujourd'hui", value: "\(r.staffPresent)", highlight: true)
        }
    }

    @ViewBuilder
    private func financialSection(_ r: DailyReport) -> some View {
        ReportCard(title: "Financier", icon: "creditcard.fill", color: .purple) {
            KpiRow(label: "Avances versées", value: formatted(r.totalWorkerAdvances), color: .red)
            KpiRow(label: "Salaires payés", value: formatted(r.totalWorkerPayments), color: .red)
            KpiRow(label: "Unités vendues", value: "\(r.totalProductUnitsSold)")
        }
    }

    private func formatted(_ v: Double) -> String {
        String(format: "%.2f DT", v)
    }
}

// MARK: – Reusable sub-components

private struct ReportCard<Content: View>: View {
    let title: String
    let icon: String
    let color: Color
    @ViewBuilder let content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Label(title, systemImage: icon)
                .font(.headline)
                .foregroundStyle(color)
            content
        }
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

private struct KpiRow: View {
    let label: String
    let value: String
    var color: Color = .primary
    var highlight: Bool = false

    var body: some View {
        HStack {
            Text(label)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .fontWeight(highlight ? .bold : .regular)
                .foregroundStyle(color)
        }
        .font(highlight ? .subheadline : .caption)
    }
}
