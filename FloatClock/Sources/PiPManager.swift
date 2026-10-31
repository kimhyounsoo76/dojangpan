import AVKit
import AVFoundation
import UIKit

/// PiP(화면 속 화면) 창에 시계를 띄우는 일을 맡아요.
final class PiPManager: NSObject {
    /// 시계 프레임이 들어가는 레이어. 앱 화면의 미리보기에도 쓰고, PiP 창에도 그대로 떠요.
    let displayLayer = AVSampleBufferDisplayLayer()

    /// PiP 켜짐/꺼짐이 바뀔 때
    var onActiveChange: ((Bool) -> Void)?
    /// PiP를 띄울 수 있는 상태인지 바뀔 때
    var onPossibleChange: ((Bool) -> Void)?
    /// PiP 시작 실패
    var onError: ((Error) -> Void)?

    private var controller: AVPictureInPictureController?
    private var possibleObservation: NSKeyValueObservation?
    private let renderer = ClockRenderer()
    private var timer: Timer?
    private var lastRenderedSecond: Int = -1

    override init() {
        super.init()
        displayLayer.videoGravity = .resizeAspect
        displayLayer.backgroundColor = UIColor.black.cgColor
        setUpController()
        startTimer()

        NotificationCenter.default.addObserver(self, selector: #selector(settingsChanged),
                                               name: ClockSettings.changedNotification, object: nil)
    }

    deinit {
        timer?.invalidate()
        NotificationCenter.default.removeObserver(self)
    }

    var isSupported: Bool { AVPictureInPictureController.isPictureInPictureSupported() }
    var isActive: Bool { controller?.isPictureInPictureActive ?? false }
    var isPossible: Bool { controller?.isPictureInPicturePossible ?? false }

    func start() {
        render(force: true)
        controller?.startPictureInPicture()
    }

    func stop() {
        controller?.stopPictureInPicture()
    }

    func toggle() {
        if isActive { stop() } else { start() }
    }

    // MARK: - 내부

    private func setUpController() {
        guard AVPictureInPictureController.isPictureInPictureSupported() else { return }
        let source = AVPictureInPictureController.ContentSource(sampleBufferDisplayLayer: displayLayer,
                                                                playbackDelegate: self)
        let pip = AVPictureInPictureController(contentSource: source)
        pip.delegate = self
        pip.requiresLinearPlayback = true
        // 앱에서 나가면(홈·다른 앱) 저절로 PiP 창이 떠요
        pip.canStartPictureInPictureAutomaticallyFromInline = ClockSettings.shared.autoStart
        controller = pip

        possibleObservation = pip.observe(\.isPictureInPicturePossible, options: [.initial, .new]) { [weak self] pip, _ in
            DispatchQueue.main.async { self?.onPossibleChange?(pip.isPictureInPicturePossible) }
        }
    }

    private func startTimer() {
        render(force: true)
        // 0.5초마다 확인해서 초가 바뀌었을 때만 새 프레임을 넣어요
        let newTimer = Timer(timeInterval: 0.5, repeats: true) { [weak self] _ in
            self?.render()
        }
        newTimer.tolerance = 0.05
        RunLoop.main.add(newTimer, forMode: .common)
        timer = newTimer
    }

    @objc private func settingsChanged() {
        controller?.canStartPictureInPictureAutomaticallyFromInline = ClockSettings.shared.autoStart
        render(force: true)
    }

    private func render(force: Bool = false) {
        let now = Date()
        let second = Int(now.timeIntervalSince1970)
        if !force && second == lastRenderedSecond { return }
        lastRenderedSecond = second

        if displayLayer.status == .failed {
            displayLayer.flush()
        }
        guard let sample = renderer.makeSampleBuffer(date: now, settings: ClockSettings.shared) else { return }
        displayLayer.enqueue(sample)
    }
}

// MARK: - 재생 상태 (시계는 '생방송'처럼 끝이 없는 내용으로 알려요)

extension PiPManager: AVPictureInPictureSampleBufferPlaybackDelegate {
    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                    setPlaying playing: Bool) {
        // 일시정지 단추를 눌러도 시계는 계속 가요
    }

    func pictureInPictureControllerTimeRangeForPlayback(_ pictureInPictureController: AVPictureInPictureController) -> CMTimeRange {
        return CMTimeRange(start: .negativeInfinity, duration: .positiveInfinity)
    }

    func pictureInPictureControllerIsPlaybackPaused(_ pictureInPictureController: AVPictureInPictureController) -> Bool {
        return false
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                    didTransitionToRenderSize newRenderSize: CMVideoDimensions) {
        // 창 크기가 바뀌어도 프레임 크기는 그대로 둬요 (시스템이 알아서 맞춰요)
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                    skipByInterval skipInterval: CMTime,
                                    completion completionHandler: @escaping () -> Void) {
        completionHandler()
    }
}

// MARK: - PiP 켜짐/꺼짐 알림

extension PiPManager: AVPictureInPictureControllerDelegate {
    func pictureInPictureControllerDidStartPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        onActiveChange?(true)
    }

    func pictureInPictureControllerDidStopPictureInPicture(_ pictureInPictureController: AVPictureInPictureController) {
        onActiveChange?(false)
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                    failedToStartPictureInPictureWithError error: Error) {
        onActiveChange?(false)
        onError?(error)
    }

    func pictureInPictureController(_ pictureInPictureController: AVPictureInPictureController,
                                    restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void) {
        completionHandler(true)
    }
}
