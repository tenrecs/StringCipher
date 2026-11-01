# StringCipher — 日本語ガイド

StringCipher は、Android アプリ内の対象クラスにある文字列リテラルをビルド時に暗号化し、実行時の復号クラスを生成する Gradle プラグインです。座標は `io.github.tenrecs:stringcipher:1.0.0` です。

## 対応環境と導入

Android **アプリケーション** モジュール用です。AGP 7.2–9.x を想定しています。利用側のプロジェクトで AGP、対応する Gradle Wrapper と JDK を設定してください。

ルートの `build.gradle` に追加します。

```groovy
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath 'com.android.tools.build:gradle:<AGPのバージョン>'
        classpath 'io.github.tenrecs:stringcipher:1.0.0'
    }
}
```

アプリモジュールの `build.gradle` に追加します。

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

`com.example.app` をアプリのパッケージ名に変更してください。`includePaths` が空なら対象となるプロジェクト内の全クラスを処理します。除外設定が優先されます。`algorithm` は `MIXED`、`AES_GCM`、`AES_CBC_HMAC`、`XOR`、`XOR_ROTATE`、`storage` は `BYTES` または `BASE64`、`paddingBytes` は 0–64 です。

外部依存、Android リソース、Manifest、アノテーション内の文字列は処理しません。復号鍵も APK に含まれるため、秘密情報の保存には使用しないでください。

[詳細設定（中国語）](../stringCipher/README.md)
