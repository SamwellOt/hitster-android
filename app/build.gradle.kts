import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.hitster.mobile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hitster.mobile"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.2.0"
        // Default sync server. Change here or in the app's settings screen.
        buildConfigField("String", "DEFAULT_SERVER_URL", "\"\"")
    }

    // Release signing: put a keystore.properties in the project root (git‑ignored) with
    //   storeFile=keystore/hitster-release.jks  storePassword=…  keyAlias=hitster  keyPassword=…
    // Without it, assembleRelease produces an unsigned APK.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 is kept off on purpose: the app cannot be exercised on a device from this environment,
            // and shrinking Java‑WebSocket / kotlinx.serialization untested is not worth the ~8 MB.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }


    // Official decks are bundled into the APK; the host phone deals cards from them.
    sourceSets["main"].assets.srcDirs("../catalog")
    androidResources { ignoreAssetsPattern = "!_cache.json:!review.csv:!.gitkeep" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // Lets the in‑app host (which uses android.util.Log) run in plain JVM unit tests.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    // In‑app game host (WebSocket server on the local network)
    implementation("org.java-websocket:Java-WebSocket:1.5.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.slf4j:slf4j-simple:2.0.13")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.0.20")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
