import com.android.build.api.dsl.ApplicationExtension
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Hook 侧源码指纹：用于判断 APK 更新后是否需要热重载。
// 仅对 hook 侧源码取指纹（排除 ui/ 子目录），纯 UI 修改不会改变指纹，
// 从而让 LSPosed 自动触发的 onHotReloading 返回 false 跳过热重载。
// 同一指纹同时写入 BuildConfig（hook 侧运行时上报"当前运行代码指纹"）
// 与 assets/hook_fingerprint.txt（onHotReloading 从新 APK 读取"新代码指纹"）。
// 范围含 .java/.kt 源码与 .aidl（binder 契约改动也需热重载），排除 ui/ 子目录。
fun computeHookFingerprint(): String {
    val hookRoot = layout.projectDirectory.dir("src/main/java/nep/timeline/cirno")
    val aidlRoot = layout.projectDirectory.dir("src/main/aidl/nep/timeline/cirno")
    val libreRoot = rootProject.layout.projectDirectory.dir("librekernel/src")
    val files = mutableListOf<Pair<String, java.io.File>>()

    fun collect(dir: java.io.File, relativeBase: String, excludeUi: Boolean) {
        if (!dir.isDirectory) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (excludeUi && child.name == "ui") continue
                collect(child, "$relativeBase/${child.name}", excludeUi)
            } else if (child.isFile && (child.extension == "java" || child.extension == "kt" || child.extension == "aidl")) {
                files.add("$relativeBase/${child.name}" to child)
            }
        }
    }
    collect(hookRoot.asFile, "cirno", excludeUi = true)
    collect(aidlRoot.asFile, "aidl", excludeUi = false)
    collect(libreRoot.asFile, "librekernel", excludeUi = false)
    if (files.isEmpty()) return "0".repeat(64)

    // 路径排序保证稳定，拼接 "相对路径\x00内容" 后整体 SHA-256
    val digest = MessageDigest.getInstance("SHA-256")
    for ((path, file) in files.sortedBy { it.first }) {
        val pathBytes = path.toByteArray(Charsets.UTF_8)
        digest.update(pathBytes)
        digest.update(0)
        digest.update(file.readBytes())
        digest.update(0)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

val hookFingerprint = computeHookFingerprint()

// asset 生成到 build 目录而非源码树，避免污染 git status。
val generatedAssetsDir = layout.buildDirectory.dir("generated/hookFingerprint/assets")

configure<ApplicationExtension> {
    namespace = "nep.timeline.cirno"
    compileSdk = 37
    val buildTime = SimpleDateFormat("MMddHHmm", Locale.getDefault()).format(Date())

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        versionCode = 8
        versionName = "${versionCode}-${buildTime}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val freezerType = "Cirno"

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
            buildConfigField("String", "FREEZER_TYPE", "\"$freezerType\"")
            buildConfigField("String", "HOOK_FINGERPRINT", "\"$hookFingerprint\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
            buildConfigField("String", "FREEZER_TYPE", "\"$freezerType\"")
            buildConfigField("String", "HOOK_FINGERPRINT", "\"$hookFingerprint\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    sourceSets["main"].assets.directories.add(generatedAssetsDir.get().asFile.absolutePath)
}

dependencies {
    implementation(project(":librekernel"))
    implementation(libs.gson)
    implementation(libs.commons.io)
    compileOnly(libs.api)
    implementation(libs.service)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.commons.lang3)
    implementation(libs.chrisbanes.haze)
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    implementation("androidx.navigation3:navigation3-runtime-android:1.1.4")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.37.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.5.0-alpha20")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-alpha20")
    implementation("com.kongzue.dialogx:DialogX:0.0.49")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.materialkolor)
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.9.3")
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val libsuVersion = "6.0.0"
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:service:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:io:$libsuVersion")
}

// 写入 asset 文件到 build 目录，与 BuildConfig.HOOK_FINGERPRINT 同源，
// 保证 onHotReloading 从新 APK 读取的指纹与 hook 侧上报给 UI 的指纹可比对。
val writeHookFingerprintAsset by tasks.registering {
    doLast {
        val dir = generatedAssetsDir.get().asFile
        dir.mkdirs()
        dir.resolve("hook_fingerprint.txt").writeText(hookFingerprint)
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(writeHookFingerprintAsset)
}
