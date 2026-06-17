import SwiftUI

struct WorkersView: View {
    @State private var showingForm = false
    @State private var fullName = ""
    @State private var category = WorkerCategory.serveur
    @State private var dailyWage = 40.0
    @State private var startDate = Date.now

    let store: FlamingoStore

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.workers) { worker in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(worker.fullName)
                            .font(.headline)
                        Text("\(worker.category) • \(Int(worker.dailyWage)) DT/jour")
                            .foregroundStyle(.secondary)
                        Text("Début: \(worker.startDate ?? "—")")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Employés")
            .toolbar {
                Button("Ajouter") { showingForm = true }
            }
            .sheet(isPresented: $showingForm) {
                NavigationStack {
                    Form {
                        Section("Identité") {
                            TextField("Nom complet", text: $fullName)
                            Picker("Catégorie", selection: $category) {
                                ForEach(WorkerCategory.allCases) { Text($0.rawValue).tag($0) }
                            }
                        }
                        Section("Conditions") {
                            DatePicker("Date d'entrée", selection: $startDate, displayedComponents: .date)
                            Stepper("Salaire journalier: \(Int(dailyWage)) DT", value: $dailyWage, in: 0...1000, step: 5)
                        }
                    }
                    .navigationTitle("Nouveau Travailleur")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Annuler") { showingForm = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Enregistrer") {
                                let dateStr = {
                                    let f = DateFormatter()
                                    f.dateFormat = "yyyy-MM-dd"
                                    return f.string(from: startDate)
                                }()
                                store.addWorker(
                                    Worker(fullName: fullName, category: category.rawValue, dailyWage: dailyWage, startDate: dateStr)
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
