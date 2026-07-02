import Foundation
import AVFoundation
import Speech

/// On-device streaming speech-to-text using Apple's `SFSpeechRecognizer`.
/// `requiresOnDeviceRecognition = true` keeps everything offline — no audio
/// leaves the phone. This is the iOS counterpart to the Android Vosk engine.
@MainActor
public final class SpeechManager: ObservableObject {

    public enum State: Equatable { case idle, listening, error(String) }

    @Published public private(set) var state: State = .idle
    @Published public private(set) var partial: String = ""

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    public init() {}

    public func requestPermissions() async -> Bool {
        let speech = await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            SFSpeechRecognizer.requestAuthorization { c.resume(returning: $0 == .authorized) }
        }
        let mic = await withCheckedContinuation { (c: CheckedContinuation<Bool, Never>) in
            AVAudioApplication.requestRecordPermission { c.resume(returning: $0) }
        }
        return speech && mic
    }

    public func start() {
        guard state != .listening else { return }
        partial = ""
        guard let recognizer, recognizer.isAvailable else {
            state = .error("Speech recognizer unavailable"); return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.record, mode: .measurement, options: .duckOthers)
            try session.setActive(true, options: .notifyOthersOnDeactivation)

            let req = SFSpeechAudioBufferRecognitionRequest()
            req.shouldReportPartialResults = true
            req.requiresOnDeviceRecognition = true   // fully offline
            request = req

            let input = audioEngine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { [weak self] buffer, _ in
                self?.request?.append(buffer)
            }
            audioEngine.prepare()
            try audioEngine.start()
            state = .listening

            task = recognizer.recognitionTask(with: req) { [weak self] result, error in
                guard let self else { return }
                if let result { self.partial = result.bestTranscription.formattedString }
                if error != nil || (result?.isFinal ?? false) {
                    self.finishAudio()
                }
            }
        } catch {
            state = .error(error.localizedDescription)
            finishAudio()
        }
    }

    /// Stop and return the final transcript via the completion.
    public func stop(_ completion: @escaping (String) -> Void) {
        let text = partial
        finishAudio()
        completion(text)
    }

    private func finishAudio() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        task?.cancel()
        request = nil
        task = nil
        if state == .listening { state = .idle }
    }
}
