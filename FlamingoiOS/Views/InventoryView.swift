import SwiftUI

struct InventoryView: View {
    @State private var showingForm = false
    @State private var name = ""
    @State private var category = InventoryCategory.boissons
    @State private var stockQuantity = 0
    @State private var minStock = 10
    @State private var buyPrice = 0.0
    @State private var sellPrice = 0.0

    let store: FlamingoStore

    var body: some View {
        NavigationStack {
            List {
                ForEach(store.inventory) { item in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(item.name)
                            .font(.headline)
                        Text("\(item.category) • Stock \(item.stockQuantity)")
                            .foregroundStyle(.secondary)
                        Text("Achat \(item.buyPrice, specifier: "%.2f") DT • Vente \(item.sellPrice, specifier: "%.2f") DT")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Stock")
            .toolbar {
                Button("Ajouter") { showingForm = true }
            }
            .sheet(isPresented: $showingForm) {
                NavigationStack {
                    Form {
                        Section("Produit") {
                            TextField("Nom du produit", text: $name)
                            Picker("Catégorie", selection: $category) {
                                ForEach(InventoryCategory.allCases) { Text($0.rawValue).tag($0) }
                            }
                        }
                        Section("Quantités") {
                            Stepper("Quantité: \(stockQuantity)", value: $stockQuantity, in: 0...1000)
                            Stepper("Stock minimum: \(minStock)", value: $minStock, in: 0...1000)
                        }
                        Section("Prix") {
                            TextField("Prix achat", value: $buyPrice, format: .number)
                                .keyboardType(.decimalPad)
                            TextField("Prix vente", value: $sellPrice, format: .number)
                                .keyboardType(.decimalPad)
                        }
                    }
                    .navigationTitle("Nouveau Produit")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) {
                            Button("Annuler") { showingForm = false }
                        }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Enregistrer") {
                                store.addInventoryItem(
                                    InventoryItem(
                                        name: name,
                                        category: category.rawValue,
                                        stockQuantity: stockQuantity,
                                        minStock: minStock,
                                        buyPrice: buyPrice,
                                        sellPrice: sellPrice
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
