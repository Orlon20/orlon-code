plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.mobilecode.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.mobilecode.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    sourceSets["test"].java.srcDir("src/test/kotlin")
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs { useLegacyPackaging = true } }
    buildTypes { release { isMinifyEnabled = false } }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    testImplementation("junit:junit:4.13.2")
}
