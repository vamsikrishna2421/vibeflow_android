import Foundation

/// One dictation, shared between the app and the keyboard extension.
public struct Dictation: Codable, Identifiable, Equatable {
    public let id: Double          // epoch seconds
    public var text: String
    public var pinned: Bool
    public init(id: Double, text: String, pinned: Bool = false) {
        self.id = id; self.text = text; self.pinned = pinned
    }
}

/// App-Group-backed store so the **app** (which records) and the **keyboard**
/// (which pastes) share the latest dictation and the history. iOS keyboard
/// extensions can't use the microphone, so this hand-off is how voice text
/// reaches arbitrary apps on iOS.
public final class SharedStore {
    public static let appGroup = "group.com.vibeflow.mobile"
    public static let shared = SharedStore()

    private let defaults: UserDefaults
    private let latestKey = "latest_dictation"
    private let historyKey = "history_json"
    private let maxEntries = 1000

    public init() {
        defaults = UserDefaults(suiteName: SharedStore.appGroup) ?? .standard
    }

    public var latest: String {
        get { defaults.string(forKey: latestKey) ?? "" }
        set { defaults.set(newValue, forKey: latestKey) }
    }

    public func history() -> [Dictation] {
        guard let data = defaults.data(forKey: historyKey),
              let list = try? JSONDecoder().decode([Dictation].self, from: data) else { return [] }
        return list
    }

    @discardableResult
    public func add(_ text: String) -> Dictation {
        let clean = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let entry = Dictation(id: Date().timeIntervalSince1970, text: clean)
        var list = history()
        list.append(entry)
        list = trim(list)
        save(list)
        latest = clean
        return entry
    }

    public func delete(_ id: Double) { save(history().filter { $0.id != id }) }

    public func togglePin(_ id: Double) {
        save(history().map { $0.id == id ? Dictation(id: $0.id, text: $0.text, pinned: !$0.pinned) : $0 })
    }

    public func clearUnpinned() { save(history().filter { $0.pinned }) }

    private func trim(_ list: [Dictation]) -> [Dictation] {
        guard list.count > maxEntries else { return list }
        let pinned = list.filter { $0.pinned }
        let unpinned = list.filter { !$0.pinned }
        let keep = max(0, maxEntries - pinned.count)
        return (pinned + unpinned.suffix(keep)).sorted { $0.id < $1.id }
    }

    private func save(_ list: [Dictation]) {
        if let data = try? JSONEncoder().encode(list) { defaults.set(data, forKey: historyKey) }
    }
}
