import UIKit

/// The VibeFlow keyboard extension. iOS forbids microphone access here, so the
/// keyboard's job is to **insert text the app already captured** by voice:
/// a one-tap "Paste latest", plus a list of recent dictations to drop in. The
/// app records → shares via the App Group → this keyboard inserts into any field.
final class KeyboardViewController: UIInputViewController {

    private let brand = UIColor(red: 0.357, green: 0.329, blue: 0.902, alpha: 1)
    private let store = SharedStore()
    private var stack: UIStackView!

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.10, green: 0.09, blue: 0.15, alpha: 1)
        buildUI()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        reload()
    }

    private func buildUI() {
        let header = label("VibeFlow — insert a dictation", size: 13, color: .lightGray)

        let pasteLatest = filledButton("⤵  Paste latest dictation")
        pasteLatest.addTarget(self, action: #selector(pasteLatestTapped), for: .touchUpInside)

        let recentTitle = label("Recent", size: 12, color: .lightGray)

        let scroll = UIScrollView()
        scroll.translatesAutoresizingMaskIntoConstraints = false
        stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 6
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)

        let nextKb = plainButton("🌐  Switch keyboard")
        nextKb.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)

        let root = UIStackView(arrangedSubviews: [header, pasteLatest, recentTitle, scroll, nextKb])
        root.axis = .vertical
        root.spacing = 10
        root.translatesAutoresizingMaskIntoConstraints = false
        root.isLayoutMarginsRelativeArrangement = true
        root.layoutMargins = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
        view.addSubview(root)

        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            root.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            root.topAnchor.constraint(equalTo: view.topAnchor),
            root.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stack.leadingAnchor.constraint(equalTo: scroll.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: scroll.trailingAnchor),
            stack.topAnchor.constraint(equalTo: scroll.topAnchor),
            stack.bottomAnchor.constraint(equalTo: scroll.bottomAnchor),
            stack.widthAnchor.constraint(equalTo: scroll.widthAnchor),
            scroll.heightAnchor.constraint(greaterThanOrEqualToConstant: 90),
        ])
        view.heightAnchor.constraint(equalToConstant: 260).isActive = true
    }

    private func reload() {
        stack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let recent = store.history().sorted { ($0.pinned ? 1 : 0, $0.id) > ($1.pinned ? 1 : 0, $1.id) }.prefix(8)
        if recent.isEmpty {
            stack.addArrangedSubview(label("Dictate in the VibeFlow app first.", size: 13, color: .gray))
            return
        }
        for d in recent {
            let b = chip(d.text)
            b.addAction(UIAction { [weak self] _ in self?.textDocumentProxy.insertText(d.text) }, for: .touchUpInside)
            stack.addArrangedSubview(b)
        }
    }

    @objc private func pasteLatestTapped() {
        let latest = store.latest
        if !latest.isEmpty { textDocumentProxy.insertText(latest) }
    }

    // MARK: - view helpers

    private func label(_ text: String, size: CGFloat, color: UIColor) -> UILabel {
        let l = UILabel(); l.text = text; l.font = .systemFont(ofSize: size); l.textColor = color; return l
    }

    private func filledButton(_ title: String) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.setTitleColor(.white, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 17, weight: .semibold)
        b.backgroundColor = brand
        b.layer.cornerRadius = 14
        b.heightAnchor.constraint(equalToConstant: 52).isActive = true
        return b
    }

    private func plainButton(_ title: String) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title, for: .normal)
        b.setTitleColor(.white, for: .normal)
        b.backgroundColor = UIColor(white: 0.16, alpha: 1)
        b.layer.cornerRadius = 12
        b.heightAnchor.constraint(equalToConstant: 44).isActive = true
        return b
    }

    private func chip(_ text: String) -> UIButton {
        let b = UIButton(type: .system)
        let oneLine = text.replacingOccurrences(of: "\n", with: " ")
        b.setTitle(String(oneLine.prefix(60)), for: .normal)
        b.contentHorizontalAlignment = .left
        b.setTitleColor(UIColor(white: 0.95, alpha: 1), for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 15)
        b.backgroundColor = UIColor(white: 0.16, alpha: 1)
        b.layer.cornerRadius = 10
        b.contentEdgeInsets = UIEdgeInsets(top: 10, left: 12, bottom: 10, right: 12)
        return b
    }
}
