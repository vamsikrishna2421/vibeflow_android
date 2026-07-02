import Foundation

/// Deterministic, fully-offline text curation — the Swift port of the Android
/// `:core` module (`TextCuration` + `Vocabulary` + `Snippets` + `Pipeline`).
/// Kept identical in behaviour so VibeFlow formats the same on both platforms.
public enum TextPipeline {

    public struct Options {
        public var spokenCommands = true
        public var spokenPunctuation = true
        public var capitalizeSentences = true
        public var capitalizeFirst = true
        public var fixPronounI = true
        public var stripFillers = false
        public var autoPeriod = true
        public var vocabulary: [String] = []
        public var snippets: [String: String] = [:]
        public init() {}
    }

    public static let defaultFillers = ["um", "uh", "umm", "uhh", "uhm", "mm", "hmm", "er", "ah"]

    public static let spokenPunctuation: [(String, String)] = [
        ("exclamation point", "!"), ("exclamation mark", "!"), ("question mark", "?"),
        ("open parenthesis", "("), ("close parenthesis", ")"), ("open paren", "("), ("close paren", ")"),
        ("ellipsis", "…"), ("semicolon", ";"), ("colon", ":"), ("comma", ","),
        ("period", "."), ("full stop", "."), ("hyphen", "-"), ("dash", " — "),
        ("ampersand", "&"), ("asterisk", "*"), ("percent sign", "%"), ("dollar sign", "$"), ("at sign", "@"),
    ]

    /// Full pipeline: vocabulary → snippets → curation.
    public static func process(_ raw: String, options: Options) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "" }
        var text = trimmed
        text = applyVocabulary(text, options.vocabulary)
        text = expandSnippets(text, options.snippets)
        text = curate(text, options)
        return text
    }

    // MARK: - Vocabulary

    static func applyVocabulary(_ text: String, _ terms: [String]) -> String {
        guard !text.isEmpty, !terms.isEmpty else { return text }
        var out = text
        for term in terms.map({ $0.trimmingCharacters(in: .whitespaces) })
            .filter({ !$0.isEmpty }).sorted(by: { $0.count > $1.count }) {
            out = replaceWord(out, word: term, with: term)
        }
        return out
    }

    // MARK: - Snippets

    static func expandSnippets(_ text: String, _ snippets: [String: String]) -> String {
        guard !text.isEmpty, !snippets.isEmpty else { return text }
        var out = text
        for (trigger, expansion) in snippets.sorted(by: { $0.key.count > $1.key.count }) {
            let t = trigger.trimmingCharacters(in: .whitespaces)
            if t.isEmpty { continue }
            out = replaceWord(out, word: t, with: expansion)
        }
        return out
    }

    // MARK: - Curation

    static func curate(_ text: String, _ o: Options) -> String {
        if text.isEmpty { return "" }
        var out = text
        if o.stripFillers { out = removeFillers(out, defaultFillers) }
        if o.spokenCommands {
            out = regexReplace(out, #"(?i)\bnew\s+paragraph\b"#, "\n\n")
            out = regexReplace(out, #"(?i)\bnew\s+line\b|\bnext\s+line\b"#, "\n")
        }
        if o.spokenPunctuation {
            for (phrase, mark) in spokenPunctuation.sorted(by: { $0.0.count > $1.0.count }) {
                out = replaceWord(out, word: phrase, with: mark)
            }
        }
        out = regexReplace(out, #"[ \t]+([,.!?;:…])"#, "$1")
        out = regexReplace(out, #"(?<![A-Za-z0-9])([,.!?;:])(?=[A-Za-z])"#, "$1 ")
        out = regexReplace(out, #"[ \t]{2,}"#, " ")
        out = regexReplace(out, #"[ \t]*\n[ \t]*"#, "\n")
        if o.fixPronounI {
            out = regexReplace(out, #"(?i)\bi('(?:m|ve|ll|d|s|re))\b"#, "I$1")
            out = regexReplace(out, #"\bi\b"#, "I")
        }
        if o.autoPeriod { out = addTrailingPeriod(out) }
        if o.capitalizeFirst { out = capitalizeFirstAlpha(out) }
        if o.capitalizeSentences { out = capitalizeAfterSentences(out) }
        return out.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func removeFillers(_ text: String, _ fillers: [String]) -> String {
        let alt = fillers.sorted(by: { $0.count > $1.count }).map { NSRegularExpression.escapedPattern(for: $0) }.joined(separator: "|")
        var normalized = text.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n")
        let onlyLine = "(?i)^[ \\t,]*(?:\(alt))(?:[ \\t,]+(?:\(alt)))*[ \\t,]*$"
        let lines = normalized.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        var out: [String] = []
        for line in lines {
            let stripped = line.trimmingCharacters(in: .whitespaces)
            if !stripped.isEmpty, matchesFull(stripped, onlyLine) { continue }
            var l = regexReplace(line, "(?i)^[ \\t]*(?:\(alt))(?:[ \\t]*,)?(?=[ \\t]|$)", "")
            var prev: String? = nil
            while prev != l { prev = l; l = regexReplace(l, "(?i)[ \\t]+(?:\(alt))(?=[ \\t])", "") }
            l = regexReplace(l, "(?i)[ \\t]*,?[ \\t]+(?:\(alt))[ \\t]*$", "")
            out.append(regexReplace(l, #"[ \t]{2,}"#, " ").trimmingCharacters(in: CharacterSet(charactersIn: " \t")))
        }
        normalized = out.joined(separator: "\n")
        return normalized
    }

    // MARK: - helpers

    static func addTrailingPeriod(_ text: String) -> String {
        let t = text.trimmingCharacters(in: CharacterSet(charactersIn: " \t\n"))
        guard let last = t.last else { return text }
        return last.isLetter || last.isNumber ? t + "." : text
    }

    static func capitalizeFirstAlpha(_ text: String) -> String {
        return regexReplaceFunc(text, #"^(\s*)([a-z])(?=[a-z]*\b)"#) { groups in
            groups[1] + groups[2].uppercased()
        }
    }

    static func capitalizeAfterSentences(_ text: String) -> String {
        var out = regexReplaceFunc(text, #"([.!?]\s+)([a-z])(?=[a-z]*\b)"#) { g in g[1] + g[2].uppercased() }
        out = regexReplaceFunc(out, #"(\n[ \t]*)([a-z])(?=[a-z]*\b)"#) { g in g[1] + g[2].uppercased() }
        return out
    }

    /// Whole-word, case-insensitive replacement (literal replacement string).
    static func replaceWord(_ text: String, word: String, with replacement: String) -> String {
        let pattern = "(?i)\\b" + NSRegularExpression.escapedPattern(for: word) + "\\b"
        guard let re = try? NSRegularExpression(pattern: pattern) else { return text }
        let range = NSRange(text.startIndex..., in: text)
        let escaped = NSRegularExpression.escapedTemplate(for: replacement)
        return re.stringByReplacingMatches(in: text, range: range, withTemplate: escaped)
    }

    static func regexReplace(_ text: String, _ pattern: String, _ template: String) -> String {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return text }
        let range = NSRange(text.startIndex..., in: text)
        return re.stringByReplacingMatches(in: text, range: range, withTemplate: template)
    }

    static func matchesFull(_ text: String, _ pattern: String) -> Bool {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return false }
        let range = NSRange(text.startIndex..., in: text)
        if let m = re.firstMatch(in: text, range: range) { return m.range == range }
        return false
    }

    static func regexReplaceFunc(_ text: String, _ pattern: String, _ transform: ([String]) -> String) -> String {
        guard let re = try? NSRegularExpression(pattern: pattern) else { return text }
        let ns = text as NSString
        var result = ""
        var last = 0
        for m in re.matches(in: text, range: NSRange(location: 0, length: ns.length)) {
            result += ns.substring(with: NSRange(location: last, length: m.range.location - last))
            var groups: [String] = []
            for i in 0..<m.numberOfRanges {
                let r = m.range(at: i)
                groups.append(r.location == NSNotFound ? "" : ns.substring(with: r))
            }
            result += transform(groups)
            last = m.range.location + m.range.length
        }
        result += ns.substring(from: last)
        return result
    }
}
