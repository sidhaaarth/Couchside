plugins {
    id("com.android.application")
}

// Charade (1963, public domain in the US). Override with -PvideoUrl=... to play a local copy.
val videoUrl = providers.gradleProperty("videoUrl")
    .getOrElse("https://archive.org/download/batvwi-Charade_1963/Charade_1963.mp4")
// Recap service URL + shared secret come from the git-ignored repo-root .env (written by recap/deploy.ps1);
// -PrecapUrl / -PrecapToken override it.
val dotenv = rootProject.file(".env").takeIf { it.exists() }?.readLines().orEmpty()
    .mapNotNull { line -> line.trim().takeIf { !it.startsWith("#") && "=" in it }?.split("=", limit = 2) }
    .associate { (k, v) -> k.trim() to v.trim() }
val recapUrl = providers.gradleProperty("recapUrl").orNull ?: dotenv["RECAP_URL"] ?: ""
val recapToken = providers.gradleProperty("recapToken").orNull ?: dotenv["RECAP_TOKEN"] ?: ""

android {
    namespace = "dev.sidk.scrubber.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.sidk.scrubber.tv"
        minSdk = 28 // Fire OS 7
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
        buildConfigField("String", "VIDEO_URL", "\"$videoUrl\"")
        buildConfigField("String", "RECAP_URL", "\"$recapUrl\"")
        buildConfigField("String", "RECAP_TOKEN", "\"$recapToken\"")
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    testImplementation("junit:junit:4.13.2")
}
