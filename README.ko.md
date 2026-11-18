<p align="center"><img src="app/src/main/res/drawable-nodpi/bladelight_reference.png" width="112" alt="BladeLight — 빛나는 두 블레이드"></p>

# BladeLight

Android에서 **Galaxy Buds3 Pro의 블레이드 라이트**를 제어합니다. 직접 켜고 끄거나, 버즈가 다시 연결될 때 자동으로 켤 수 있습니다.

[English](README.md) · **한국어**

## 기능

- 한 번의 터치로 조명 켜기·끄기, 재연결 시 자동 켜기
- 라이트·다크·시스템 테마
- 중단된 명령 복구, 상시 Bluetooth 연결 불필요
- 계정·광고·분석 수집·인터넷 권한 없음

**현재 한계:** 앱으로 켜면 일정하게 점등됩니다. 삼성 앱의 깜빡임·켜지고 흐려지기 패턴은 적용되지 않지만, 저장된 스타일을 덮어쓰지는 않습니다. 화면의 시그니처 그림은 실제 조명 상태를 표시하지 않습니다.

## 호환성

**Android 12 이상**이 필요합니다. 실기기 시험 범위는 Galaxy Buds3 Pro, 펌웨어 **R630XXU0AZG2**, Galaxy S25 FE / Android 17 / One UI 9.0입니다. 다른 펌웨어·휴대폰은 미검증이며, 최소 지원 버전이 모든 Android 버전에서의 실기기 검증을 뜻하지는 않습니다.

BladeLight는 삼성과 제휴하지 않은 독립 프로젝트이며 역공학으로 확인한 진단 인터페이스를 사용합니다. 장시간 배터리 소모와 저전력 상태 동작은 미검증입니다. 세부 범위는 [검증 기록](docs/validation.md)을 확인하세요.

## 사용 방법

1. 서명된 BladeLight APK를 설치하거나 [직접 빌드](#소스에서-빌드)합니다.
2. Android Bluetooth 설정에서 Galaxy Buds3 Pro를 페어링합니다.
3. 앱을 열어 주변 기기 권한을 허용하고 버즈를 선택합니다.
4. **켜기 / 끄기**를 누릅니다. 삼성 관리 앱은 실행한 채로 두어도 됩니다.
5. 필요하면 **연결 시 자동 켜기**를 활성화합니다. 다음 연결부터 적용되며, 옵션을 꺼도 이미 켜진 조명은 꺼지지 않습니다.

이름을 바꿨다면 Bluetooth 이름에 **Buds3 Pro**를 포함해야 선택 목록에 표시됩니다. 이름 검사는 선택을 돕기 위한 것으로 모델 인증은 아닙니다.

Android 설정에서 앱을 강제 종료했다면 다시 열어야 자동 동작이 재개됩니다. 명령이 중단된 경우 버즈를 연결하고 앱을 열어 복구를 완료하세요.

## 소스에서 빌드

JDK **21 권장**(최소 17), Android SDK **36**, Build Tools **35.0.0**이 필요합니다. Gradle Wrapper는 체크섬과 함께 **8.13**으로 고정되며 Android Gradle Plugin은 **8.13.2**입니다.

`ANDROID_HOME` 또는 Android Studio에서 SDK 경로를 설정한 뒤 실행합니다.

```sh
./gradlew :app:verifyCore :app:lintDebug :app:assembleDebug
```

Windows에서는 `gradlew.bat`을 사용합니다. 결과물은 `app/build/outputs/apk/debug/app-debug.apk`이며 개발용 서명 APK입니다. 독립 Java 테스트 3종은 `verifyCore`가 실행합니다. `testDebugUnitTest`만으로는 이 테스트들이 수행되지 않습니다. 배포 서명과 릴리즈 절차는 [릴리즈 문서](docs/releasing.md)에 정리했습니다.

## 기술 문서

| 문서 | 내용 |
| --- | --- |
| [Architecture](docs/architecture.md) | 명령 처리, 복구, 자동 동작과 로컬 데이터 |
| [Protocol](docs/protocol.md) | 통신 경로, 프레임, CRC와 응답 검증 |
| [Reverse engineering](docs/reverse-engineering.md) | 분석 출처, 내부 패턴 엔진, 실패한 대안과 BESAUD 조사 |
| [Validation](docs/validation.md) | 실제 관찰, 자동 검사와 검증 한계 |
| [Releasing](docs/releasing.md) | 빌드, 서명과 공개 절차 |

## 기여와 라이선스

문제 보고에는 휴대폰·Android·버즈 펌웨어 버전과 재현 단계를 포함해 주세요. 통신 응답과 실제 조명 관찰은 구분하고, 주소·일련번호·무관한 로그 등 개인정보는 제거해 주세요. [기여 안내](CONTRIBUTING.md)를 참고하세요.

공개 저장소에는 앱 소스, 필요한 자산·테스트·빌드 설정과 정리된 문서만 포함합니다. 원본 펌웨어·삼성 APK·디컴파일 결과·기기 캡처·서명 키·실험 도구는 제외합니다.

[MIT 라이선스](LICENSE)로 배포합니다. 외부 도구의 라이선스는 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)를 확인하세요. 공개 프로토콜 연구를 제공한 [GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient)와 분석에 사용한 [펌웨어 아카이브](https://github.com/timschneeb/galaxy-buds-firmware-archive)에 감사드립니다. 삼성 앱 코드와 펌웨어는 재배포하지 않습니다.
