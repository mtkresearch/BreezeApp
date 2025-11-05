import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.hilt.android.plugin)
}

android {
    namespace = "com.mtkresearch.breezeapp"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // Production release keystore
            // Store sensitive data in keystore.properties or environment variables for security
            val keystorePropertiesFile = rootProject.file("keystore.properties")

            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))

                storeFile = file(keystoreProperties.getProperty("storeFile")
                    ?: "${System.getProperty("user.home")}/Resource/android_key_mr")
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            } else {
                // Fallback to production keystore with environment variables
                storeFile = file(System.getProperty("KEYSTORE_FILE")
                    ?: "${System.getProperty("user.home")}/Resource/android_key_mr")
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: System.getProperty("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: System.getProperty("KEY_ALIAS") ?: "key0"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getProperty("KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.mtkresearch.breezeapp"
        minSdk = 35
        targetSdk = 35
        versionCode = 32
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Use production release keystore for release builds
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        // Disable lint fatal errors for release builds
        // This allows the build to complete even with lint warnings
        abortOnError = false

        // Optional: Create a lint report for review
        checkReleaseBuilds = true

        // Ignore specific issues that are false positives
        disable += setOf("ResAuto")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    // BreezeApp Engine SDK
    implementation("com.github.mtkresearch:BreezeApp-engine:EdgeAI-v0.1.7")
    
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // AndroidX Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Hilt for Dependency Injection
    implementation(libs.hilt.android)
    implementation(libs.androidx.fragment.ktx)
    kapt(libs.hilt.compiler)

    // Espresso / Rules / Runtime
    implementation(libs.androidx.junit.ktx)
    implementation(libs.androidx.runtime)
    implementation(libs.androidx.espresso.contrib)
    implementation(libs.androidx.rules)
    implementation(libs.androidx.espresso.intents)

    // ✅ Unit Testing (JUnit5 + Coroutine + Mock)
    testImplementation(libs.junit) // JUnit4 - 保留以防部分 lib 相依
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.fragment.testing)

    // Android Testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.fragment.testing)
    androidTestImplementation(libs.androidx.core)
    androidTestImplementation(libs.androidx.runner)
    androidTestImplementation(libs.androidx.recyclerview)
    androidTestImplementation(libs.core)
    androidTestImplementation(libs.core.ktx)
    androidTestImplementation(libs.androidx.junit.ktx)
    androidTestImplementation(libs.androidx.truth)
    androidTestUtil(libs.androidx.orchestrator)
    
    // Hilt Testing
    testImplementation(libs.hilt.android.testing)
    kaptTest(libs.hilt.compiler)
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
}

// ✅ 使用 JUnit5 測試平台
tasks.withType<Test> {
    useJUnitPlatform()
}