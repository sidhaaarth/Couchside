plugins {
    id("com.android.application")
}

android {
    namespace = "dev.sidk.scrubber.phone"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.sidk.scrubber.phone"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
        // 10.0.2.2 = the dev PC from the emulator; for a real Fire TV build with -PtvHost=<fire-tv-ip>.
        buildConfigField("String", "TV_HOST", "\"${providers.gradleProperty("tvHost").getOrElse("10.0.2.2")}\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.media3:media3-session:1.11.1")
}
