import com.android.build.api.dsl.ProductFlavor

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mtkresearch.gai_android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mtkresearch.gai_android"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        manifestPlaceholders["app_name"] = "Breeze2-demo"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }

    sourceSets {
        getByName("main") {
            java {
                srcDirs("src/main/java")
            }
            jniLibs {
                srcDirs("libs")
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("breeze") {
            dimension = "version"
            applicationIdSuffix = ".breeze"
            versionNameSuffix = "-breeze"
            resValue("string", "app_name", "Breeze2-demo")
            buildConfigField("String", "GIT_BRANCH", "\"release/0.1\"")
            manifestPlaceholders["file_provider_authority"] = 
                "com.mtkresearch.gai_android.breeze.fileprovider"
        }
    }
}

// Version constants
object Versions {
    const val CORE_KTX = "1.12.0"
    const val APPCOMPAT = "1.6.1"
    const val MATERIAL = "1.11.0"
    const val CONSTRAINT_LAYOUT = "2.1.4"
    const val COROUTINES = "1.7.3"
    const val JUNIT = "4.13.2"
    const val ANDROID_JUNIT = "1.1.5"
    const val ESPRESSO = "3.5.1"
    const val FBJNI = "0.5.1"
    const val GSON = "2.8.6"
    const val SOLOADER = "0.10.5"
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

    // Testing
    testImplementation("junit:junit:${Versions.JUNIT}")
    androidTestImplementation("androidx.test.ext:junit:${Versions.ANDROID_JUNIT}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${Versions.ESPRESSO}")

    // Executorch dependencies
    implementation("com.facebook.fbjni:fbjni:${Versions.FBJNI}")
    implementation("com.google.code.gson:gson:${Versions.GSON}")
    implementation("com.facebook.soloader:soloader:${Versions.SOLOADER}")
    implementation(files("libs/executorch.aar"))
}