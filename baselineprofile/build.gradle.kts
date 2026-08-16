plugins {
    // No version: AGP is already on the classpath via :app.
    id("com.android.test")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.myapp.expensetracker.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 31
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Measures and profiles the real app.
    targetProjectPath = ":app"
}

baselineProfile {
    // Profiles are generated from a connected device or emulator, not in CI.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
