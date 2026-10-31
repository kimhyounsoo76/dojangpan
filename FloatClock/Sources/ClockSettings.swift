import UIKit

/// 사용자 설정 (앱을 껐다 켜도 유지)
final class ClockSettings {
    static let shared = ClockSettings()
    static let changedNotification = Notification.Name("ClockSettingsChanged")

    struct ColorOption {
        let name: String
        let color: UIColor
    }

    static let colors: [ColorOption] = [
        ColorOption(name: "흰색", color: .white),
        ColorOption(name: "노랑", color: UIColor(red: 1.00, green: 0.84, blue: 0.20, alpha: 1)),
        ColorOption(name: "연두", color: UIColor(red: 0.55, green: 0.95, blue: 0.45, alpha: 1)),
        ColorOption(name: "하늘", color: UIColor(red: 0.45, green: 0.80, blue: 1.00, alpha: 1)),
    ]

    private let defaults = UserDefaults.standard
    private init() {}

    private func bool(_ key: String, fallback value: Bool) -> Bool {
        return defaults.object(forKey: key) as? Bool ?? value
    }

    private func store(_ value: Any, _ key: String) {
        defaults.set(value, forKey: key)
        NotificationCenter.default.post(name: ClockSettings.changedNotification, object: nil)
    }

    /// 초 보이기
    var showSeconds: Bool {
        get { bool("showSeconds", fallback: true) }
        set { store(newValue, "showSeconds") }
    }

    /// 24시간으로 보기 (끄면 오전/오후)
    var use24Hour: Bool {
        get { bool("use24Hour", fallback: true) }
        set { store(newValue, "use24Hour") }
    }

    /// 날짜 보이기
    var showDate: Bool {
        get { bool("showDate", fallback: false) }
        set { store(newValue, "showDate") }
    }

    /// 앱에서 나가면 저절로 시계 띄우기
    var autoStart: Bool {
        get { bool("autoStart", fallback: true) }
        set { store(newValue, "autoStart") }
    }

    /// 글자 색 (colors 배열의 번호)
    var colorIndex: Int {
        get {
            let i = defaults.integer(forKey: "colorIndex")
            return (0..<ClockSettings.colors.count).contains(i) ? i : 0
        }
        set { store(newValue, "colorIndex") }
    }

    var textColor: UIColor { ClockSettings.colors[colorIndex].color }
}
