# StringCipher — English guide

StringCipher is an Android Gradle plugin that transforms string literals in selected app classes and adds a runtime decoder. The current release is `io.github.tenrecs:stringcipher:1.0.0`.

## Requirements

Use it in an Android **application** module with AGP 7.2–9.x. Declare the AGP version, compatible Gradle wrapper, and JDK in the consuming project. This plugin does not add AGP as a transitive dependency.

## Install

Add the plugin JAR to the root `build.gradle` buildscript classpath:

```groovy
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath 'com.android.tools.build:gradle:<your-AGP-version>'
        classpath 'io.github.tenrecs:stringcipher:1.0.0'
    }
}
```

In the app module's `build.gradle`:

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

Replace `com.example.app` with your app's package. An empty `includePaths` processes all eligible project classes; exclusions take precedence. Paths accept dotted packages, slash-separated paths, or exact class paths. The extension supports `MIXED`, `AES_GCM`, `AES_CBC_HMAC`, `XOR`, and `XOR_ROTATE`; `storage` is `BYTES` or `BASE64`; `paddingBytes` ranges from 0 to 64.

The plugin does not transform strings in dependencies, Android resources, manifest values, or annotations. Decryption keys are shipped with the APK, so do not use this feature to store real secrets.

[Full configuration reference (Chinese)](../stringCipher/README.md)
