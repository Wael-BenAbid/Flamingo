import SwiftUI
import FirebaseFirestore

// MARK: – Local models
struct FinanceRecord: Identifiable {
    let id: String
    let workerId: String
    let workerName: String
    let amount: Double
    let date: String
    let reason: String
    let type: FinanceType
}

enum FinanceType: String {
    case advance = "advance"
    case penalty = "penalty"
    case payment = "payment"

    var label: String {
        switch self {
        case .advance: return "Avance"
        case .penalty: return "Pénalité"
        case .payment: return "Paiement"
        }
    }

    var icon: String {
        switch self {
        case .advance: return "arrow.up.circle.fill"
        case .penalty: return "exclamationmark.circle.fill"
        case .payment: return "checkmark.circle.fill"
        }
    }

    var color: Color {
        switch self {
        case .advance: return .orange
        case .penalty: return .red
        case .payment: return .green
        }
    }
}

// MARK: – ViewModel
@Observable
@MainActor
final class FinanceViewModel {

    var records: [FinanceRecord] = []
    var workers: [String: String] = [:]   // uid → fullName
    var isLoading = false
    var errorMessage: String? = nil
    var selectedType: FinanceType = .advance

    private let db = Firestore.firestore()

    func loadAll() async {
        isLoading = true
        errorMessage = nil
        do {
            // Load workers for name resolution
            let wSnap = try await db.collection("workers").getDocuments()
            workers = Dictionary(uniqueKeysWithValues: wSnap.documents.compactMap { doc -> (String, String)? in
                guard let name = doc["fullName"] as? String else { return nil }
                let uid = (doc["uid"] as? String) ?? doc.documentID
                return (uid, name)
            })

            async let advances = fetchRecords(collection: "advances", type: .advance)
            async let penalties = fetchRecords(collection: "penalties", type: .penalty)
            async let payments = fetchRecords(collection: "payments", type: .payment)

            records = try await (advances + penalties + payments)
                .sorted { $0.date > $1.date }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    private func fetchRecords(collection: String, type: FinanceType) async throws -> [FinanceRecord] {
        try await db.collection(collection).getDocuments().documents.compactMap { doc in
            guard let wid = doc["workerId"] as? String,
                  let amount = doc["amount"] as? Double,
                  let date = doc["date"] as? String else { return nil }
            return FinanceRecord(
                id: doc.documentID,
                workerId: wid,
                workerName: workers[wid] ?? "Employé",
                amount: amount,
                date: date,
                reason: (doc["reason"] as? String) ?? (doc["method"] as? String) ?? "",
                type: type
            )
        }
    }

    var filteredRecords: [FinanceRecord] {
        records.filter { $0.type == selectedType }
    }

    var totalFiltered: Double {
        filteredRecords.reduce(0) { $0 + $1.amount }
    }
}

// MARK: – View
struct FinanceView: View {

    @State private var vm = FinanceViewModel()

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Type picker
                Picker("Type", selection: $vm.selectedType) {
                    ForEach([FinanceType.advance, .penalty, .payment], id: \.self) { t in
                        Label(t.label, systemImage: t.icon).tag(t)
                    }
                }
                .pickerStyle(.segmented)
                .padding()

                // Total banner
                HStack {
                    Text("Total \(vm.selectedType.label)s")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(String(format: "%.2f DT", vm.totalFiltered))
                        .font(.headline)
                        .foregroundStyle(vm.selectedType.color)
                }
                .padding(.horizontal)
                .padding(.bottom, 8)

                if vm.isLoading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if vm.filteredRecords.isEmpty {
                    ContentUnavailableView(
                        "Aucun enregistrement",
                        systemImage: "tray",
                        description: Text("Aucun(e) \(vm.selectedType.label.lowercased()) enregistré(e).")
                    )
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(vm.filteredRecords) { record in
                        FinanceRowView(record: record)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Financier")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { Task { await vm.loadAll() } } label: {
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
        .task { await vm.loadAll() }
    }
}

private struct FinanceRowView: View {
    let record: FinanceRecord

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: record.type.icon)
                .foregroundStyle(record.type.color)
                .font(.title3)
                .frame(width: 32)

            VStack(alignment: .leading, spacing: 2) {
                Text(record.workerName)
                    .font(.subheadline).fontWeight(.medium)
                if !record.reason.isEmpty {
                    Text(record.reason)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Text(record.date)
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }

            Spacer()

            Text(String(format: "%.2f DT", record.amount))
                .font(.subheadline)
                .foregroundStyle(record.type.color)
        }
        .padding(.vertical, 4)
    }
}
