plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "space.zenithw.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "space.zenithw.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 2001000
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        getByName("debug") { storeFile = rootProject.file(".signing/preview.keystore") }
        create("release") {
            storeFile = rootProject.file(".signing/preview.keystore")
            storePassword = "android"
            keyAlias = "AndroidDebugKey"
            keyPassword = "android"
            enableV2Signing = true
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            resValue("string", "app_name", "ZenithW 2.1 Preview")
        }
        release {
            applicationIdSuffix = ".stable"
            signingConfig = signingConfigs.getByName("release")
            resValue("string", "app_name", "Zenith")
            isMinifyEnabled = false
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    testBuildType = "release"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { jniLibs.useLegacyPackaging = true }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:aria2c:0.18.1")
}

// Keep preview installs updateable with a local key, never checked into Git.
val previewKey=rootProject.file(".signing/preview.keystore")
val generatePreviewKey=tasks.register<Exec>("generatePreviewKey") {
    onlyIf { !previewKey.exists() }
    doFirst { previewKey.parentFile.mkdirs() }
    val executable=if(System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool"
    commandLine(File(System.getProperty("java.home"),"bin/$executable").absolutePath,
        "-genkeypair","-keystore",previewKey.absolutePath,"-storepass","android","-keypass","android",
        "-alias","AndroidDebugKey","-dname","CN=ZenithW Preview, O=ZenithW, C=TR",
        "-keyalg","RSA","-keysize","2048","-validity","10000","-noprompt")
}
tasks.matching { it.name=="validateSigningDebug" }.configureEach { dependsOn(generatePreviewKey) }
