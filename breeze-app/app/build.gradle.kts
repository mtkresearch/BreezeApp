import com.android.build.api.dsl.ProductFlavor

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.mtkresearch.breeze_app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mtkresearch.breeze_app"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        manifestPlaceholders["app_name"] = "BreezeApp"

        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
                cppFlags += "-fsanitize-hwaddress"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")

            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
                mappingFileUploadEnabled = true
            }
        }
        
        debug {
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
                mappingFileUploadEnabled = true
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

    // Testing
    testImplementation("junit:junit:${Versions.JUNIT}")
    androidTestImplementation("androidx.test.ext:junit:${Versions.ANDROID_JUNIT}")
    androidTestImplementation("androidx.test.espresso:espresso-core:${Versions.ESPRESSO}")

    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))
    
    // Add the dependencies for the Crashlytics NDK and Analytics libraries
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation("com.google.firebase:firebase-analytics")

    // Executorch dependencies
    implementation("com.facebook.fbjni:fbjni:${Versions.FBJNI}")
    implementation("com.google.code.gson:gson:${Versions.GSON}")
    implementation("com.facebook.soloader:soloader:${Versions.SOLOADER}")
    implementation(files("libs/executorch.aar"))
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