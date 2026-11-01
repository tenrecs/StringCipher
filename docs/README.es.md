# StringCipher — guía en español

StringCipher es un complemento de Gradle para Android que cifra literales de texto en las clases seleccionadas de la aplicación durante la compilación y genera una clase para descifrarlos en ejecución. Coordenadas Maven: `io.github.tenrecs:stringcipher:1.0.0`.

## Requisitos e instalación

Se aplica a módulos de **aplicación** Android y está orientado a AGP 7.2–9.x. El proyecto consumidor debe configurar AGP, un Gradle Wrapper compatible y el JDK correspondiente.

En el `build.gradle` raíz:

```groovy
buildscript {
    repositories { google(); mavenCentral() }
    dependencies {
        classpath 'com.android.tools.build:gradle:<versión-de-AGP>'
        classpath 'io.github.tenrecs:stringcipher:1.0.0'
    }
}
```

En el `build.gradle` del módulo de aplicación:

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

Sustituye `com.example.app` por el paquete de tu aplicación. Una lista `includePaths` vacía incluye todas las clases del proyecto que se puedan procesar; las exclusiones tienen prioridad. `algorithm` admite `MIXED`, `AES_GCM`, `AES_CBC_HMAC`, `XOR` y `XOR_ROTATE`. `storage` admite `BYTES` o `BASE64`; `paddingBytes` va de 0 a 64.

No se procesan cadenas de dependencias, recursos Android, Manifest ni anotaciones. La clave de descifrado se distribuye en el APK; no guardes secretos reales con esta función.

[Referencia completa de configuración (chino)](../stringCipher/README.md)
