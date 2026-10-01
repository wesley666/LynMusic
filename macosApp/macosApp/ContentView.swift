import AppKit
import SwiftUI
import UniformTypeIdentifiers
import ComposeApp

@MainActor
final class MacPlaybackViewModel: ObservableObject {
    private let controller = MacosPlaybackHostKt.createMacPlaybackHostController()
    private var timer: Timer?
    private var transientSeekGeneration = 0
    private var showsTransientSeekMessage = false

    @Published var title: String = ""
    @Published var hasLoadedFile: Bool = false
    @Published var isPlaying: Bool = false
    @Published var positionMs: Double = 0
    @Published var durationMs: Double = 0
    @Published var canSeek: Bool = false
    @Published var volume: Double = 1.0
    @Published var errorMessage: String?

    init() {
        refresh()
        timer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: true) { [weak self] _ in
            Task { @MainActor in
                self?.refresh()
            }
        }
    }

    deinit {
        timer?.invalidate()
        controller.dispose()
    }

    var hasTransientSeekMessage: Bool { showsTransientSeekMessage }

    var durationText: String {
        Self.formatTime(durationMs)
    }

    var positionText: String {
        Self.formatTime(positionMs)
    }

    var seekUpperBound: Double {
        max(durationMs, 1)
    }

    func openLocalFile() {
        let panel = NSOpenPanel()
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = ["mp3", "m4a", "aac", "wav"]
            .compactMap { UTType(filenameExtension: $0) }
        if panel.runModal() == .OK, let url = panel.url {
            showsTransientSeekMessage = false
            controller.openLocalFile(path: url.path)
            refresh()
        }
    }

    func togglePlayback() {
        if isPlaying {
            controller.pause()
        } else {
            controller.play()
        }
        refresh()
    }

    func seek(to value: Double) {
        if !canSeek {
            showTransientSeekMessage()
            return
        }
        controller.seek(positionMs: Int64(value))
        refresh()
    }

    func setVolume(_ value: Double) {
        controller.setVolume(volume: Float(value))
        refresh()
    }

    func refresh() {
        let snapshot = controller.currentState()
        title = snapshot.title
        hasLoadedFile = snapshot.hasLoadedFile
        isPlaying = snapshot.isPlaying
        positionMs = Double(snapshot.positionMs)
        durationMs = Double(snapshot.durationMs)
        canSeek = snapshot.canSeek
        volume = Double(snapshot.volume)
        if let playbackError = snapshot.errorMessage, !playbackError.isEmpty {
            showsTransientSeekMessage = false
            errorMessage = playbackError
        } else {
            errorMessage = showsTransientSeekMessage ? MacUiLanguage.shared.text("mac_seek_unsupported") : nil
        }
    }

    private func showTransientSeekMessage() {
        transientSeekGeneration += 1
        let generation = transientSeekGeneration
        showsTransientSeekMessage = true
        refresh()
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard let self, self.transientSeekGeneration == generation else { return }
            self.showsTransientSeekMessage = false
            self.refresh()
        }
    }

    private static func formatTime(_ value: Double) -> String {
        let totalSeconds = max(Int(value / 1000), 0)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60
        return String(format: "%02d:%02d", minutes, seconds)
    }
}

struct ContentView: View {
    @StateObject private var viewModel = MacPlaybackViewModel()
    @ObservedObject private var language = MacUiLanguage.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack {
                Spacer()
                Picker(language.text("language_title"), selection: $language.selectedValue) {
                    Text(language.text("language_follow_system")).tag("system")
                    Text("简体中文").tag("zh-Hans")
                    Text("繁體中文").tag("zh-Hant")
                    Text("English").tag("en")
                }
                .pickerStyle(.menu)
                .fixedSize(horizontal: false, vertical: true)
            }
            HStack(spacing: 12) {
                Button(language.text("mac_open_file")) {
                    viewModel.openLocalFile()
                }
                Button(language.text(viewModel.isPlaying ? "pause" : "play")) {
                    viewModel.togglePlayback()
                }
                .disabled(!viewModel.hasLoadedFile)
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(viewModel.hasLoadedFile ? viewModel.title : language.text("mac_no_file"))
                    .font(.title2.weight(.semibold))
                    .lineLimit(1)
                if let errorMessage = viewModel.errorMessage, !errorMessage.isEmpty {
                    Text(viewModel.hasTransientSeekMessage ? language.text("mac_seek_unsupported") : errorMessage)
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            VStack(alignment: .leading, spacing: 8) {
                Slider(
                    value: Binding(
                        get: { min(viewModel.positionMs, viewModel.seekUpperBound) },
                        set: { viewModel.seek(to: $0) }
                    ),
                    in: 0...viewModel.seekUpperBound
                )
                .accessibilityLabel(language.text("position"))
                HStack {
                    Text(viewModel.positionText)
                    Spacer()
                    Text(viewModel.durationText)
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text(language.text("mac_volume"))
                    .font(.headline)
                Slider(
                    value: Binding(
                        get: { viewModel.volume },
                        set: { viewModel.setVolume($0) }
                    ),
                    in: 0...1
                )
                .accessibilityLabel(language.text("mac_volume"))
            }

            Spacer()
        }
        .environment(\.locale, Locale(identifier: language.effectiveValue))
        .onReceive(language.$effectiveValue) { _ in viewModel.refresh() }
        .onReceive(NotificationCenter.default.publisher(for: NSLocale.currentLocaleDidChangeNotification)) { _ in
            language.refreshSystemLanguage()
            viewModel.refresh()
        }
        .padding(24)
        .frame(minWidth: 520, minHeight: 320)
    }
}
