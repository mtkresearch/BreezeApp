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
    namespace = "com.mtkresearch.breeze_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mtkresearch.breeze_app"
        minSdk = 33
        targetSdk = 35
        versionCode = 9
        versionName = "0.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        manifestPlaceholders["app_name"] = "BreezeApp"

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
        
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

    flavorDimensions += "version"
    productFlavors {
        create("breeze") {
            dimension = "version"
            applicationIdSuffix = ".breeze"
            versionNameSuffix = "-breeze"
            resValue("string", "app_name", "BreezeApp")
            buildConfigField("String", "GIT_BRANCH", "\"release/0.1\"")
            manifestPlaceholders["file_provider_authority"] =
                "com.mtkresearch.breeze_app.breeze.fileprovider"
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
}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:${Versions.CORE_KTX}")
    implementation("androidx.appcompat:appcompat:${Versions.APPCOMPAT}")
    implementation("com.google.android.material:material:${Versions.MATERIAL}")
    implementation("androidx.constraintlayout:constraintlayout:${Versions.CONSTRAINT_LAYOUT}")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.COROUTINES}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.COROUTINES}")
    implementation(libs.play.services.measurement.api)
    implementation(libs.firebase.crashlytics)

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