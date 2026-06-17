import SwiftUI

// MARK: – El Bounta Design Tokens (iOS)
extension Color {
    static let oceanNight     = Color(hex: "#0B1628")
    static let deepSurface    = Color(hex: "#13203A")
    static let raisedSurface  = Color(hex: "#1C2E4A")
    static let warmAmber      = Color(hex: "#F59B35")
    static let coralSunset    = Color(hex: "#E8603A")
    static let lagoonTeal     = Color(hex: "#2EC4B6")
    static let pearlWhite     = Color(hex: "#F0EEE8")
    static let mutedMist      = Color(hex: "#9DB4C0")
    static let crimsonAlert   = Color(hex: "#E63946")
}

extension Color {
    init(hex: String) {
        let h = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int = UInt64()
        Scanner(string: h).scanHexInt64(&int)
        let r = Double((int >> 16) & 0xFF) / 255
        let g = Double((int >> 8)  & 0xFF) / 255
        let b = Double(int         & 0xFF) / 255
        self.init(red: r, green: g, blue: b)
    }
}

// MARK: – Login View

struct LoginView: View {
    @EnvironmentObject private var auth: AuthService

    @State private var email    = ""
    @State private var password = ""
    @State private var showPassword = false

    // Entrance animations
    @State private var logoVisible   = false
    @State private var formVisible   = false
    @State private var buttonPressed = false

    // Role-based routing after sign-in
    @State private var navigateToDashboard = false
    @State private var navigateToKitchen   = false

    var body: some View {
        ZStack {
            // Deep marine gradient background
            LinearGradient(
                colors: [Color.oceanNight, Color(hex: "#0F1E38"), Color(hex: "#0B1628")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            // Ambient sunset glow (top-right)
            RadialGradient(
                colors: [Color.warmAmber.opacity(0.12), .clear],
                center: .topTrailing,
                startRadius: 0,
                endRadius: 350
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 0) {

                    // ── Logo block ─────────────────────────────────────────
                    VStack(spacing: 12) {
                        ZStack {
                            Circle()
                                .fill(Color.warmAmber.opacity(0.12))
                                .frame(width: 100, height: 100)
                                .blur(radius: 20)

                            Image(systemName: "water.waves")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 52, height: 52)
                                .foregroundStyle(
                                    LinearGradient(
                                        colors: [.warmAmber, .coralSunset],
                                        startPoint: .topLeading, endPoint: .bottomTrailing
                                    )
                                )
                        }

                        Text("OCEAN")
                            .font(.system(size: 32, weight: .black, design: .rounded))
                            .foregroundColor(.pearlWhite)
                            .tracking(8)

                        Text("El Bounta · Lounge & Restaurant")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.mutedMist)
                            .tracking(2)
                    }
                    .padding(.top, 72)
                    .scaleEffect(logoVisible ? 1 : 0.7)
                    .opacity(logoVisible ? 1 : 0)
                    .animation(.spring(response: 0.6, dampingFraction: 0.7), value: logoVisible)

                    // ── Glass card ─────────────────────────────────────────
                    VStack(spacing: 20) {

                        // Email field
                        OceanInputField(
                            icon: "envelope.fill",
                            placeholder: "Adresse e-mail",
                            text: $email,
                            isSecure: false
                        )

                        // Password field
                        OceanInputField(
                            icon: "lock.fill",
                            placeholder: "Mot de passe",
                            text: $password,
                            isSecure: !showPassword,
                            trailingIcon: showPassword ? "eye.slash.fill" : "eye.fill",
                            trailingAction: { showPassword.toggle() }
                        )

                        // Error message
                        if let err = auth.errorMessage {
                            HStack(spacing: 6) {
                                Image(systemName: "exclamationmark.circle.fill")
                                Text(err)
                                    .font(.caption)
                            }
                            .foregroundColor(.crimsonAlert)
                            .padding(.horizontal, 4)
                            .transition(.move(edge: .top).combined(with: .opacity))
                        }

                        // Sign-in button
                        Button(action: signIn) {
                            HStack(spacing: 10) {
                                if auth.isLoading {
                                    ProgressView()
                                        .tint(.black)
                                        .scaleEffect(0.9)
                                } else {
                                    Image(systemName: "arrow.right.circle.fill")
                                    Text("Se connecter")
                                        .fontWeight(.bold)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(
                                LinearGradient(
                                    colors: [.warmAmber, .coralSunset],
                                    startPoint: .leading, endPoint: .trailing
                                )
                            )
                            .foregroundColor(.black.opacity(0.85))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .shadow(color: .warmAmber.opacity(0.35), radius: 14, y: 6)
                            .scaleEffect(buttonPressed ? 0.96 : 1)
                        }
                        .disabled(auth.isLoading || email.isEmpty || password.isEmpty)
                        .animation(.spring(response: 0.25, dampingFraction: 0.6), value: buttonPressed)
                        ._onButtonGesture(pressing: { buttonPressed = $0 }, perform: {})
                    }
                    .padding(28)
                    .background(
                        // Glassmorphism card
                        RoundedRectangle(cornerRadius: 24)
                            .fill(Color.deepSurface.opacity(0.82))
                            .overlay(
                                RoundedRectangle(cornerRadius: 24)
                                    .stroke(Color.warmAmber.opacity(0.12), lineWidth: 1)
                            )
                    )
                    .shadow(color: .black.opacity(0.4), radius: 30, y: 12)
                    .padding(.horizontal, 24)
                    .padding(.top, 48)
                    .offset(y: formVisible ? 0 : 40)
                    .opacity(formVisible ? 1 : 0)
                    .animation(
                        .spring(response: 0.65, dampingFraction: 0.75).delay(0.25),
                        value: formVisible
                    )

                    Spacer(minLength: 48)
                }
            }
        }
        .preferredColorScheme(.dark)
        .navigationDestination(isPresented: $navigateToDashboard) {
            MainTabView()
        }
        .navigationDestination(isPresented: $navigateToKitchen) {
            // KitchenView() — add once you build the kitchen screen
            Text("Kitchen Dashboard — TODO")
                .foregroundColor(.pearlWhite)
        }
        .onAppear {
            logoVisible = true
            formVisible = true
        }
        .onChange(of: auth.currentRole) { _, role in
            guard role != .none, !auth.isLoading else { return }
            // Direct kitchen staff straight to their dedicated screen.
            if role == .cuisine {
                navigateToKitchen = true
            } else {
                navigateToDashboard = true
            }
        }
    }

    private func signIn() {
        Task { await auth.signIn(email: email, password: password) }
    }
}

// MARK: – Reusable input field

struct OceanInputField: View {
    let icon: String
    let placeholder: String
    @Binding var text: String
    var isSecure: Bool = false
    var trailingIcon: String? = nil
    var trailingAction: (() -> Void)? = nil

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(.warmAmber)
                .frame(width: 20)

            Group {
                if isSecure {
                    SecureField(placeholder, text: $text)
                } else {
                    TextField(placeholder, text: $text)
                        .keyboardType(icon == "envelope.fill" ? .emailAddress : .default)
                        .autocapitalization(.none)
                }
            }
            .foregroundColor(.pearlWhite)
            .tint(.warmAmber)

            if let trailing = trailingIcon {
                Button(action: { trailingAction?() }) {
                    Image(systemName: trailing)
                        .foregroundColor(.mutedMist)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 12)
                .fill(Color.raisedSurface)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color.warmAmber.opacity(text.isEmpty ? 0.08 : 0.28), lineWidth: 1)
                )
        )
        .animation(.easeInOut(duration: 0.2), value: text.isEmpty)
    }
}

// MARK: – App entry with auth routing

struct RootView: View {
    @EnvironmentObject private var auth: AuthService

    var body: some View {
        NavigationStack {
            if auth.currentUser == nil {
                LoginView()
            } else {
                switch auth.currentRole {
                case .cuisine:
                    Text("Kitchen Dashboard")  // Replace with KitchenView()
                        .foregroundColor(.pearlWhite)
                default:
                    MainTabView()
                }
            }
        }
        .preferredColorScheme(.dark)
    }
}
