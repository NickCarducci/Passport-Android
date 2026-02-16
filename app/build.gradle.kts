import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.max

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

val dateStamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
val buildSequence = System.getenv("RUN_NUMBER")
    ?: System.getenv("BUILD_NUMBER")
    ?: System.getenv("GITHUB_RUN_NUMBER")
    ?: "1"
val buildNumber = buildSequence.toIntOrNull() ?: 1
val digits = max(2, buildNumber.toString().length)
val computedVersionCode = (dateStamp.toInt() * Math.pow(10.0, digits.toDouble()).toInt()) + buildNumber

android {
    namespace = "com.sayists.passport"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sayists.passport"
        minSdk = 24
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.guava)
    implementation(platform(libs.firebase.bom))
    implementation(libs.play.integrity)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

configurations.all {
    resolutionStrategy.force("com.google.guava:guava:32.1.3-android")
}
