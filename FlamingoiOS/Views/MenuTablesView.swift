import SwiftUI
import FirebaseFirestore

// MARK: – Local models (read-only — write is Admin/Responsable only)
struct MenuCategory: Identifiable, Codable {
    @DocumentID var documentId: String?
    var id: String { documentId ?? _lid.uuidString }
    private var _lid = UUID()

    var name: String
    var description: String?
    var order: Int?
    var available: Bool?

    enum CodingKeys: String, CodingKey {
        case documentId, name, description, order, available
    }
}

struct MenuItemDetail: Identifiable, Codable {
    @DocumentID var documentId: String?
    var id: String { documentId ?? _lid.uuidString }
    private var _lid = UUID()

    var name: String
    var description: String?
    var price: Double
    var categoryId: String?
    var available: Bool?

    enum CodingKeys: String, CodingKey {
        case documentId, name, description, price, categoryId, available
    }
}

// MARK: – ViewModel
@Observable
@MainActor
final class MenuTablesViewModel {

    var categories: [MenuCategory] = []
    var items: [MenuItemDetail] = []
    var isLoading = false
    var errorMessage: String? = nil

    private let db = Firestore.firestore()

    func load() async {
        isLoading = true
        errorMessage = nil
        do {
            async let catSnap = db.collection(FlamingoConfig.Collections.menuCategories)
                .order(by: "order").getDocuments()
            async let itemSnap = db.collection(FlamingoConfig.Collections.menuItems)
                .getDocuments()

            let (cats, itms) = try await (catSnap, itemSnap)

            categories = cats.documents.compactMap { try? $0.data(as: MenuCategory.self) }
            items = itms.documents.compactMap { try? $0.data(as: MenuItemDetail.self) }
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func itemsForCategory(_ categoryId: String) -> [MenuItemDetail] {
        items.filter { ($0.categoryId ?? "") == categoryId && ($0.available ?? true) }
    }
}

// MARK: – View
struct MenuTablesView: View {

    @State private var vm = MenuTablesViewModel()
    @State private var selectedCategory: MenuCategory? = nil

    var body: some View {
        NavigationSplitView {
            // Sidebar — categories
            Group {
                if vm.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                } else if vm.categories.isEmpty {
                    ContentUnavailableView(
                        "Aucune catégorie",
                        systemImage: "fork.knife",
                        description: Text("Ajoutez des catégories depuis le panel Web.")
                    )
                } else {
                    List(vm.categories, selection: $selectedCategory) { cat in
                        Label {
                            VStack(alignment: .leading) {
                                Text(cat.name).font(.subheadline)
                                Text("\(vm.itemsForCategory(cat.id).count) article(s)")
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        } icon: {
                            Image(systemName: "tag.fill").foregroundStyle(.teal)
                        }
                        .tag(cat)
                    }
                    .navigationTitle("Menu")
                }
            }
        } detail: {
            if let cat = selectedCategory {
                MenuItemListView(
                    category: cat,
                    items: vm.itemsForCategory(cat.id)
                )
            } else {
                ContentUnavailableView(
                    "Sélectionnez une catégorie",
                    systemImage: "sidebar.left"
                )
            }
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button { Task { await vm.load() } } label: {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .alert("Erreur", isPresented: .constant(vm.errorMessage != nil)) {
            Button("OK") { vm.errorMessage = nil }
        } message: {
            Text(vm.errorMessage ?? "")
        }
        .task { await vm.load() }
    }
}

private struct MenuItemListView: View {
    let category: MenuCategory
    let items: [MenuItemDetail]

    var body: some View {
        Group {
            if items.isEmpty {
                ContentUnavailableView(
                    "Aucun article",
                    systemImage: "tray",
                    description: Text("Cette catégorie ne contient pas encore d'articles.")
                )
            } else {
                List(items) { item in
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(item.name).font(.subheadline).fontWeight(.medium)
                            if let desc = item.description, !desc.isEmpty {
                                Text(desc).font(.caption).foregroundStyle(.secondary)
                            }
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 4) {
                            Text(String(format: "%.2f DT", item.price))
                                .font(.subheadline).fontWeight(.semibold)
                            if item.available == false {
                                Label("Indisponible", systemImage: "xmark.circle.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.red)
                            }
                        }
                    }
                    .padding(.vertical, 2)
                }
            }
        }
        .navigationTitle(category.name)
        .navigationBarTitleDisplayMode(.inline)
    }
}
