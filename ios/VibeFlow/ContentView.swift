import SwiftUI
import UIKit

private let brand = Color(red: 0.357, green: 0.329, blue: 0.902)
private let accentRed = Color(red: 0.898, green: 0.282, blue: 0.302)

struct ContentView: View {
    var body: some View {
        TabView {
            TalkScreen().tabItem { Label("Talk", systemImage: "mic.fill") }
            HistoryScreen().tabItem { Label("History", systemImage: "clock.fill") }
            AboutScreen().tabItem { Label("About", systemImage: "info.circle.fill") }
        }
    }
}

// MARK: - Talk

struct TalkScreen: View {
    @StateObject private var speech = SpeechManager()
    @State private var result: String = ""
    @State private var authorized = false

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    Text("Talk. It types — fully offline.")
                        .font(.subheadline).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    card {
                        VStack(spacing: 16) {
                            switch speech.state {
                            case .listening:
                                Text(speech.partial.isEmpty ? "Listening… speak now" : speech.partial)
                                    .font(.title3).multilineTextAlignment(.center)
                                micButton(listening: true) { stop() }
                                Text("Tap to stop").font(.footnote).foregroundStyle(.secondary)
                            case .error(let msg):
                                Text(msg).foregroundStyle(.red).multilineTextAlignment(.center)
                                micButton(listening: false) { start() }
                            case .idle:
                                if result.isEmpty {
                                    Text("Tap, speak, and it's copied to your clipboard.")
                                        .font(.title3).multilineTextAlignment(.center)
                                    micButton(listening: false) { start() }
                                    Text(authorized ? "Tap to talk" : "Tap to grant the mic & talk")
                                        .font(.footnote).foregroundStyle(.secondary)
                                } else {
                                    doneBlock
                                }
                            }
                        }.padding(8)
                    }

                    tips
                }
                .padding()
            }
            .navigationTitle("VibeFlow")
            .task { authorized = await speech.requestPermissions() }
        }
    }

    private var doneBlock: some View {
        VStack(spacing: 12) {
            Label("Copied to clipboard", systemImage: "checkmark.circle.fill").foregroundStyle(brand)
            Text(result).padding().frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(.secondarySystemBackground)).clipShape(RoundedRectangle(cornerRadius: 14))
            HStack {
                Button { UIPasteboard.general.string = result } label: { Label("Copy", systemImage: "doc.on.doc") }
                    .buttonStyle(.bordered)
                Button { result = ""; start() } label: { Label("Again", systemImage: "mic.fill") }
                    .buttonStyle(.borderedProminent)
            }
            Text("Switch to the VibeFlow keyboard in any app and paste.")
                .font(.caption).foregroundStyle(.secondary)
        }
    }

    private var tips: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("How to type anywhere").font(.headline)
            tip("1", "Open any app and tap a text box.")
            tip("2", "Switch to the VibeFlow keyboard (🌐 globe key).")
            tip("3", "Tap “Paste latest” to drop in what you just dictated.")
            Text("Say “comma”, “period”, “question mark”, or “new line” to punctuate.")
                .font(.caption).foregroundStyle(.secondary).padding(.top, 4)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }

    private func start() {
        Task {
            if !authorized { authorized = await speech.requestPermissions() }
            if authorized { speech.start() }
        }
    }

    private func stop() {
        speech.stop { raw in
            var opts = TextPipeline.Options()
            opts.vocabulary = []
            let text = TextPipeline.process(raw, options: opts)
            guard !text.isEmpty else { result = ""; return }
            UIPasteboard.general.string = text
            SharedStore.shared.add(text)
            result = text
        }
    }

    private func micButton(listening: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: listening ? "stop.fill" : "mic.fill")
                .font(.system(size: 40)).foregroundStyle(.white)
                .frame(width: 92, height: 92)
                .background(listening ? accentRed : brand).clipShape(Circle())
        }.buttonStyle(.plain)
    }

    private func card<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        content().frame(maxWidth: .infinity).padding()
            .background(Color(.secondarySystemBackground)).clipShape(RoundedRectangle(cornerRadius: 26))
    }

    private func tip(_ n: String, _ t: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Text(n).font(.caption).foregroundStyle(.white).frame(width: 24, height: 24).background(brand).clipShape(Circle())
            Text(t)
        }
    }
}

// MARK: - History

struct HistoryScreen: View {
    @State private var entries: [Dictation] = []
    @State private var query = ""

    var body: some View {
        NavigationStack {
            List {
                ForEach(filtered) { e in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(e.text)
                        HStack {
                            Button { UIPasteboard.general.string = e.text } label: { Image(systemName: "doc.on.doc") }
                            Button { SharedStore.shared.togglePin(e.id); reload() } label: {
                                Image(systemName: e.pinned ? "pin.fill" : "pin")
                            }
                            Spacer()
                            Button(role: .destructive) { SharedStore.shared.delete(e.id); reload() } label: {
                                Image(systemName: "trash")
                            }
                        }.buttonStyle(.borderless).foregroundStyle(brand)
                    }
                }
            }
            .searchable(text: $query, prompt: "Search your dictations")
            .navigationTitle("History")
            .toolbar { Button("Clear") { SharedStore.shared.clearUnpinned(); reload() } }
            .onAppear(perform: reload)
        }
    }

    private var filtered: [Dictation] {
        let sorted = entries.sorted { ($0.pinned ? 1 : 0, $0.id) > ($1.pinned ? 1 : 0, $1.id) }
        guard !query.isEmpty else { return sorted }
        return sorted.filter { $0.text.localizedCaseInsensitiveContains(query) }
    }

    private func reload() { entries = SharedStore.shared.history() }
}

// MARK: - About

struct AboutScreen: View {
    var body: some View {
        NavigationStack {
            List {
                Section("Privacy") {
                    Text("100% offline. Speech is recognised on-device with Apple's on-device recognizer. Your voice never leaves this phone.")
                }
                Section("Setup") {
                    Text("Enable the VibeFlow keyboard: Settings → General → Keyboard → Keyboards → Add New Keyboard → VibeFlow.")
                }
                Section { Text("VibeFlow Mobile 0.1.0 (iOS)") }
            }.navigationTitle("About")
        }
    }
}
