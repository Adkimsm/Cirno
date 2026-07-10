import com.android.build.api.dsl.ApplicationExtension
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.gradle.kotlin.dsl.configure

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
            buildConfigField("String", "FREEZER_TYPE", "\"$freezerType\"")
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
    implementation("androidx.navigation3:navigation3-runtime:1.1.1")
    implementation("androidx.navigation3:navigation3-runtime-android:1.1.1")
    implementation("androidx.navigationevent:navigationevent-compose:1.0.0-alpha10")
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("com.google.accompanist:accompanist-drawablepainter:0.37.3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.material3:material3:1.5.0-alpha20")
    implementation("com.kongzue.dialogx:DialogX:0.0.49")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.materialkolor)
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.2")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.2")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.2")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.9.2")
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    val libsuVersion = "6.0.0"
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:service:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:io:$libsuVersion")
}
