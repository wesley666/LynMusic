import AppKit
import SwiftUI
import UniformTypeIdentifiers
import ComposeApp

@MainActor
final class MacPlaybackViewModel: ObservableObject {
    private static let unsupportedSeekMessage = "歌曲可能正在转码，不支持快进。"
    private let controller = MacosPlaybackHostKt.createMacPlaybackHostController()
    private var timer: Timer?
    private var transientSeekMessage: String?

    @Published var title: String = "未选择文件"
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
            transientSeekMessage = nil
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
        title = snapshot.title.isEmpty ? "未选择文件" : snapshot.title
        isPlaying = snapshot.isPlaying
        positionMs = Double(snapshot.positionMs)
        durationMs = Double(snapshot.durationMs)
        canSeek = snapshot.canSeek
        volume = Double(snapshot.volume)
        if let playbackError = snapshot.errorMessage, !playbackError.isEmpty {
            transientSeekMessage = nil
            errorMessage = playbackError
        } else {
            errorMessage = transientSeekMessage
        }
    }

    private func showTransientSeekMessage() {
        transientSeekMessage = Self.unsupportedSeekMessage
        errorMessage = Self.unsupportedSeekMessage
        Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            await MainActor.run {
                guard self?.transientSeekMessage == Self.unsupportedSeekMessage else { return }
                self?.transientSeekMessage = nil
                self?.refresh()
            }
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

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            HStack(spacing: 12) {
                Button("打开本地文件") {
                    viewModel.openLocalFile()
                }
                Button(viewModel.isPlaying ? "暂停" : "播放") {
                    viewModel.togglePlayback()
                }
                .disabled(viewModel.title == "未选择文件")
            }

            VStack(alignment: .leading, spacing: 6) {
                Text(viewModel.title)
                    .font(.title2.weight(.semibold))
                    .lineLimit(1)
                if let errorMessage = viewModel.errorMessage, !errorMessage.isEmpty {
                    Text(errorMessage)
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
                HStack {
                    Text(viewModel.positionText)
                    Spacer()
                    Text(viewModel.durationText)
                }
                .font(.caption.monospacedDigit())
                .foregroundStyle(.secondary)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("音量")
                    .font(.headline)
                Slider(
                    value: Binding(
                        get: { viewModel.volume },
                        set: { viewModel.setVolume($0) }
                    ),
                    in: 0...1
                )
            }

            Spacer()
        }
        .padding(24)
        .frame(minWidth: 520, minHeight: 320)
    }
}
