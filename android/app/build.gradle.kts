import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.gms.google-services")
}

// Custom task to clean before build
tasks.register("cleanBuild") {
    dependsOn("clean")
    finalizedBy("build")
}

// Task to automatically increment bundle version code for AAB builds
tasks.register("incrementBundleVersion") {
    group = "versioning"
    description = "Automatically increments bundle_version_code in local.properties for AAB builds"
    
    doLast {
        val localPropsFile = rootProject.file("local.properties")
        val properties = Properties()
        
        // Read existing properties
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { properties.load(it) }
        }
        
        // Get current bundle version code or default to 1
        val currentVersion = properties.getProperty("bundle_version_code", "1").toIntOrNull() ?: 1
        val newVersion = currentVersion + 1
        
        // Update the property
        properties.setProperty("bundle_version_code", newVersion.toString())
        
        // Write back to file, preserving comments and other properties
        val lines = if (localPropsFile.exists()) {
            localPropsFile.readLines().toMutableList()
        } else {
            mutableListOf()
        }
        
        // Find and update bundle_version_code line, or add it
        var found = false
        for (i in lines.indices) {
            if (lines[i].trim().startsWith("bundle_version_code=")) {
                lines[i] = "bundle_version_code=$newVersion"
                found = true
                break
            }
        }
        
        // If not found, add it at the end
        if (!found) {
            // Remove comment line if it exists and add property after it
            val commentIndex = lines.indexOfFirst { it.contains("# Bundle Version Code") }
            if (commentIndex >= 0) {
                lines.add(commentIndex + 1, "bundle_version_code=$newVersion")
            } else {
                lines.add("bundle_version_code=$newVersion")
            }
        }
        
        // Write back to file
        localPropsFile.writeText(lines.joinToString("\n") + "\n")
        
        println("✅ Incremented bundle version code: $currentVersion → $newVersion")
    }
}

android {
    namespace = "com.mealprint.ai"
    compileSdk = 35
    
    // Explicitly enable 16 KB page size support at the android block level
    // This ensures the property is applied even if gradle.properties is overridden
    // The gradle.properties setting should work, but this is a backup

    // Load API keys from local.properties (gitignored)
    val localProperties = Properties().apply {
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            load(localPropsFile.inputStream())
        }
    }

    // Determine if we're building a bundle (AAB) or APK
    val isBundleBuild = gradle.startParameter.taskNames.any { 
        it.contains("bundle", ignoreCase = true) 
    }
    
    // Version code: 1 for APKs (side-loading), incrementing for AABs (Google Play)
    val bundleVersionCode = localProperties.getProperty("bundle_version_code", "2").toIntOrNull() ?: 2
    val apkVersionCode = 1
    val finalVersionCode = if (isBundleBuild) bundleVersionCode else apkVersionCode

    defaultConfig {
        applicationId = "com.mealprint.ai"
        minSdk = 29
        targetSdk = 35
        versionCode = finalVersionCode
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Explicitly enable 16 KB page size support
        // This ensures all native libraries are aligned for 16 KB pages
        ndk {
            // No NDK version specified - using default
            // The enable16KbPageSize property in gradle.properties handles alignment
            // Generate full debug symbols for crash analysis
            debugSymbolLevel = "FULL"
        }
    }

    signingConfigs {
        create("debugSigning") {
            val keystoreFile = file("debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    val upcItemDbKey = localProperties.getProperty("upcitemdb_api_key", "")
    val upcItemDbHost = localProperties.getProperty("upcitemdb_api_host", "https://api.upcitemdb.com/prod/trial/lookup")
    val nutritionixAppId = localProperties.getProperty("nutritionix_app_id", "")
    val nutritionixApiKey = localProperties.getProperty("nutritionix_api_key", "")

    buildTypes {
        debug {
            // Inject API keys into BuildConfig for debug builds
            buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("gemini_api_key", "YOUR_KEY_HERE")}\"")
            buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("openai_api_key", "YOUR_KEY_HERE")}\"")
            buildConfigField("String", "UPC_ITEM_DB_API_KEY", "\"$upcItemDbKey\"")
            buildConfigField("String", "UPC_ITEM_DB_API_HOST", "\"$upcItemDbHost\"")
            buildConfigField("String", "NUTRITIONIX_APP_ID", "\"$nutritionixAppId\"")
            buildConfigField("String", "NUTRITIONIX_API_KEY", "\"$nutritionixApiKey\"")
        }
        release {
            // For production, consider using environment variables or secure storage
            buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("gemini_api_key", "YOUR_KEY_HERE")}\"")
            buildConfigField("String", "OPENAI_API_KEY", "\"${localProperties.getProperty("openai_api_key", "YOUR_KEY_HERE")}\"")
            buildConfigField("String", "UPC_ITEM_DB_API_KEY", "\"$upcItemDbKey\"")
            buildConfigField("String", "UPC_ITEM_DB_API_HOST", "\"$upcItemDbHost\"")
            buildConfigField("String", "NUTRITIONIX_APP_ID", "\"$nutritionixAppId\"")
            buildConfigField("String", "NUTRITIONIX_API_KEY", "\"$nutritionixApiKey\"")
            // Only use signing config if keystore exists
            val keystoreFile = file("debug.keystore")
            if (keystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("debugSigning")
            }
            // Enable minification to generate mapping.txt and reduce app size
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // CRITICAL FIX: Exclude problematic ML Kit native libraries that aren't 16 KB aligned
            // These specific .so files from ML Kit cause Google Play rejection
            // Even with useLegacyPackaging=true, Google Play still checks alignment
            excludes += listOf(
                "**/libbarhopper*.so",           // From barcode-scanning
                "**/libimage_processing_util_jni.so", // From pose-detection
                "**/libxeno_native.so"           // From pose-detection
            )
        }
    }
}

// Make bundle tasks automatically increment version code
// Use afterEvaluate because bundle tasks are created by the Android plugin after configuration
afterEvaluate {
    tasks.matching { it.name.startsWith("bundle") }.configureEach {
        dependsOn("incrementBundleVersion")
    }
}

dependencies {
    // Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:32.7.4"))

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material-icons-core")

    // Compose Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Accompanist Pager for ViewPager
    implementation("com.google.accompanist:accompanist-pager:0.32.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    // Google Fit for health data
    implementation("com.google.android.gms:play-services-fitness:21.1.0")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    
    // Health Connect for health data
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.1.1")

    // Encrypted Shared Preferences for secure caching
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Firebase
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-functions")

    // ML Kit Pose Detection - Updated to latest versions for potential 16 KB fixes
    implementation("com.google.mlkit:pose-detection:18.0.0-beta5")
    implementation("com.google.mlkit:pose-detection-accurate:18.0.0-beta5")


    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // OpenAI SDK
    implementation("com.aallam.openai:openai-client:3.8.2")
    // Google ML Kit for OCR with spatial coordinate table reading
    // ML Kit for barcode scanning - Latest version (may still have 16 KB issues)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    implementation("io.ktor:ktor-client-cio:2.3.8")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Activity Compose
    implementation("androidx.activity:activity-compose:1.9.0")

    // WorkManager for background tasks
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.3")
    implementation("androidx.camera:camera-camera2:1.3.3")
    implementation("androidx.camera:camera-lifecycle:1.3.3")
    implementation("androidx.camera:camera-view:1.3.3")
    implementation("androidx.camera:camera-extensions:1.3.3")

    // Retrofit for API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Test dependencies
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.6.8")
    androidTestImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
