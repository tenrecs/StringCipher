# StringCipher — 한국어 안내

StringCipher는 Android 앱의 선택한 클래스에 있는 문자열 리터럴을 빌드할 때 암호화하고 런타임 복호화 클래스를 생성하는 Gradle 플러그인입니다. Maven 좌표는 `io.github.tenrecs:stringcipher:1.0.0`입니다.

## 요구 사항과 설치

Android **애플리케이션** 모듈에서 사용합니다. AGP 7.2–9.x를 대상으로 하며, 사용하는 프로젝트에서 AGP와 호환되는 Gradle Wrapper 및 JDK를 설정해야 합니다.

루트 `build.gradle`에 추가합니다.

```groovy
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath 'com.android.tools.build:gradle:<AGP-버전>'
        classpath 'io.github.tenrecs:stringcipher:1.0.0'
    }
}
```

앱 모듈의 `build.gradle`에 추가합니다.

```groovy
apply plugin: 'com.android.application'
apply plugin: 'io.github.tenrecs.stringcipher'

stringCipher {
    enabled = true
    algorithm = 'MIXED'
    storage = 'BYTES'
    paddingBytes = 8
    includePaths = ['com.example.app']
    excludePaths = ['com.example.app.ui.theme']
}
```

`com.example.app`을 앱 패키지로 바꾸세요. `includePaths`가 비어 있으면 처리 가능한 프로젝트 클래스 전체가 대상이며 제외 규칙이 우선합니다. `algorithm`은 `MIXED`, `AES_GCM`, `AES_CBC_HMAC`, `XOR`, `XOR_ROTATE`를 지원합니다. `storage`는 `BYTES` 또는 `BASE64`, `paddingBytes`는 0–64입니다.

외부 의존성, Android 리소스, Manifest 및 어노테이션의 문자열은 처리하지 않습니다. 복호화 키도 APK에 포함되므로 실제 비밀값을 저장하는 용도로 사용하지 마세요.

[전체 설정 참고 문서(중국어)](../stringCipher/README.md)
