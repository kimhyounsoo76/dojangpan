# 떠 있는 시계 (iOS판)

유튜브 등 다른 앱 위에 시계를 띄워 두는 아이폰·아이패드 앱이에요.
아이폰은 '다른 앱 위에 표시'가 없어서, 화면 속 화면(PiP) 창에 시계를 그려 띄워요.

## 설치 파일(.ipa) 만들기

이 가지(브랜치)에 올리면 GitHub Actions가 맥 없이 알아서 빌드해요.

1. 이 가지에 푸시하거나, Actions 탭에서 **Build iOS IPA** 를 직접 실행
2. 끝나면 같은 가지의 `dist/` 폴더에 `FloatClock_iOS_v1.0_날짜.ipa` 가 올라와요 (Actions의 Artifacts에도 있음)
3. 실패하면 `logs/` 폴더에 오류 로그가 올라와요

## 아이폰에 넣기 (윈도우 PC)

1. 애플 사이트에서 iTunes 설치 (마이크로소프트 스토어판 말고) → https://www.apple.com/itunes/
2. Sideloadly 설치 → https://sideloadly.io/
3. 아이폰을 USB로 연결하고 Sideloadly에 .ipa를 끌어다 놓은 뒤, 애플 ID로 로그인해 Start
4. 아이폰: 설정 > 개인정보 보호 및 보안 > 개발자 모드 켜기 (재시동)
5. 아이폰: 설정 > 일반 > VPN 및 기기 관리 > 개발자 앱 신뢰

무료 애플 ID로 설치한 앱은 7일마다 다시 설치(갱신)해야 해요. Sideloadly의 자동 갱신(같은 와이파이)을 켜 두면 편해요.

## 구성

- `project.yml` — XcodeGen 프로젝트 정의 (빌드할 때 .xcodeproj를 만들어요)
- `FloatClock/Sources/` — Swift 소스
  - `PiPManager.swift` — PiP 창 띄우기 (AVPictureInPictureController + AVSampleBufferDisplayLayer)
  - `ClockRenderer.swift` — 시계를 1초마다 영상 프레임으로 그리기
  - `MainViewController.swift` — 앱 화면과 설정
- `.github/workflows/build.yml` — 자동 빌드

만든이: kingkongssam
