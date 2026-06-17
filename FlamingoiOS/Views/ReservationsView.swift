import SwiftUI

struct ReservationsView: View {
    @State private var showingForm = false
    @State private var firstName = ""
    @State private var lastName = ""
    @State private var phone = ""
    @State private var adults = 0
    @State private var children = 0
    @State private var positionType = "Terrasse"
    @State private var positionNumber = ""
    @State private var notes = ""
    @State private var status = ReservationStatus.pending

    let store: FlamingoStore

    private let positionTypes = ["Terrasse", "Parasol", "Cabane", "Payotte", "Cabane avec piscine privée"]

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.reservations) { reservation in
                    VStack(alignment: .leading, spacing: 6) {
                        Text("\(reservation.firstName) \(reservation.lastName)")
                            .font(.headline)
                        Text("\(reservation.positionType) \(reservation.positionNumber.map { "N°\($0)" } ?? "")")
                            .foregroundStyle(.secondary)
                        Text(reservation.reservationStatus.label)
                            .font(.caption)
                            .foregroundStyle(.blue)
                    }
                }
            }
            .navigationTitle("Réservations")
            .toolbar {
                Button("Nouvelle") { showingForm = true }
            }
            .sheet(isPresented: $showingForm) {
                NavigationStack {
                    Form {
                        Section("Client") {
                            TextField("Prénom", text: $firstName)
                            TextField("Nom", text: $lastName)
                            TextField("Téléphone", text: $phone)
                            Stepper("Adultes: \(adults)", value: $adults, in: 0...30)
                            Stepper("Enfants: \(children)", value: $children, in: 0...30)
                        }
                        Section("Placement") {
                            Picker("Position", selection: $positionType) {
                                ForEach(positionTypes, id: \.self) { Text($0) }
                            }
                            TextField("N° de position", text: $positionNumber)
                        }
                        Section("Notes") {
                            TextField("Notes spéciales", text: $notes, axis: .vertical)
                        }
                    }
                    .navigationTitle("Nouvelle Réservation")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Annuler") { showingForm = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Confirmer") {
                                let dateStr = {
                                    let f = DateFormatter()
                                    f.dateFormat = "yyyy-MM-dd"
                                    return f.string(from: Date())
                                }()
                                store.addReservation(
                                    Reservation(
                                        firstName: firstName,
                                        lastName: lastName,
                                        phone: phone,
                                        adults: adults,
                                        children: children,
                                        date: dateStr,
                                        time: "12:00",
                                        positionType: positionType,
                                        positionNumber: positionNumber.isEmpty ? nil : positionNumber,
                                        status: status.rawValue,
                                        notes: notes.isEmpty ? nil : notes
                                    )
                                )
                                showingForm = false
                            }
                        }
                    }
                }
            }
        }
    }
}
