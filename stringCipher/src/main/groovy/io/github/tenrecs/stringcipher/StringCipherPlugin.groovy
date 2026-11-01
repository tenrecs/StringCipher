package io.github.tenrecs.stringcipher

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.SecureRandom
import java.util.Arrays

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class StringCipherPlugin implements Plugin<Project> {
    void apply(Project project) {
        StringCipherExtension configuration = project.extensions.create('stringCipher', StringCipherExtension)
        project.plugins.withId('com.android.application') {
            def components = project.extensions.getByName('androidComponents')
            components.onVariants(components.selector().all(), { variant ->
                configuration.validate()
                if (!configuration.enabled) return
                def task = project.tasks.register('generateStringCipher' + variant.name.capitalize() + 'Runtime', GenerateRuntimeSource) {
                    outputDirectory.set(project.layout.buildDirectory.dir('generated/stringCipher/' + variant.name))
                }
                variant.sources.java.addGeneratedSourceDirectory(task, { generated -> generated.outputDirectory } as kotlin.jvm.functions.Function1)
                variant.instrumentation.transformClassesWith(CipherClassVisitorFactory, InstrumentationScope.PROJECT, { params ->
                    params.enabled.set(configuration.enabled)
                    params.algorithm.set(configuration.algorithm)
                    params.storage.set(configuration.storage)
                    params.paddingBytes.set(configuration.paddingBytes)
                    params.includePaths.set(configuration.includePaths)
                    params.excludePaths.set(configuration.excludePaths)
                    kotlin.Unit.INSTANCE
                } as kotlin.jvm.functions.Function1)
                variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS)
            } as Action)
        }
    }
}

class StringCipherExtension {
    boolean enabled = true
    String algorithm = 'MIXED'
    String storage = 'BYTES'
    int paddingBytes = 8
    List<String> includePaths = []
    List<String> excludePaths = []

    void validate() {
        if (!(algorithm in ['AES_GCM', 'AES_CBC_HMAC', 'XOR', 'XOR_ROTATE', 'MIXED'])) throw new IllegalArgumentException('Unsupported stringCipher.algorithm')
        if (!(storage in ['BYTES', 'BASE64'])) throw new IllegalArgumentException('stringCipher.storage must be BYTES or BASE64')
        if (paddingBytes < 0 || paddingBytes > 64) throw new IllegalArgumentException('stringCipher.paddingBytes must be between 0 and 64')
        (includePaths ?: []).plus(excludePaths ?: []).each { path ->
            if (!path?.trim()) throw new IllegalArgumentException('stringCipher paths must not be blank')
        }
    }
}

abstract class GenerateRuntimeSource extends DefaultTask {
    @OutputDirectory
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generate() {
        InputStream source = getClass().getResourceAsStream('/runtime-source/io/github/tenrecs/stringcipher/StringObfuscator.java')
        if (source == null) throw new IllegalStateException('String cipher runtime source is missing from the plugin JAR')
        File target = new File(outputDirectory.get().asFile, 'io/github/tenrecs/stringcipher/StringObfuscator.java')
        target.parentFile.mkdirs()
        try { Files.copy(source, target.toPath(), StandardCopyOption.REPLACE_EXISTING) } finally { source.close() }
    }
}

interface CipherParameters extends InstrumentationParameters {
    @Input
    Property<Boolean> getEnabled()
    @Input
    Property<String> getAlgorithm()
    @Input
    Property<String> getStorage()
    @Input
    Property<Integer> getPaddingBytes()
    @Input
    ListProperty<String> getIncludePaths()
    @Input
    ListProperty<String> getExcludePaths()
}

class CipherSettings {
    final String algorithm
    final String storage
    final int paddingBytes

    CipherSettings(CipherParameters parameters) {
        algorithm = parameters.algorithm.get()
        storage = parameters.storage.get()
        paddingBytes = parameters.paddingBytes.get()
    }
}

abstract class CipherClassVisitorFactory implements AsmClassVisitorFactory<CipherParameters> {
    boolean isInstrumentable(ClassData data) {
        CipherParameters settings = parameters.get()
        String classPath = data.className.replace('.', '/')
        settings.enabled.get() && !CipherPaths.generated(classPath) && CipherPaths.selected(classPath, settings.includePaths.get(), settings.excludePaths.get())
    }

    ClassVisitor createClassVisitor(ClassContext context, ClassVisitor delegate) {
        new CipherClassVisitor(instrumentationContext.apiVersion.get(), delegate, new CipherSettings(parameters.get()))
    }
}

class CipherPaths {
    static boolean generated(String classPath) {
        String name = classPath.substring(classPath.lastIndexOf('/') + 1)
        classPath == 'io/github/tenrecs/stringcipher/StringObfuscator' || name == 'BuildConfig' || name == 'R' || name.startsWith('R$')
    }

    static boolean selected(String classPath, List<String> includes, List<String> excludes) {
        boolean included = !includes || includes.any { path -> matches(classPath, path) }
        included && !(excludes?.any { path -> matches(classPath, path) })
    }

    private static boolean matches(String classPath, String configuredPath) {
        String path = configuredPath.trim().replace('\\', '/').replaceAll('^/+', '').replaceAll('/+$', '')
        if (path.endsWith('/**')) path = path.substring(0, path.length() - 3)
        if (path.endsWith('.class')) path = path.substring(0, path.length() - 6)
        path = path.replace('.', '/')
        classPath == path || classPath.startsWith(path + '/') || classPath.startsWith(path + '$')
    }
}

class CipherClassVisitor extends ClassVisitor {
    private final CipherSettings settings
    private final int asmApi
    String className
    boolean hasInitializer
    List<Map> constants = []

    CipherClassVisitor(int asmApi, ClassVisitor delegate, CipherSettings settings) {
        super(asmApi, delegate)
        this.asmApi = asmApi
        this.settings = settings
    }

    void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        className = name
        super.visit(version, access, name, signature, superName, interfaces)
    }

    FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (descriptor == 'Ljava/lang/String;' && value instanceof String && (access & Opcodes.ACC_STATIC) != 0) {
            constants << [name: name, value: value]
            return super.visitField(access, name, descriptor, signature, null)
        }
        super.visitField(access, name, descriptor, signature, value)
    }

    MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        MethodVisitor method = super.visitMethod(access, name, descriptor, signature, exceptions)
        if (name == '<clinit>') hasInitializer = true
        new CipherMethodVisitor(asmApi, method, this, name == '<clinit>', settings)
    }

    void visitEnd() {
        if (!hasInitializer && !constants.isEmpty()) {
            MethodVisitor method = super.visitMethod(Opcodes.ACC_STATIC, '<clinit>', '()V', null, null)
            method.visitCode()
            constants.each { constant ->
                CipherCodec.emitDecrypt(method, constant.value, settings)
                method.visitFieldInsn(Opcodes.PUTSTATIC, className, constant.name, 'Ljava/lang/String;')
            }
            method.visitInsn(Opcodes.RETURN)
            method.visitMaxs(0, 0)
            method.visitEnd()
        }
        super.visitEnd()
    }
}

class CipherMethodVisitor extends MethodVisitor {
    private final CipherClassVisitor ownerClass
    private final boolean initializer
    private final CipherSettings settings

    CipherMethodVisitor(int asmApi, MethodVisitor delegate, CipherClassVisitor ownerClass, boolean initializer, CipherSettings settings) {
        super(asmApi, delegate)
        this.ownerClass = ownerClass
        this.initializer = initializer
        this.settings = settings
    }

    void visitCode() {
        super.visitCode()
        if (initializer) ownerClass.constants.each { constant ->
            CipherCodec.emitDecrypt(mv, constant.value, settings)
            mv.visitFieldInsn(Opcodes.PUTSTATIC, ownerClass.className, constant.name, 'Ljava/lang/String;')
        }
    }

    void visitLdcInsn(Object value) {
        if (value instanceof String) CipherCodec.emitDecrypt(mv, value, settings)
        else super.visitLdcInsn(value)
    }
}

class CipherCodec {
    private static final String RUNTIME = 'io/github/tenrecs/stringcipher/StringObfuscator'
    private static final SecureRandom RANDOM = new SecureRandom()

    static void emitDecrypt(MethodVisitor method, String value, CipherSettings configuration) {
        int algorithm = ['AES_GCM': 1, 'XOR': 2, 'AES_CBC_HMAC': 3, 'XOR_ROTATE': 4][configuration.algorithm] ?: (RANDOM.nextBoolean() ? 1 : 3)
        byte[] key = new byte[algorithm == 3 ? 32 : 16]
        RANDOM.nextBytes(key)
        byte[] cleartext = value.getBytes('UTF-8')
        byte[] encrypted
        if (algorithm == 1) {
            byte[] iv = new byte[12]
            RANDOM.nextBytes(iv)
            Cipher cipher = Cipher.getInstance('AES/GCM/NoPadding')
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, 'AES'), new GCMParameterSpec(128, iv))
            byte[] ciphertext = cipher.doFinal(cleartext)
            encrypted = new byte[iv.length + ciphertext.length]
            System.arraycopy(iv, 0, encrypted, 0, iv.length)
            System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length)
        } else if (algorithm == 3) {
            byte[] iv = new byte[16]
            RANDOM.nextBytes(iv)
            Cipher cipher = Cipher.getInstance('AES/CBC/PKCS5Padding')
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Arrays.copyOfRange(key, 0, 16), 'AES'), new IvParameterSpec(iv))
            byte[] ciphertext = cipher.doFinal(cleartext)
            Mac mac = Mac.getInstance('HmacSHA256')
            mac.init(new SecretKeySpec(Arrays.copyOfRange(key, 16, 32), 'HmacSHA256'))
            mac.update(iv)
            mac.update(ciphertext)
            byte[] tag = mac.doFinal()
            encrypted = new byte[iv.length + ciphertext.length + tag.length]
            System.arraycopy(iv, 0, encrypted, 0, iv.length)
            System.arraycopy(ciphertext, 0, encrypted, iv.length, ciphertext.length)
            System.arraycopy(tag, 0, encrypted, iv.length + ciphertext.length, tag.length)
        } else {
            encrypted = cleartext.clone()
            for (int i = 0; i < encrypted.length; i++) {
                int mixed = encrypted[i] ^ key[i % key.length]
                encrypted[i] = (byte) (algorithm == 4 ? mixed + key[(i + 1) % key.length] + i : mixed)
            }
        }
        int padding = configuration.paddingBytes
        byte[] payload = new byte[encrypted.length + padding * 2]
        RANDOM.nextBytes(payload)
        System.arraycopy(encrypted, 0, payload, padding, encrypted.length)
        boolean bytesMode = configuration.storage == 'BYTES' && payload.length <= 4096
        if (bytesMode) {
            emitByteArray(method, payload)
            emitByteArray(method, key)
            pushInt(method, algorithm)
            pushInt(method, padding)
            method.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, 'decrypt', '([B[BII)Ljava/lang/String;', false)
        } else {
            emitString(method, Base64.encoder.encodeToString(payload))
            method.visitLdcInsn(Base64.encoder.encodeToString(key))
            pushInt(method, algorithm)
            pushInt(method, padding)
            method.visitMethodInsn(Opcodes.INVOKESTATIC, RUNTIME, 'decryptBase64', '(Ljava/lang/String;Ljava/lang/String;II)Ljava/lang/String;', false)
        }
    }

    private static void emitByteArray(MethodVisitor method, byte[] data) {
        pushInt(method, data.length)
        method.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_BYTE)
        for (int i = 0; i < data.length; i++) {
            method.visitInsn(Opcodes.DUP)
            pushInt(method, i)
            pushInt(method, data[i])
            method.visitInsn(Opcodes.BASTORE)
        }
    }

    private static void emitString(MethodVisitor method, String value) {
        if (value.length() <= 16000) {
            method.visitLdcInsn(value)
            return
        }
        method.visitTypeInsn(Opcodes.NEW, 'java/lang/StringBuilder')
        method.visitInsn(Opcodes.DUP)
        method.visitMethodInsn(Opcodes.INVOKESPECIAL, 'java/lang/StringBuilder', '<init>', '()V', false)
        for (int start = 0; start < value.length(); start += 16000) {
            method.visitLdcInsn(value.substring(start, Math.min(start + 16000, value.length())))
            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 'java/lang/StringBuilder', 'append', '(Ljava/lang/String;)Ljava/lang/StringBuilder;', false)
        }
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, 'java/lang/StringBuilder', 'toString', '()Ljava/lang/String;', false)
    }

    private static void pushInt(MethodVisitor method, int value) {
        if (value >= -1 && value <= 5) method.visitInsn(Opcodes.ICONST_0 + value)
        else if (value >= -128 && value <= 127) method.visitIntInsn(Opcodes.BIPUSH, value)
        else if (value >= -32768 && value <= 32767) method.visitIntInsn(Opcodes.SIPUSH, value)
        else method.visitLdcInsn(value)
    }

}
