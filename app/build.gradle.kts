import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.myapp.expensetracker"
    compileSdk = 37

    val gitCommitHash = try {
        // Use rev-list -n 1 HEAD to ensure we get the commit hash even if HEAD is a tag
        val process = Runtime.getRuntime().exec("git rev-list -n 1 HEAD")
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        "unknown"
    }

    // ---------------------------------------------------------------------------
    // Version resolution
    //   CI:    APP_VERSION env var is set by the workflow to the git tag (e.g. "2.2.1").
    //          versionCode is auto-computed from semver so you never touch it manually.
    //   Local: falls back to libs.versions.toml values — Studio builds are unaffected.
    // ---------------------------------------------------------------------------
    val resolvedVersionName: String = System.getenv("APP_VERSION")
        ?.takeIf { it.isNotBlank() }
        ?: libs.versions.appVersion.get()

    // Auto-compute versionCode from semver: major*10000 + minor*100 + patch
    // e.g. "2.3.1" → 20301. Falls back to TOML value if parsing fails.
    val resolvedVersionCode: Int = run {
        val parts = resolvedVersionName.split(".")
        if (parts.size == 3) {
            try {
                parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
            } catch (_: NumberFormatException) {
                libs.versions.appVersionCode.get().toInt()
            }
        } else {
            libs.versions.appVersionCode.get().toInt()
        }
    }

    defaultConfig {
        applicationId = "com.myapp.expensetracker"
        minSdk = 31
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName

        buildConfigField("String", "GIT_COMMIT_HASH", "\"$gitCommitHash\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        val localProperties = Properties().apply {
            val localPropertiesFile = rootProject.file("local.properties")
            if (localPropertiesFile.exists()) {
                load(localPropertiesFile.inputStream())
            }
        }

        create("release") {
            val envKeystore = System.getenv("KEYSTORE_PATH")
            val keystoreFile =
                if (envKeystore != null) file(envKeystore) else file("C:\\Users\\Admin\\Desktop\\workspace\\AdnroidStudioKey\\key.jks")

            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias =
                    System.getenv("KEY_ALIAS") ?: localProperties.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Requires app/proguard-rules.pro to keep everything reached by
            // reflection — Gson models, WorkManager workers, MediaPipe JNI,
            // ML Kit and Fused Location. Without those, the app compiles and
            // installs fine but silently loses SMS detection, GPS and the AI.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// Compose compiler diagnostics — which composables can be skipped, and which
// classes Compose considers unstable (unstable state = the whole screen
// recomposes on any change).
//
// Off by default because it slows compilation. Generate with:
//   ./gradlew :app:compileReleaseKotlin -PcomposeMetrics=true
//
// Output in app/build/compose_compiler/:
//   *-module.json     — totals: skippable vs restartable composables
//   *-composables.txt — per-composable, with the reason it can't be skipped
//   *-classes.txt     — per-class stability verdict
composeCompiler {
    // Applied to every build, not just metric runs — it changes codegen.
    stabilityConfigurationFiles.add(
        layout.projectDirectory.file("compose_stability.conf")
    )

    if (project.findProperty("composeMetrics") == "true") {
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

// Room schema JSONs are committed so migrations can be diffed in review and
// exercised by MigrationTestHelper.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// The Apps Script and the SMS extraction rules live as plain files at the repo
// root — they are the single source of truth, fetched at runtime from the public
// repo so they can be updated without shipping a new APK. They are also copied
// into assets at build time as the offline fallback, which keeps exactly one
// copy in version control.
abstract class BundleRemoteResourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val target = outputDir.get().asFile
        target.deleteRecursively()
        target.mkdirs()
        sourceFiles.files.forEach { source ->
            source.copyTo(File(target, source.name), overwrite = true)
        }
    }
}

val bundleRemoteResources = tasks.register<BundleRemoteResourcesTask>("bundleRemoteResources") {
    sourceFiles.from(
        rootProject.file("scripts/apps-script.gs"),
        rootProject.file("rules/extraction-rules.json")
    )
}

// Registered through the Variant API rather than sourceSets.assets.srcDir():
// AGP then wires the task dependency into every consumer (asset merging, lint
// model generation, …). Declaring it only on merge*Assets left lint reading the
// directory without a declared dependency, which Gradle 9 fails the build over.
androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            bundleRemoteResources,
            BundleRemoteResourcesTask::outputDir
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.biometric)
    // Applies the baseline profile at install time.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.mlkit.entity.extraction)
    implementation(libs.mlkit.language.id)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.mediapipe.genai)

    // Supplies app/src/release/generated/baselineProfiles/
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}