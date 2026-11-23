plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val propVersionName = (project.findProperty("versionName") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0.0"
val propVersionCode = (project.findProperty("versionCode") as String?)?.trim()?.toIntOrNull() ?: 10000
val propGithubRepo = (project.findProperty("githubRepo") as String?)?.trim()?.takeIf { it.isNotEmpty() }
    ?: System.getenv("GITHUB_REPOSITORY")?.takeIf { it.isNotBlank() }
    ?: "NguyenHieu2711/obsidian-quicknotes"

val defaultKeystore = file("../keystore/sideload-release.jks")
val keystoreFilePath = (project.findProperty("keystorePath") as String?)
    ?: System.getenv("KEYSTORE_PATH")
    ?: defaultKeystore.takeIf { it.exists() }?.absolutePath
val keystorePwd = (project.findProperty("keystorePassword") as String?)
    ?: System.getenv("KEYSTORE_PASSWORD")
    ?: "quicknotes2026"
val keyAliasValue = (project.findProperty("keyAlias") as String?)
    ?: System.getenv("KEY_ALIAS")
    ?: "quicknotes"
val keyPwd = (project.findProperty("keyPassword") as String?)
    ?: System.getenv("KEY_PASSWORD")
    ?: "quicknotes2026"

val hasReleaseKeystore = !keystoreFilePath.isNullOrBlank() &&
    file(keystoreFilePath).exists() &&
    !keystorePwd.isNullOrBlank() &&
    !keyAliasValue.isNullOrBlank() &&
    !keyPwd.isNullOrBlank()

android {
    namespace = "com.nguyenhieu.quicknotes"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nguyenhieu.quicknotes"
        minSdk = 26
        targetSdk = 34
        versionCode = propVersionCode
        versionName = propVersionName

        buildConfigField("String", "GITHUB_REPO", "\"$propGithubRepo\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreFilePath!!)
                storePassword = keystorePwd
                keyAlias = keyAliasValue
                keyPassword = keyPwd
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "../../static")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
