import UIKit
import CoreMedia
import CoreVideo

/// 현재 시각을 그림(픽셀 버퍼)으로 그려 영상 프레임(CMSampleBuffer)으로 만들어요.
/// PiP 창은 영상만 띄울 수 있어서, 시계를 1초마다 한 장짜리 영상으로 만들어 넣어요.
final class ClockRenderer {
    /// 프레임 크기 (가로:세로 = 3:1 → 납작한 시계 창)
    let width = 720
    let height = 240

    private var pool: CVPixelBufferPool?
    private var formatDescription: CMVideoFormatDescription?
    private let timeFormatter = DateFormatter()
    private let dateFormatter = DateFormatter()

    init() {
        var attrs: [String: Any] = [:]
        attrs[kCVPixelBufferPixelFormatTypeKey as String] = kCVPixelFormatType_32BGRA
        attrs[kCVPixelBufferWidthKey as String] = width
        attrs[kCVPixelBufferHeightKey as String] = height
        attrs[kCVPixelBufferIOSurfacePropertiesKey as String] = [String: Any]()
        attrs[kCVPixelBufferCGBitmapContextCompatibilityKey as String] = true

        var newPool: CVPixelBufferPool?
        CVPixelBufferPoolCreate(kCFAllocatorDefault, nil, attrs as CFDictionary, &newPool)
        pool = newPool

        let locale = Locale(identifier: "ko_KR")
        timeFormatter.locale = locale
        dateFormatter.locale = locale
        dateFormatter.dateFormat = "M월 d일 EEEE"
    }

    func makeSampleBuffer(date: Date, settings: ClockSettings) -> CMSampleBuffer? {
        guard let pool = pool else { return nil }

        var newBuffer: CVPixelBuffer?
        let status = CVPixelBufferPoolCreatePixelBuffer(kCFAllocatorDefault, pool, &newBuffer)
        guard status == kCVReturnSuccess, let pixelBuffer = newBuffer else { return nil }

        draw(into: pixelBuffer, date: date, settings: settings)

        if formatDescription == nil {
            var newDescription: CMVideoFormatDescription?
            CMVideoFormatDescriptionCreateForImageBuffer(allocator: kCFAllocatorDefault,
                                                         imageBuffer: pixelBuffer,
                                                         formatDescriptionOut: &newDescription)
            formatDescription = newDescription
        }
        guard let description = formatDescription else { return nil }

        var timing = CMSampleTimingInfo(duration: CMTime(value: 1, timescale: 1),
                                        presentationTimeStamp: CMClockGetTime(CMClockGetHostTimeClock()),
                                        decodeTimeStamp: .invalid)
        var newSample: CMSampleBuffer?
        let result = CMSampleBufferCreateReadyWithImageBuffer(allocator: kCFAllocatorDefault,
                                                              imageBuffer: pixelBuffer,
                                                              formatDescription: description,
                                                              sampleTiming: &timing,
                                                              sampleBufferOut: &newSample)
        guard result == noErr, let sample = newSample else { return nil }

        // 받자마자 바로 화면에 보여 주도록 표시
        if let attachments = CMSampleBufferGetSampleAttachmentsArray(sample, createIfNecessary: true),
           CFArrayGetCount(attachments) > 0 {
            let dict = unsafeBitCast(CFArrayGetValueAtIndex(attachments, 0), to: CFMutableDictionary.self)
            CFDictionarySetValue(dict,
                                 Unmanaged.passUnretained(kCMSampleAttachmentKey_DisplayImmediately).toOpaque(),
                                 Unmanaged.passUnretained(kCFBooleanTrue).toOpaque())
        }
        return sample
    }

    // MARK: - 그리기

    private func draw(into pixelBuffer: CVPixelBuffer, date: Date, settings: ClockSettings) {
        CVPixelBufferLockBaseAddress(pixelBuffer, [])
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, []) }

        guard let base = CVPixelBufferGetBaseAddress(pixelBuffer) else { return }
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
        let bitmapInfo = CGBitmapInfo.byteOrder32Little.rawValue | CGImageAlphaInfo.premultipliedFirst.rawValue
        guard let context = CGContext(data: base,
                                      width: width,
                                      height: height,
                                      bitsPerComponent: 8,
                                      bytesPerRow: bytesPerRow,
                                      space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: bitmapInfo) else { return }

        context.setFillColor(UIColor.black.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))

        // UIKit 글자 그리기용으로 좌표계를 뒤집어요 (왼쪽 위가 원점)
        context.translateBy(x: 0, y: CGFloat(height))
        context.scaleBy(x: 1, y: -1)
        UIGraphicsPushContext(context)
        defer { UIGraphicsPopContext() }

        let w = CGFloat(width)
        let h = CGFloat(height)
        let color = settings.textColor
        let timeText = timeString(date, settings: settings)

        if settings.showDate {
            drawCentered(timeText,
                         in: CGRect(x: 0, y: h * 0.04, width: w, height: h * 0.62),
                         weight: .bold, color: color, maxWidthRatio: 0.92)
            drawCentered(dateFormatter.string(from: date),
                         in: CGRect(x: 0, y: h * 0.66, width: w, height: h * 0.30),
                         weight: .medium, color: color.withAlphaComponent(0.85), maxWidthRatio: 0.9)
        } else {
            drawCentered(timeText,
                         in: CGRect(x: 0, y: h * 0.08, width: w, height: h * 0.84),
                         weight: .bold, color: color, maxWidthRatio: 0.92)
        }
    }

    private func timeString(_ date: Date, settings: ClockSettings) -> String {
        var format = settings.use24Hour ? "HH:mm" : "a h:mm"
        if settings.showSeconds { format += ":ss" }
        timeFormatter.dateFormat = format
        return timeFormatter.string(from: date)
    }

    /// 사각형 안에 꽉 차게, 가운데 정렬로 글자를 그려요.
    private func drawCentered(_ text: String, in rect: CGRect, weight: UIFont.Weight,
                              color: UIColor, maxWidthRatio: CGFloat) {
        let probeSize: CGFloat = 100
        let probeFont = UIFont.monospacedDigitSystemFont(ofSize: probeSize, weight: weight)
        let probe = (text as NSString).size(withAttributes: [.font: probeFont])
        guard probe.width > 0, probe.height > 0 else { return }

        let scale = min(rect.width * maxWidthRatio / probe.width, rect.height / probe.height)
        let font = UIFont.monospacedDigitSystemFont(ofSize: floor(probeSize * scale), weight: weight)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let size = (text as NSString).size(withAttributes: attributes)
        let origin = CGPoint(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2)
        (text as NSString).draw(at: origin, withAttributes: attributes)
    }
}
