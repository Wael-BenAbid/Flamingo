import SwiftUI

struct SettingsView: View {
    var body: some View {
        NavigationStack {
            Form {
                Section("Projet") {
                    LabeledContent("Version") { Text("iOS SwiftUI") }
                    LabeledContent("Nom") { Text("A G.Resto") }
                }

                Section("Notes") {
                    Text("Ce squelette iPhone reprend les modules principaux de l'app web et Android.")
                }
            }
            .navigationTitle("Paramètres")
        }
    }
}
