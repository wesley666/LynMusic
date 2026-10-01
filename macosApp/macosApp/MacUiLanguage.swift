import Foundation
import SwiftUI
import ComposeApp

@MainActor
final class MacUiLanguage: ObservableObject {
    static let shared = MacUiLanguage()
    private static let preferenceKey = "app_language"
    @Published var selectedValue: String {
        didSet {
            UserDefaults.standard.set(selectedValue, forKey: Self.preferenceKey)
            refreshSystemLanguage()
        }
    }
    @Published private(set) var effectiveValue = "en"

    private init() {
        let stored = UserDefaults.standard.string(forKey: Self.preferenceKey) ?? "system"
        selectedValue = ["system", "zh-Hans", "zh-Hant", "en"].contains(stored) ? stored : "system"
        refreshSystemLanguage()
    }

    func refreshSystemLanguage() {
        effectiveValue = MacosPlaybackHostKt.configureMacUiLanguage(
            savedValue: selectedValue,
            systemLanguageTag: Locale.preferredLanguages.first ?? "en"
        )
    }

    func text(_ key: String) -> String {
        guard let path = Bundle.main.path(forResource: effectiveValue, ofType: "lproj"),
              let bundle = Bundle(path: path) else { return key }
        return bundle.localizedString(forKey: key, value: nil, table: nil)
    }
}
