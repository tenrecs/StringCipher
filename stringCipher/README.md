# StringCipher 使用说明

StringCipher 是本项目自实现的 Android Gradle 插件。打包时，它读取应用模块编译后的类文件，将选中类中的字符串字面量和静态字符串常量改写为密文与运行时解密调用。插件会把运行时解密类写入应用产物，接入方只需引用一个插件坐标。

## 适用环境

- 插件使用 AGP 的 `variant.instrumentation` 字节码 API，以及 `variant.sources.java` 生成运行时解密类的源码。代码面向 **AGP 7.2–9.x**；不同版本的实际构建与产物仍需在各自环境验证。
- 插件工程以 AGP 7.2.2 作为 `compileOnly` 编译基线。它不会向使用方传递 AGP；使用方应在自己的根项目中声明目标 AGP 版本，并选用与该 AGP 匹配的 Gradle Wrapper 和 JDK。
- AGP 7.0–7.1 没有此版本使用的完整 `variant.instrumentation` 接入方式；AGP 10 尚未验证。
- 运行时解密类使用 Android 23 及以上可用的 API。插件仅应用于 `com.android.application` 模块，配置项应写在该模块的 `build.gradle` 中。

## 接入配置

在使用方根项目的 `build.gradle` 中加入下文所示插件依赖，并在应用模块中应用插件：

```groovy
apply plugin: 'com.android.application'
apply plugin: 'io.github.tenrecs.stringcipher'

stringCipher {
    enabled = true
    algorithm = 'MIXED'
    storage = 'BYTES'
    paddingBytes = 8
    includePaths = ['io.github.tenrecs.stringcipher']
    excludePaths = ['io.github.tenrecs.stringcipher.ui.theme']
}
```

示例配置选择 `io.github.tenrecs.stringcipher` 下的应用类，并排除 `ui.theme` 子包。

## 全部配置参数

| 参数 | 类型 | 默认值 | 可选值及作用 |
| --- | --- | --- | --- |
| `enabled` | `boolean` | `true` | `true` 为每个变体注册字节码改写并生成解密类；`false` 不注册这些步骤。 |
| `algorithm` | `String` | `'MIXED'` | `'MIXED'`、`'AES_GCM'`、`'AES_CBC_HMAC'`、`'XOR'`、`'XOR_ROTATE'`。大小写需与示例一致。 |
| `storage` | `String` | `'BYTES'` | `'BYTES'` 在字节码中构造密文字节数组；`'BASE64'` 将密文写成 Base64 字符串。 |
| `paddingBytes` | `int` | `8` | `0` 到 `64`。在密文前后各加入指定数量的随机字节；`0` 表示不填充。 |
| `includePaths` | `List<String>` | `[]` | 需要处理的包路径或类路径。空列表表示所有应用类都可进入处理范围。 |
| `excludePaths` | `List<String>` | `[]` | 不处理的包路径或类路径。排除规则优先于包含规则。 |

`algorithm`、`storage` 和 `paddingBytes` 的非法值会使变体配置报错。列表中不能放空字符串。修改配置后，需要重新打包才能看到新的密文结果。

### 算法

| 值 | 行为 |
| --- | --- |
| `MIXED` | 对每个字符串随机选择 `AES_GCM` 或 `AES_CBC_HMAC`；不会随机选择 XOR。 |
| `AES_GCM` | 每个字符串使用随机 AES 密钥和 12 字节 IV，使用 128 位认证标签。 |
| `AES_CBC_HMAC` | 每个字符串使用随机 AES 密钥、HMAC 密钥和 16 字节 IV；先计算 HMAC-SHA256 并校验，再进行 AES-CBC 解密。 |
| `XOR` | 使用每个字符串独立生成的随机字节密钥做 XOR 混淆；强度低于两种 AES 方案。 |
| `XOR_ROTATE` | 在 XOR 基础上增加按字节位置变化的运算；属于轻量混淆，强度仍低于两种 AES 方案。 |

这些算法使用同一套运行时解密类。切换算法无需增加 Android 依赖。`MIXED` 选择的是构建期随机方案，并不是运行时由服务器下发算法。

### 密文存储和填充

`BYTES` 不在类文件中放完整的 Base64 密文字符串，而是生成字节数组构造指令。单个密文载荷超过 4096 字节时，插件会自动改用 Base64，避免生成过长的方法。`BASE64` 始终使用 Base64 存储；Base64 只是编码形式，不是加密算法。

`paddingBytes` 控制每一侧随机填充的**字节数**，不是最终密文总长度。例如 `paddingBytes = 8` 会增加 16 字节。它不会改变解密后的原始字符串。

### 路径选择规则

路径匹配发生在**编译后的应用类文件路径**上，而不是 `src/main/java`、`src/main/kotlin` 等源码目录。可使用点分包名、斜杠路径或具体类名：

```groovy
includePaths = [
    'io.github.tenrecs.stringcipher'
]
excludePaths = [
    'io/github/tenrecs/stringcipher/ui/theme/**'
]
```

- 包路径匹配该包及其子包；具体类路径匹配该类及其内部类。可写或省略路径末尾的 `/**`，它表示同一个包前缀。
- `includePaths = []` 表示不按路径限制应用类。`excludePaths = []` 表示没有额外排除。两者都有匹配时，**排除优先**。
- 这不是通用 glob 匹配：中间的 `*`、`?` 和绝对文件系统路径都不受支持。
- 路径只限制哪些类会改写。匹配类中的字符串会一起处理，不能用该配置选中类里的某个字段或方法。

## 在其他项目中复用

本目录是独立 Gradle 工程，包含自己的 Wrapper。发布到 Maven 仓库后，在目标项目根目录的 `build.gradle` 中配置：

```groovy
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:<目标项目的 AGP 版本>'
        classpath 'io.github.tenrecs:stringcipher:1.0.0'
    }
}
```

在目标项目的应用模块中，按前文顺序应用两个插件并配置 `stringCipher { ... }`。发布到其他 Maven 仓库时，在 `repositories` 中添加该仓库的地址。目标项目无需复制 `buildSrc` 或再引入运行时 AAR。

## 处理范围与安全边界

插件处理应用模块 `PROJECT` 范围内的类文件。它会跳过 `BuildConfig.class`、`R.class` 和 `R$...class`；项目外部依赖、Android XML 字符串资源、Manifest 值和注解值不会被自动加密。应用运行时需要解密，因此密钥也会随 APK 分发。此功能用于提高静态反编译的分析成本，不能安全保存服务端密钥、令牌或其他真正的秘密。

## 发布准备

发布坐标为 `io.github.tenrecs:stringcipher:1.0.0`。本地参数配置、签名、打包和 Central Portal 上传步骤见 [PUBLISHING.md](PUBLISHING.md)。
