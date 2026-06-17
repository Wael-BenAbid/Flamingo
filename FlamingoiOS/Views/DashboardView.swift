import SwiftUI

struct DashboardView: View {
    let store: FlamingoStore

    var body: some View {
        NavigationStack {
            List {
                Section("Résumé") {
                    row(title: "Réservations", value: "\(store.reservations.count)")
                    row(title: "Travailleurs", value: "\(store.workers.count)")
                    row(title: "Produits", value: "\(store.inventory.count)")
                }

                Section("Aperçu") {
                    ForEach(store.reservations.prefix(3)) { reservation in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("\(reservation.firstName) \(reservation.lastName)")
                                .font(.headline)
                            Text("\(reservation.positionType) - \(reservation.reservationStatus.label)")
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("Ocean iOS")
        }
    }

    private func row(title: String, value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .fontWeight(.semibold)
        }
    }
}
