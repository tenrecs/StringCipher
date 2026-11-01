# 本地准备并发布到 Maven Central

当前坐标是 `io.github.tenrecs:stringcipher:1.0.0`。本工程使用 Gradle 自带的 `maven-publish` 和 `signing` 插件，先把 Maven 文件写到本地，再通过 Central Portal 上传 ZIP。Portal 网页上传不使用用户令牌；令牌留给以后使用发布 API。

## 1. 准备账号与项目资料

确认 Central Portal 中 `io.github.tenrecs` 命名空间已验证。确定公开 Git 仓库 URL、代码许可证、开发者名称和邮箱。仓库内应有对应的许可证文件；不要填写尚未取得授权的许可证。

将公钥发布到 Sonatype 支持的密钥服务器。把下面的 `<KEY_ID>` 换成 `gpg --list-secret-keys --keyid-format LONG` 显示的主密钥 ID：

```bash
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

## 2. 在本机保存发布参数

先导出 ASCII 格式的私钥到用户目录中的文件，并限制文件权限：

```bash
gpg --armor --export-secret-keys <KEY_ID> > ~/.gradle/stringcipher-signing-key.asc
chmod 600 ~/.gradle/stringcipher-signing-key.asc
```

本库已创建 `stringCipher/gradle.properties`，在其中填写空值即可。这个文件已被本库的 `.gitignore` 忽略；每个待发布的库各自保留一份，就不会与其他库的项目 URL、许可证或开发者信息冲突。`signingPassword` 是生成 PGP 密钥时设置的密码。该文件和私钥都不要提交到 Git，也不要发送给他人。下面是填写格式示例：

```properties
projectUrl=https://github.com/tenrecs/StringCipher
scmUrl=https://github.com/tenrecs/StringCipher
scmConnection=scm:git:https://github.com/tenrecs/StringCipher.git
scmDeveloperConnection=scm:git:ssh://git@github.com/tenrecs/StringCipher.git
licenseName=<实际许可证名称>
licenseUrl=<实际许可证 URL>
developerId=tenrecs
developerName=<公开显示的名称>
developerEmail=<公开显示的邮箱>
signingKeyFile=/Users/<你的用户名>/.gradle/stringcipher-signing-key.asc
signingPassword=<PGP 私钥密码>
```

## 3. 生成本地发布文件

在本目录 `stringCipher` 执行：

```bash
./gradlew publishStringCipherPluginPublicationToReleaseRepository
```

文件会写入 `build/staging-repository/io/github/tenrecs/stringcipher/1.0.0/`。检查该目录至少包含 `.pom`、主 JAR、`-sources.jar`、`-javadoc.jar`，以及这四个文件各自的 `.asc` 签名。检查 POM 中的 URL、许可证、开发者和 SCM 信息均为真实值。

为这些文件生成校验和，并从 Maven 仓库布局的根目录打包：

```bash
cd build/staging-repository
for file in io/github/tenrecs/stringcipher/1.0.0/*; do
    case "$file" in *.md5|*.sha1) continue ;; esac
    md5 -q "$file" > "$file.md5"
    shasum -a 1 "$file" | cut -d ' ' -f 1 > "$file.sha1"
done
zip -r ../stringcipher-1.0.0-central.zip io
```

ZIP 应位于 `build/stringcipher-1.0.0-central.zip`，解压后的顶层目录应是 `io/`。

## 4. 上传并发布

登录 [Central Portal](https://central.sonatype.com/)，进入 **Publishing Settings → Deployments → Publish Component**，上传 `build/stringcipher-1.0.0-central.zip`。等待校验通过后，在 Portal 中确认正式发布。发布后的同一版本不能覆盖；若需修复，使用新版本号。

参考：[Portal ZIP 上传说明](https://central.sonatype.org/publish/publish-portal-upload/)、[发布文件要求](https://central.sonatype.org/publish/requirements/)、[PGP 公钥说明](https://central.sonatype.org/publish/requirements/gpg/)。
