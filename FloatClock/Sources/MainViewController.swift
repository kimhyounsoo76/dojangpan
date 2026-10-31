import UIKit

final class MainViewController: UIViewController {
    private let pip = PiPManager()
    private let settings = ClockSettings.shared

    private let previewContainer = UIView()
    private let statusLabel = UILabel()
    private let mainButton = UIButton(type: .system)
    private let secondsSwitch = UISwitch()
    private let hour24Switch = UISwitch()
    private let dateSwitch = UISwitch()
    private let autoSwitch = UISwitch()
    private let colorControl = UISegmentedControl(items: ClockSettings.colors.map { $0.name })

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "떠 있는 시계"
        overrideUserInterfaceStyle = .dark
        view.backgroundColor = .systemBackground
        buildUI()
        bind()
        updateState()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        pip.displayLayer.frame = previewContainer.bounds
        CATransaction.commit()
    }

    // MARK: - 화면 구성

    private func buildUI() {
        let scroll = UIScrollView()
        scroll.alwaysBounceVertical = true
        scroll.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scroll)

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 20
        stack.translatesAutoresizingMaskIntoConstraints = false
        scroll.addSubview(stack)

        let widthConstraint = stack.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor, constant: -40)
        widthConstraint.priority = UILayoutPriority(999)

        NSLayoutConstraint.activate([
            scroll.topAnchor.constraint(equalTo: view.topAnchor),
            scroll.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            scroll.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scroll.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scroll.contentLayoutGuide.widthAnchor.constraint(equalTo: scroll.frameLayoutGuide.widthAnchor),

            stack.topAnchor.constraint(equalTo: scroll.contentLayoutGuide.topAnchor, constant: 16),
            stack.bottomAnchor.constraint(equalTo: scroll.contentLayoutGuide.bottomAnchor, constant: -40),
            stack.centerXAnchor.constraint(equalTo: scroll.contentLayoutGuide.centerXAnchor),
            widthConstraint,
            stack.widthAnchor.constraint(lessThanOrEqualToConstant: 560),
        ])

        // 미리보기 (PiP 창에 뜨는 모습 그대로)
        previewContainer.backgroundColor = .black
        previewContainer.layer.cornerRadius = 16
        previewContainer.layer.masksToBounds = true
        previewContainer.layer.addSublayer(pip.displayLayer)
        stack.addArrangedSubview(previewContainer)
        previewContainer.heightAnchor.constraint(equalTo: previewContainer.widthAnchor, multiplier: 1.0 / 3.0).isActive = true

        let previewCaption = makeCaption("영상 위에 이렇게 떠요")
        stack.addArrangedSubview(previewCaption)
        stack.setCustomSpacing(8, after: previewContainer)

        // 상태 + 큰 단추
        statusLabel.font = .preferredFont(forTextStyle: .subheadline)
        statusLabel.textColor = .secondaryLabel
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 0
        stack.addArrangedSubview(statusLabel)

        var config = UIButton.Configuration.filled()
        config.cornerStyle = .large
        config.baseBackgroundColor = UIColor(red: 0.96, green: 0.65, blue: 0.14, alpha: 1)
        config.baseForegroundColor = .black
        config.contentInsets = NSDirectionalEdgeInsets(top: 16, leading: 20, bottom: 16, trailing: 20)
        var titleAttr = AttributeContainer()
        titleAttr.font = UIFont.systemFont(ofSize: 20, weight: .bold)
        config.attributedTitle = AttributedString("시계 띄우기", attributes: titleAttr)
        mainButton.configuration = config
        mainButton.addTarget(self, action: #selector(mainButtonTapped), for: .touchUpInside)
        stack.addArrangedSubview(mainButton)
        stack.setCustomSpacing(10, after: statusLabel)

        // 보이는 모습
        let lookCard = makeCard(title: "보이는 모습")
        lookCard.addArrangedSubview(makeRow("초 보이기", secondsSwitch))
        lookCard.addArrangedSubview(makeRow("24시간으로 보기", hour24Switch))
        lookCard.addArrangedSubview(makeRow("날짜 보이기", dateSwitch))
        let colorRow = UIStackView(arrangedSubviews: [makeRowLabel("글자 색"), colorControl])
        colorRow.axis = .vertical
        colorRow.spacing = 10
        lookCard.addArrangedSubview(colorRow)
        stack.addArrangedSubview(lookCard)

        // 동작
        let actionCard = makeCard(title: "동작")
        actionCard.addArrangedSubview(makeRow("앱에서 나가면 저절로 띄우기", autoSwitch))
        actionCard.addArrangedSubview(makeCaption("켜 두면 이 앱을 연 채로 홈이나 유튜브로 가는 순간 시계 창이 떠요."))
        stack.addArrangedSubview(actionCard)

        // 사용법
        let helpCard = makeCard(title: "사용법")
        let help = UILabel()
        help.numberOfLines = 0
        help.font = .preferredFont(forTextStyle: .body)
        help.textColor = .label
        help.text = """
        1. '시계 띄우기'를 누르거나, 이 앱을 열어 둔 채 유튜브로 가면 시계 창이 떠요.
        2. 창을 끌면 옮겨지고, 두 손가락으로 벌리면 커져요.
        3. 창을 화면 가장자리 밖으로 밀면 잠시 숨길 수 있어요.
        4. 창의 X를 누르면 꺼져요.

        ※ 아이폰은 다른 앱 위에 투명하게 띄우는 걸 허용하지 않아서, 검은 창(화면 속 화면)으로 떠요.
        """
        helpCard.addArrangedSubview(help)
        stack.addArrangedSubview(helpCard)

        let credit = makeCaption("만든이: kingkongssam")
        credit.textAlignment = .center
        stack.addArrangedSubview(credit)
    }

    private func makeCard(title: String) -> UIStackView {
        let card = UIStackView()
        card.axis = .vertical
        card.spacing = 14
        card.isLayoutMarginsRelativeArrangement = true
        card.layoutMargins = UIEdgeInsets(top: 16, left: 16, bottom: 16, right: 16)
        card.backgroundColor = .secondarySystemBackground
        card.layer.cornerRadius = 16

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .preferredFont(forTextStyle: .headline)
        titleLabel.textColor = .secondaryLabel
        card.addArrangedSubview(titleLabel)
        return card
    }

    private func makeRowLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .preferredFont(forTextStyle: .body)
        label.textColor = .label
        label.numberOfLines = 0
        label.setContentHuggingPriority(.defaultLow, for: .horizontal)
        return label
    }

    private func makeRow(_ text: String, _ control: UIControl) -> UIStackView {
        let row = UIStackView(arrangedSubviews: [makeRowLabel(text), control])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        control.setContentHuggingPriority(.required, for: .horizontal)
        control.setContentCompressionResistancePriority(.required, for: .horizontal)
        return row
    }

    private func makeCaption(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .preferredFont(forTextStyle: .footnote)
        label.textColor = .secondaryLabel
        label.numberOfLines = 0
        return label
    }

    // MARK: - 연결

    private func bind() {
        secondsSwitch.isOn = settings.showSeconds
        hour24Switch.isOn = settings.use24Hour
        dateSwitch.isOn = settings.showDate
        autoSwitch.isOn = settings.autoStart
        colorControl.selectedSegmentIndex = settings.colorIndex

        secondsSwitch.addTarget(self, action: #selector(switchChanged(_:)), for: .valueChanged)
        hour24Switch.addTarget(self, action: #selector(switchChanged(_:)), for: .valueChanged)
        dateSwitch.addTarget(self, action: #selector(switchChanged(_:)), for: .valueChanged)
        autoSwitch.addTarget(self, action: #selector(switchChanged(_:)), for: .valueChanged)
        colorControl.addTarget(self, action: #selector(colorChanged), for: .valueChanged)

        pip.onActiveChange = { [weak self] _ in self?.updateState() }
        pip.onPossibleChange = { [weak self] _ in self?.updateState() }
        pip.onError = { [weak self] error in self?.showError(error) }

        NotificationCenter.default.addObserver(self, selector: #selector(updateState),
                                               name: UIApplication.didBecomeActiveNotification, object: nil)
    }

    @objc private func switchChanged(_ sender: UISwitch) {
        switch sender {
        case secondsSwitch: settings.showSeconds = sender.isOn
        case hour24Switch: settings.use24Hour = sender.isOn
        case dateSwitch: settings.showDate = sender.isOn
        case autoSwitch: settings.autoStart = sender.isOn
        default: break
        }
    }

    @objc private func colorChanged() {
        settings.colorIndex = colorControl.selectedSegmentIndex
    }

    @objc private func mainButtonTapped() {
        pip.toggle()
    }

    @objc private func updateState() {
        guard pip.isSupported else {
            statusLabel.text = "이 기기는 화면 속 화면(PiP)을 지원하지 않아요."
            mainButton.isEnabled = false
            return
        }
        let active = pip.isActive
        mainButton.isEnabled = active || pip.isPossible
        if var config = mainButton.configuration {
            var titleAttr = AttributeContainer()
            titleAttr.font = UIFont.systemFont(ofSize: 20, weight: .bold)
            config.attributedTitle = AttributedString(active ? "시계 내리기" : "시계 띄우기", attributes: titleAttr)
            mainButton.configuration = config
        }
        if active {
            statusLabel.text = "시계가 떠 있어요. 유튜브를 열어 보세요."
        } else if pip.isPossible {
            statusLabel.text = "단추를 누르거나, 앱에서 나가면 시계가 떠요."
        } else {
            statusLabel.text = "준비하는 중이에요…"
        }
    }

    private func showError(_ error: Error) {
        let alert = UIAlertController(title: "시계를 띄우지 못했어요",
                                      message: error.localizedDescription,
                                      preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "확인", style: .default))
        present(alert, animated: true)
    }
}
