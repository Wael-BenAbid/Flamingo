import SwiftUI

// MARK: – Data model

struct OrderTicket: Identifiable, Equatable {
    let id: String              // Firestore document ID — stable across re-renders
    let tableLabel: String      // "Cabane 5" (web) or "Table 12" (iOS)
    let items: [OrderItem]
    let status: TicketStatus
    let createdAt: Date

    static func == (lhs: OrderTicket, rhs: OrderTicket) -> Bool { lhs.id == rhs.id }

    enum TicketStatus: String {
        case pending    = "En attente"
        case inProgress = "En préparation"
        case ready      = "Prêt"
        case served     = "Servi"

        var color: Color {
            switch self {
            case .pending:    return .warmAmber
            case .inProgress: return .coralSunset
            case .ready:      return .lagoonTeal
            case .served:     return .mutedMist
            }
        }

        var icon: String {
            switch self {
            case .pending:    return "clock.fill"
            case .inProgress: return "flame.fill"
            case .ready:      return "checkmark.circle.fill"
            case .served:     return "tray.full.fill"
            }
        }
    }
}

struct OrderItem: Identifiable, Equatable {
    let id     = UUID()
    let name:   String
    let qty:    Int
    let note:   String?
    let itemId: String? // For role filtering — defaults to nil
}

// MARK: – Ticket list row (collapsed)

struct TicketRow: View {
    let ticket: OrderTicket
    let namespace: Namespace.ID
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {

                // Table badge
                ZStack {
                    Circle()
                        .fill(Color.warmAmber.opacity(0.14))
                        .matchedGeometryEffect(id: "badge_\(ticket.id)", in: namespace)

                    Text(ticket.tableLabel)
                        .font(.system(size: 13, weight: .black, design: .rounded))
                        .foregroundColor(.pearlWhite)
                        .lineLimit(1)
                        .minimumScaleFactor(0.5)
                        .padding(6)
                        .matchedGeometryEffect(id: "tableNum_\(ticket.id)", in: namespace)
                }
                .frame(width: 52, height: 52)

                // Summary
                VStack(alignment: .leading, spacing: 3) {
                    Text(ticket.tableLabel)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.pearlWhite)
                        .matchedGeometryEffect(id: "title_\(ticket.id)", in: namespace)

                    Text("\(ticket.items.count) article\(ticket.items.count > 1 ? "s" : "")")
                        .font(.caption)
                        .foregroundColor(.mutedMist)
                }

                Spacer()

                // Status pill
                StatusPill(status: ticket.status)
                    .matchedGeometryEffect(id: "status_\(ticket.id)", in: namespace)
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .background(
                RoundedRectangle(cornerRadius: 18)
                    .fill(Color.deepSurface)
                    .shadow(color: .black.opacity(0.30), radius: 10, y: 4)
                    .overlay(
                        RoundedRectangle(cornerRadius: 18)
                            .stroke(Color.warmAmber.opacity(isSelected ? 0 : 0.10), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
        .opacity(isSelected ? 0 : 1)
    }
}

// MARK: – Full ticket detail (expanded)

struct TicketDetailView: View {
    let ticket: OrderTicket
    let namespace: Namespace.ID
    let onDismiss: () -> Void
    var onSetStatus: ((KitchenOrderStatus) -> Void)? = nil

    @State private var itemsVisible = false

    var body: some View {
        ZStack(alignment: .topTrailing) {

            // Backdrop
            Color.oceanNight.opacity(0.92)
                .ignoresSafeArea()
                .onTapGesture(perform: onDismiss)

            VStack(alignment: .leading, spacing: 0) {

                // ── Header with matchedGeometryEffect ──────────────
                HStack(spacing: 16) {
                    ZStack {
                        Circle()
                            .fill(Color.warmAmber.opacity(0.14))
                            .matchedGeometryEffect(id: "badge_\(ticket.id)", in: namespace)

                        Text(ticket.tableLabel)
                            .font(.system(size: 17, weight: .black, design: .rounded))
                            .foregroundColor(.pearlWhite)
                            .lineLimit(1)
                            .minimumScaleFactor(0.5)
                            .padding(8)
                            .matchedGeometryEffect(id: "tableNum_\(ticket.id)", in: namespace)
                    }
                    .frame(width: 68, height: 68)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(ticket.tableLabel)
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(.pearlWhite)
                            .matchedGeometryEffect(id: "title_\(ticket.id)", in: namespace)

                        Text(ticket.createdAt.formatted(date: .omitted, time: .shortened))
                            .font(.caption)
                            .foregroundColor(.mutedMist)
                    }

                    Spacer()

                    StatusPill(status: ticket.status)
                        .matchedGeometryEffect(id: "status_\(ticket.id)", in: namespace)
                }
                .padding(.horizontal, 22)
                .padding(.top, 24)
                .padding(.bottom, 20)

                // Amber divider
                Rectangle()
                    .fill(
                        LinearGradient(
                            colors: [.clear, .warmAmber.opacity(0.25), .clear],
                            startPoint: .leading, endPoint: .trailing
                        )
                    )
                    .frame(height: 1)
                    .padding(.horizontal, 22)

                // ── Item list (staggered entrance) ─────────────────
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(Array(ticket.items.enumerated()), id: \.element.id) { idx, item in
                            OrderItemRow(item: item)
                                .offset(y: itemsVisible ? 0 : 20)
                                .opacity(itemsVisible ? 1 : 0)
                                .animation(
                                    .spring(response: 0.5, dampingFraction: 0.72)
                                        .delay(Double(idx) * 0.06),
                                    value: itemsVisible
                                )
                        }
                    }
                    .padding(.horizontal, 22)
                    .padding(.top, 18)
                    .padding(.bottom, 14)
                }

                // ── Status action buttons ───────────────────────────
                if let onSetStatus {
                    Rectangle()
                        .fill(LinearGradient(
                            colors: [.clear, .warmAmber.opacity(0.08), .clear],
                            startPoint: .leading, endPoint: .trailing
                        ))
                        .frame(height: 1)
                        .padding(.horizontal, 22)

                    HStack(spacing: 10) {
                        // Back button
                        switch ticket.status {
                        case .inProgress:
                            Button {
                                onSetStatus(.pending)
                                onDismiss()
                            } label: {
                                Text("← En attente")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.warmAmber)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .background(Color.warmAmber.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.warmAmber.opacity(0.4), lineWidth: 1))
                            }
                        case .ready:
                            Button {
                                onSetStatus(.preparing)
                                onDismiss()
                            } label: {
                                Text("← En prép.")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(.coralSunset)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 10)
                                    .background(Color.coralSunset.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.coralSunset.opacity(0.4), lineWidth: 1))
                            }
                        default:
                            EmptyView()
                        }

                        Spacer()

                        // Forward button
                        switch ticket.status {
                        case .pending:
                            Button {
                                onSetStatus(.preparing)
                                onDismiss()
                            } label: {
                                Label("Commencer", systemImage: "play.fill")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(red: 0.1, green: 0.04, blue: 0))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 10)
                                    .background(Color.warmAmber, in: RoundedRectangle(cornerRadius: 12))
                            }
                        case .inProgress:
                            Button {
                                onSetStatus(.ready)
                                onDismiss()
                            } label: {
                                Label("Prête", systemImage: "checkmark.circle.fill")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(red: 0, green: 0.12, blue: 0.11))
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 10)
                                    .background(Color.lagoonTeal, in: RoundedRectangle(cornerRadius: 12))
                            }
                        default:
                            EmptyView()
                        }
                    }
                    .padding(.horizontal, 22)
                    .padding(.top, 12)
                    .padding(.bottom, 24)
                }
            }
            .background(
                RoundedRectangle(cornerRadius: 28)
                    .fill(Color.deepSurface)
                    .shadow(color: .black.opacity(0.55), radius: 40, y: 16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 28)
                            .stroke(Color.warmAmber.opacity(0.12), lineWidth: 1)
                    )
            )
            .padding(.horizontal, 16)
            .padding(.vertical, 60)

            // Close button
            Button(action: onDismiss) {
                Image(systemName: "xmark.circle.fill")
                    .symbolRenderingMode(.hierarchical)
                    .foregroundColor(.mutedMist)
                    .font(.title2)
            }
            .padding(.top, 68)
            .padding(.trailing, 28)
        }
        .onAppear { itemsVisible = true }
        .onDisappear { itemsVisible = false }
    }
}

// MARK: – Order item row

private struct OrderItemRow: View {
    let item: OrderItem

    var body: some View {
        HStack(spacing: 14) {
            Text("×\(item.qty)")
                .font(.system(size: 13, weight: .black, design: .rounded))
                .foregroundColor(.warmAmber)
                .frame(width: 36, height: 36)
                .background(Color.warmAmber.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))

            VStack(alignment: .leading, spacing: 2) {
                Text(item.name)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.pearlWhite)

                if let note = item.note, !note.isEmpty {
                    Text(note)
                        .font(.caption)
                        .foregroundColor(.mutedMist)
                        .italic()
                }
            }
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Color.raisedSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color.warmAmber.opacity(0.07), lineWidth: 1)
                )
        )
    }
}

// MARK: – Status pill

private struct StatusPill: View {
    let status: OrderTicket.TicketStatus

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: status.icon)
                .font(.system(size: 10, weight: .bold))
            Text(status.rawValue)
                .font(.system(size: 11, weight: .bold))
        }
        .foregroundColor(status.color)
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(status.color.opacity(0.12), in: Capsule())
        .overlay(Capsule().stroke(status.color.opacity(0.25), lineWidth: 1))
    }
}

// MARK: – Item totals chip

private struct TotalChip: View {
    let name: String
    let quantity: Int

    var body: some View {
        HStack(spacing: 8) {
            Text(name)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.pearlWhite)

            Text("\(quantity)")
                .font(.system(size: 11, weight: .black, design: .rounded))
                .foregroundColor(Color(red: 0.1, green: 0.04, blue: 0))
                .padding(.horizontal, 7)
                .padding(.vertical, 3)
                .background(Color.warmAmber, in: Capsule())
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(Color.raisedSurface, in: RoundedRectangle(cornerRadius: 20))
        .overlay(
            RoundedRectangle(cornerRadius: 20)
                .stroke(Color.warmAmber.opacity(0.15), lineWidth: 1)
        )
    }
}

// MARK: – Main kitchen / bar ticket list

private let kitchenTabs: [(status: OrderTicket.TicketStatus, label: String)] = [
    (.pending,    "En attente"),
    (.inProgress, "En préparation"),
    (.ready,      "Prêtes"),
]

struct OrderTicketListView: View {
    let tickets:      [OrderTicket]
    let totals:       [(name: String, quantity: Int)]
    let pageTitle:    String
    var onSetStatus:  ((String, KitchenOrderStatus) -> Void)? = nil

    @Namespace private var hero
    @State private var selectedTicket: OrderTicket? = nil
    @State private var activeTab: OrderTicket.TicketStatus = .pending

    private var visibleTickets: [OrderTicket] {
        tickets.filter { $0.status == activeTab }
    }

    var body: some View {
        ZStack {
            Color.oceanNight.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 10) {

                    // ── Totals summary panel ──────────────────────────
                    if !totals.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("TOTAL EN COURS — \(tickets.count) ticket\(tickets.count > 1 ? "s" : "")")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(.mutedMist)
                                .kerning(1.2)
                                .padding(.horizontal, 16)

                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(totals, id: \.name) { total in
                                        TotalChip(name: total.name, quantity: total.quantity)
                                    }
                                }
                                .padding(.horizontal, 16)
                            }
                        }
                        .padding(.vertical, 14)
                        .background(Color.deepSurface)
                        .overlay(
                            Rectangle()
                                .fill(Color.warmAmber.opacity(0.08))
                                .frame(height: 1),
                            alignment: .bottom
                        )
                    }

                    // ── Status tabs ───────────────────────────────────
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(kitchenTabs, id: \.status) { tab in
                                let count = tickets.filter { $0.status == tab.status }.count
                                let isActive = activeTab == tab.status
                                HStack(spacing: 6) {
                                    Text(tab.label)
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(isActive ? Color(red: 0.1, green: 0.04, blue: 0) : .pearlWhite)
                                    if count > 0 {
                                        Text("\(count)")
                                            .font(.system(size: 11, weight: .black, design: .rounded))
                                            .foregroundColor(isActive ? Color(red: 0.1, green: 0.04, blue: 0).opacity(0.7) : .warmAmber)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(
                                                isActive
                                                    ? Color(red: 0.1, green: 0.04, blue: 0).opacity(0.2)
                                                    : Color.warmAmber.opacity(0.2),
                                                in: Capsule()
                                            )
                                    }
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 8)
                                .background(
                                    isActive ? Color.warmAmber : Color.raisedSurface,
                                    in: RoundedRectangle(cornerRadius: 20)
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 20)
                                        .stroke(isActive ? Color.warmAmber : Color.warmAmber.opacity(0.15), lineWidth: 1)
                                )
                                .onTapGesture { withAnimation(.easeInOut(duration: 0.2)) { activeTab = tab.status } }
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                    .padding(.top, 8)

                    // ── Ticket rows ───────────────────────────────────
                    if visibleTickets.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.lagoonTeal.opacity(0.4))
                            Text("Aucune commande « \(kitchenTabs.first(where: { $0.status == activeTab })?.label ?? "") »")
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundColor(.pearlWhite)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 48)
                        .padding(.horizontal, 32)
                    }

                    ForEach(visibleTickets) { ticket in
                        TicketRow(
                            ticket:     ticket,
                            namespace:  hero,
                            isSelected: selectedTicket?.id == ticket.id,
                            onTap: {
                                withAnimation(.spring(response: 0.45, dampingFraction: 0.72)) {
                                    selectedTicket = ticket
                                }
                            }
                        )
                        .padding(.horizontal, 16)
                    }
                }
                .padding(.top, 12)
                .padding(.bottom, 20)
            }

            // Full-screen detail overlay
            if let ticket = selectedTicket {
                TicketDetailView(
                    ticket:    ticket,
                    namespace: hero,
                    onDismiss: {
                        withAnimation(.spring(response: 0.45, dampingFraction: 0.72)) {
                            selectedTicket = nil
                        }
                    },
                    onSetStatus: onSetStatus.map { cb in { status in cb(ticket.id, status) } }
                )
                .zIndex(10)
                .transition(.asymmetric(
                    insertion:  .opacity.combined(with: .scale(scale: 0.92)),
                    removal:    .opacity.combined(with: .scale(scale: 0.95))
                ))
            }
        }
        .navigationTitle(pageTitle)
        .navigationBarTitleDisplayMode(.large)
        .preferredColorScheme(.dark)
    }
}

// MARK: – Top-level wrapper (used by MainTabView)

struct OrderTicketView: View {
    let store: FlamingoStore
    @EnvironmentObject private var auth: AuthService

    var body: some View {
        let role    = auth.currentRole
        let orders  = store.filteredKitchenOrders(for: role)
        let totals  = store.kitchenItemTotals(for: role)
        let title   = role == .cuisinier ? "Commandes Cuisine"
                    : role == .barman    ? "Commandes Bar"
                    : "Commandes Cuisine & Bar"
        let tickets = orders.map { toTicket($0) }

        NavigationStack {
            OrderTicketListView(
                tickets:     tickets,
                totals:      totals,
                pageTitle:   title,
                onSetStatus: { id, status in store.setOrderStatus(id: id, status: status) }
            )
        }
    }

    private func toTicket(_ order: KitchenOrder) -> OrderTicket {
        OrderTicket(
            id:         order.id,
            tableLabel: order.tableLabel,
            items: order.items.map { item in
                OrderItem(
                    name:   item.name,
                    qty:    item.quantity,
                    note:   item.note,
                    itemId: item.itemId
                )
            },
            status:    toTicketStatus(order.orderStatus),
            createdAt: order.resolvedCreatedAt
        )
    }

    private func toTicketStatus(_ s: KitchenOrderStatus) -> OrderTicket.TicketStatus {
        switch s {
        case .pending:              return .pending
        case .preparing, .inProgress: return .inProgress
        case .ready:                return .ready
        case .served:               return .served
        }
    }
}

// MARK: – Preview

#Preview {
    let sample = [
        OrderTicket(
            id: "preview-1", tableLabel: "Cabane 5",
            items: [
                OrderItem(name: "Mojito Royal",    qty: 2, note: "Sans sucre"),
                OrderItem(name: "Salade Niçoise",  qty: 1, note: nil),
                OrderItem(name: "Eau pétillante",  qty: 3, note: nil),
            ],
            status: .inProgress, createdAt: Date()
        ),
        OrderTicket(
            id: "preview-2", tableLabel: "Table 12",
            items: [
                OrderItem(name: "Burger du Chef", qty: 2, note: "Bien cuit"),
                OrderItem(name: "Frites maison",  qty: 2, note: nil),
            ],
            status: .ready, createdAt: Date()
        ),
    ]
    let totals: [(name: String, quantity: Int)] = [
        (name: "Mojito Royal", quantity: 2),
        (name: "Frites maison", quantity: 2),
        (name: "Salade Niçoise", quantity: 1),
        (name: "Eau pétillante", quantity: 3),
        (name: "Burger du Chef", quantity: 2),
    ]
    NavigationStack {
        OrderTicketListView(tickets: sample, totals: totals, pageTitle: "Commandes Cuisine & Bar")
    }
}
