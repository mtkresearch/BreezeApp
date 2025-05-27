import com.android.build.api.dsl.ProductFlavor

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Firebase plugins are applied conditionally below
}

// Check if google-services.json exists
val googleServicesFile = file("google-services.json")
val hasGoogleServices = googleServicesFile.exists()

// Apply Firebase plugins if google-services.json exists
if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.mtkresearch.breezeapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mtkresearch.breezeapp"
        minSdk = 33
        targetSdk = 35
        versionCode = 22
        versionName = "1.0.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        manifestPlaceholders["app_name"] = "BreezeApp"
        manifestPlaceholders["file_provider_authority"] = "com.mtkresearch.breezeapp.fileprovider"
        
        // Add a BuildConfig field to indicate Firebase presence
        buildConfigField("Boolean", "USE_FIREBASE", hasGoogleServices.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")

            if (hasGoogleServices) {
                configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    mappingFileUploadEnabled = true
                }
            }
            
            // Add full debug symbols for release builds to better diagnose crashes
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        
        debug {
            if (hasGoogleServices) {
                configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                    nativeSymbolUploadEnabled = true
                    mappingFileUploadEnabled = true
                }
            }
            
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    // 必須定義一個或多個 flavorDimensions
    flavorDimensions += "environment" // 可以自定義維度名稱，例如 "type", "feature", "version" 等

    productFlavors {
        // 'serviceMockup' Flavor
        create("mockup") {
            dimension = "environment" // 指定它屬於哪個維度
            applicationIdSuffix = ".mockup" // 避免與其他版本衝突
            versionNameSuffix = "-mockup" // 版本名稱後綴
            // 可以添加 Service 團隊專用的配置，例如模擬 API 端點
            buildConfigField("String", "SERVICE_ENDPOINT", "\"http://mock.api.example.com/service\"")
            // 你也可以在這裡設置一個標誌，讓 Service 知道它是在模擬模式下運行
            buildConfigField("Boolean", "IS_MOCK_MODE", "true")
        }

        // 你可能還會想定義一個 'production' 或 'full' Flavor 來表示正式版本
        // 這樣在沒有特殊需求的環境下，可以構建一個不帶任何特定 Flavor 後綴的版本
        create("production") {
            dimension = "environment"
            // 不需要特別設置 applicationIdSuffix 和 versionNameSuffix
            // 因為它會使用 defaultConfig 中的值
            buildConfigField("String", "SERVICE_ENDPOINT", "\"http://api.example.com/service\"")
            buildConfigField("Boolean", "IS_MOCK_MODE", "false")
        }
    }
    // --- 新增 Product Flavor 區塊結束 ---

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        languageVersion = "2.0"
        apiVersion = "2.0"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
            jniLibs.srcDirs("libs")
        }
        getByName("mockup"){
            java.srcDirs("src/mockup/java")

        }
        getByName("production") {
            java.srcDirs("src/service/java")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            
            // Handle duplicate .so libraries
            pickFirsts += listOf(
                "**/libc++_shared.so",
                "**/libfbjni.so"
            )
        }
    }
}

// Version constants
object Versions {
    const val CORE_KTX = "1.12.0"
    const val APPCOMPAT = "1.6.1"
    const val MATERIAL = "1.11.0"
    const val CONSTRAINT_LAYOUT = "2.1.4"
    const val COROUTINES = "1.8.0"
    const val JUNIT = "4.13.2"
    const val ANDROID_JUNIT = "1.1.5"
    const val ESPRESSO = "3.5.1"
    const val FBJNI = "0.5.1"
    const val GSON = "2.8.6"
    const val SOLOADER = "0.10.5"
    const val KOTLIN = "2.0.0"
    const val PREFERENCE = "1.2.1"
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:${Versions.CORE_KTX}")
    implementation("androidx.appcompat:appcompat:${Versions.APPCOMPAT}")
    implementation("com.google.android.material:material:${Versions.MATERIAL}")
    implementation("androidx.constraintlayout:constraintlayout:${Versions.CONSTRAINT_LAYOUT}")
    implementation("androidx.preference:preference:${Versions.PREFERENCE}")
    implementation("androidx.preference:preference-ktx:${Versions.PREFERENCE}")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINES}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}")

    // Testing
    testImplementation("junit:junit:${Versions.JUNIT}")
    androidTestImplementation("androidx.test.ext:junit:${Versions.ANDROID_JUNIT}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${Versions.ESPRESSO}")

    // Firebase dependencies only if google-services.json exists
    if (hasGoogleServices) {
        // Import the BoM for the Firebase platform
        implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
        
        // Add the dependencies for the Crashlytics NDK and Analytics libraries
        implementation("com.google.firebase:firebase-crashlytics-ndk")
        implementation("com.google.firebase:firebase-analytics")
    }

    // Executorch dependencies
    implementation("com.facebook.fbjni:fbjni:${Versions.FBJNI}")
    implementation("com.google.code.gson:gson:${Versions.GSON}")
    implementation("com.facebook.soloader:soloader:${Versions.SOLOADER}")
    implementation(files("libs/executorch.aar"))

//    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")
}

// Force specific versions for compatibility with Kotlin 2.0.0
configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-common:2.0.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.0.0")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0")
    }
}