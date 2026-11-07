import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Your own keystore, never committed. See key.properties.example.
val keyProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// The backend. The same one as the React Native and Flutter apps: ./gradlew assembleRelease -PapiUrl=https://api.example.com
val apiUrl = (findProperty("apiUrl") as String?) ?: "https://demo-api-react-native.voltkit.in"

android {
    namespace = "in.voltkit.livedemo.kotlin"
    compileSdk = 36

    defaultConfig {
        // Its own id, so it installs next to VoltKit Native and VoltKit Flutter.
        applicationId = "in.voltkit.livedemo.kotlin"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
        // Device tests (tools/e2e): names every control for UI Automator. Off in a normal build.
        buildConfigField("boolean", "E2E", (findProperty("e2e") ?: "false").toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (keyProperties.containsKey("storeFile")) {
            create("release") {
                storeFile = file(keyProperties.getProperty("storeFile"))
                storePassword = keyProperties.getProperty("storePassword")
                keyAlias = keyProperties.getProperty("keyAlias")
                keyPassword = keyProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // With no key.properties the build is unsigned, rather than quietly signed with a debug key.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("in.voltkit:voltkit-rtc:0.9.16")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.4")
    implementation("androidx.compose.ui:ui:1.10.5")
    implementation("androidx.compose.foundation:foundation:1.10.5")
    implementation("androidx.compose.animation:animation:1.10.5")
    implementation("androidx.compose.material:material:1.10.5") // pull to refresh only

    // REST, on the same OkHttp the SDK brings.
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    // The SVGA player from the SVGA project. Its old okio is lined up with OkHttp's below.
    implementation("com.github.yyued:SVGAPlayer-Android:2.6.1")
    implementation("com.squareup.okio:okio:3.16.0")

    testImplementation("junit:junit:4.13.2")
}
