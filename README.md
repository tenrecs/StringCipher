# StringCipher

Android Gradle plugin that encrypts selected string literals during the app build and generates a runtime decoder. Maven coordinate: `io.github.tenrecs:stringcipher:1.0.0`.

## Documentation / 使用说明

- [简体中文](stringCipher/README.md)
- [English](docs/README.en.md)
- [日本語](docs/README.ja.md)
- [한국어](docs/README.ko.md)
- [Español](docs/README.es.md)

The plugin applies to Android application modules. It processes project classes, not dependency classes, resources, manifest values, or annotations. Runtime decryption raises the cost of static analysis but cannot protect secrets embedded in an APK.

License: [Apache License 2.0](LICENSE).
