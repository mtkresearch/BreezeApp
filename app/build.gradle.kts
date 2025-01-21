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
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }

        // Set default app name in manifest placeholders
        manifestPlaceholders["app_name"] = "GAI Android"
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
        buildConfig = true  // Enable BuildConfig generation
        viewBinding = true  // Keep existing viewBinding
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

    // TODO: refactor this, currently used for MTK NPU support
    // externalNativeBuild {
    //     cmake {
    //         path = File("src/main/cpp/CMakeLists.txt")
    //     }
    // }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("llm") {
            dimension = "version"
            applicationIdSuffix = ".llm"
            versionNameSuffix = "-llm"
            resValue("string", "app_name", "GAI-LLM")
            buildConfigField("String", "GIT_BRANCH", "\"llm_cpu\"")
            manifestPlaceholders["file_provider_authority"] = 
                "com.mtkresearch.gai_android.llm.fileprovider"
        }
        create("vlm") {
            dimension = "version"
            applicationIdSuffix = ".vlm"
            versionNameSuffix = "-vlm"
            resValue("string", "app_name", "GAI-VLM")
            buildConfigField("String", "GIT_BRANCH", "\"vlm_cpu\"")
            manifestPlaceholders["file_provider_authority"] = 
                "com.mtkresearch.gai_android.vlm.fileprovider"
        }
        create("full") {
            dimension = "version"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
            resValue("string", "app_name", "GAI-Full")
            buildConfigField("String", "GIT_BRANCH", "\"main\"")
            manifestPlaceholders["file_provider_authority"] = 
                "com.mtkresearch.gai_android.full.fileprovider"
        }
        create("open_source") {
            dimension = "version"
            applicationIdSuffix = ".open_source"
            versionNameSuffix = "-open_source"
            resValue("string", "app_name", "GAI-open_source")
            buildConfigField("String", "GIT_BRANCH", "\"open_source\"")
            manifestPlaceholders["file_provider_authority"] = 
                "com.mtkresearch.gai_android.open_source.fileprovider"
        }
        create("et_vlm") {
            dimension = "version"
            applicationIdSuffix = ".et_vlm"
            versionNameSuffix = "-et_vlm"
            resValue("string", "app_name", "GAI-et_vlm")
            buildConfigField("String", "GIT_BRANCH", "\"et_vlm\"")
            manifestPlaceholders["file_provider_authority"] =
                "com.mtkresearch.gai_android.open_source.fileprovider"
        }
    }

//    sourceSets.getByName("main") {
//        jniLibs.setSrcDirs(listOf("src/main/libs"))
//    }

    dependencies {
        implementation(fileTree(mapOf(
            "dir" to "libs",
            "include" to listOf("*.jar", "*.aar")
        )))
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // Executorch
    implementation("com.facebook.fbjni:fbjni:0.5.1")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.facebook.soloader:soloader:0.10.5")
    implementation(files("libs/executorch.aar"))

    // Add explicit dependency for Sherpa native libraries
    implementation(fileTree(mapOf(
        "dir" to "../libs",
        "include" to listOf(
            "*.jar",
            "*.aar",
            "*.so"
        )
    )))
}

// Git Branch Switch Task
tasks.register("switchGitBranch") {
    doLast {
        val selectedFlavor = project.gradle.startParameter.taskRequests
            .flatMap { it.args }
            .firstOrNull { it.contains("assemble") && 
                (it.contains("Llm") || it.contains("Vlm") || 
                 it.contains("Full") || it.contains("OpenSource")) }
            ?.let { task ->
                when {
                    task.contains("Llm") -> "llm_cpu"
                    task.contains("Vlm") -> "vlm_cpu"
                    task.contains("Full") -> "main"
                    task.contains("OpenSource") -> "open_source"
                    else -> null
                }
            }

        selectedFlavor?.let {
            exec {
                commandLine("git", "checkout", it)
            }
        }
    }
}

// Make assemble tasks depend on switchGitBranch
tasks.whenTaskAdded {
    if (name.startsWith("assemble")) {
        dependsOn("switchGitBranch")
    }
}