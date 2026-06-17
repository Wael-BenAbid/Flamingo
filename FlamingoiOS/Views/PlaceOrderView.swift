import SwiftUI
import FirebaseFirestore
import FirebaseAuth

// MARK: – Models
struct MenuItem: Identifiable, Codable {
    @DocumentID var documentId: String?
    var id: String { documentId ?? _lid.uuidString }
    private var _lid = UUID()

    var name: String
    var price: Double
    var categoryId: String?
    var available: Bool?

    enum CodingKeys: String, CodingKey {
        case documentId, name, price, categoryId, available
    }
}

struct OrderLine: Identifiable {
    let id = UUID()
    var item: MenuItem
    var quantity: Int
    var note: String = ""
}

// MARK: – ViewModel
@Observable
@MainActor
final class PlaceOrderViewModel {

    var menuItems: [MenuItem] = []
    var orderLines: [OrderLine] = []
    var tableNumber: String = ""
    var isLoading = false
    var isSubmitting = false
    var successMessage: String? = nil
    var errorMessage: String? = nil
    var category: String = "cuisine"  // "cuisine" | "bar"

    private let db = Firestore.firestore()

    func loadMenu() async {
        isLoading = true
        do {
            let snap = try await db.collection(FlamingoConfig.Collections.menuItems).getDocuments()
            menuItems = snap.documents.compactMap { try? $0.data(as: MenuItem.self) }
                .filter { $0.available ?? true }
                .sorted { $0.name < $1.name }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func addToOrder(_ item: MenuItem) {
        if let idx = orderLines.firstIndex(where: { $0.item.id == item.id }) {
            orderLines[idx].quantity += 1
        } else {
            orderLines.append(OrderLine(item: item, quantity: 1))
        }
    }

    func removeFromOrder(_ item: MenuItem) {
        if let idx = orderLines.firstIndex(where: { $0.item.id == item.id }) {
            if orderLines[idx].quantity > 1 {
                orderLines[idx].quantity -= 1
            } else {
                orderLines.remove(at: idx)
            }
        }
    }

    func quantityFor(_ item: MenuItem) -> Int {
        orderLines.first { $0.item.id == item.id }?.quantity ?? 0
    }

    var total: Double {
        orderLines.reduce(0) { $0 + ($1.item.price * Double($1.quantity)) }
    }

    func submitOrder() async {
        guard !tableNumber.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Veuillez saisir un numéro de table."
            return
        }
        guard !orderLines.isEmpty else {
            errorMessage = "Le panier est vide."
            return
        }

        isSubmitting = true
        errorMessage = nil

        let user = Auth.auth().currentUser
        let serverName = user?.displayName ?? user?.email ?? "Serveur"
        let serverId = user?.uid ?? "unknown"

        let items: [[String: Any]] = orderLines.map { line in
            [
                "name": line.item.name,
                "quantity": line.quantity,
                "note": line.note,
                "unit_price": line.item.price
            ]
        }

        let data: [String: Any] = [
            "server_id": serverId,
            "server_name": serverName,
            "table_number": tableNumber.trimmingCharacters(in: .whitespaces),
            "status": "pending",
            "items": items,
            "total_price": total,
            "category": category,
            "created_at": Timestamp(),
            "updated_at": Timestamp()
        ]

        do {
            try await db.collection(FlamingoConfig.Collections.tableOrders).addDocument(data: data)
            orderLines.removeAll()
            tableNumber = ""
            successMessage = "Commande envoyée en cuisine !"
        } catch {
            errorMessage = "Impossible d'envoyer la commande : \(error.localizedDescription)"
        }

        isSubmitting = false
    }
}

// MARK: – View
struct PlaceOrderView: View {

    @State private var vm = PlaceOrderViewModel()
    @State private var showCart = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Category picker
                Picker("Catégorie", selection: $vm.category) {
                    Label("Cuisine", systemImage: "flame").tag("cuisine")
                    Label("Bar", systemImage: "wineglass").tag("bar")
                }
                .pickerStyle(.segmented)
                .padding()

                if vm.isLoading {
                    ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                } else {
                    List(vm.menuItems) { item in
                        MenuItemRow(
                            item: item,
                            quantity: vm.quantityFor(item),
                            onAdd: { vm.addToOrder(item) },
                            onRemove: { vm.removeFromOrder(item) }
                        )
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Prise de commande")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showCart = true
                    } label: {
                        Label("Panier", systemImage: "cart.fill")
                            .overlay(alignment: .topTrailing) {
                                if !vm.orderLines.isEmpty {
                                    Text("\(vm.orderLines.count)")
                                        .font(.caption2).bold()
                                        .foregroundStyle(.white)
                                        .padding(4)
                                        .background(.red)
                                        .clipShape(Circle())
                                        .offset(x: 8, y: -8)
                                }
                            }
                    }
                }
            }
            .sheet(isPresented: $showCart) {
                CartSheet(vm: vm)
            }
            .alert("Succès", isPresented: .constant(vm.successMessage != nil)) {
                Button("OK") { vm.successMessage = nil }
            } message: {
                Text(vm.successMessage ?? "")
            }
            .alert("Erreur", isPresented: .constant(vm.errorMessage != nil)) {
                Button("OK") { vm.errorMessage = nil }
            } message: {
                Text(vm.errorMessage ?? "")
            }
        }
        .task { await vm.loadMenu() }
    }
}

private struct MenuItemRow: View {
    let item: MenuItem
    let quantity: Int
    let onAdd: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.name).font(.subheadline).fontWeight(.medium)
                Text(String(format: "%.2f DT", item.price))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            if quantity > 0 {
                HStack(spacing: 12) {
                    Button(action: onRemove) {
                        Image(systemName: "minus.circle.fill").foregroundStyle(.red)
                    }
                    Text("\(quantity)").font(.subheadline).fontWeight(.bold).frame(minWidth: 20)
                    Button(action: onAdd) {
                        Image(systemName: "plus.circle.fill").foregroundStyle(.teal)
                    }
                }
                .buttonStyle(.plain)
            } else {
                Button(action: onAdd) {
                    Image(systemName: "plus.circle.fill")
                        .foregroundStyle(.teal).font(.title3)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

private struct CartSheet: View {
    @Bindable var vm: PlaceOrderViewModel
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Table") {
                    TextField("Numéro de table", text: $vm.tableNumber)
                        .keyboardType(.numberPad)
                }

                Section("Articles (\(vm.orderLines.count))") {
                    ForEach(vm.orderLines) { line in
                        HStack {
                            Text(line.item.name)
                            Spacer()
                            Text("×\(line.quantity)")
                                .foregroundStyle(.secondary)
                            Text(String(format: "%.2f DT", line.item.price * Double(line.quantity)))
                                .fontWeight(.medium)
                        }
                    }
                }

                Section {
                    HStack {
                        Text("Total").fontWeight(.bold)
                        Spacer()
                        Text(String(format: "%.2f DT", vm.total))
                            .fontWeight(.bold).foregroundStyle(.teal)
                    }
                }

                Section {
                    Button {
                        Task {
                            await vm.submitOrder()
                            if vm.errorMessage == nil { dismiss() }
                        }
                    } label: {
                        if vm.isSubmitting {
                            ProgressView()
                        } else {
                            Label("Envoyer la commande", systemImage: "paperplane.fill")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(vm.isSubmitting || vm.orderLines.isEmpty)
                }
            }
            .navigationTitle("Panier")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Fermer") { dismiss() }
                }
            }
        }
    }
}
