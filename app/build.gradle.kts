import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// MediaPipe pose models are downloaded at build time (not committed) into a generated assets folder.
// Pinned to model version 1 so builds are reproducible.
val poseModels = mapOf(
    "pose_landmarker_lite.task" to
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task",
    "pose_landmarker_full.task" to
        "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/1/pose_landmarker_full.task",
)
val modelAssetsDir = layout.buildDirectory.dir("generated/poseModels")
val unsignedRelease = providers.gradleProperty("unsignedRelease").map(String::toBoolean).orElse(false)

val downloadPoseModels by tasks.registering {
    description = "Downloads the MediaPipe pose landmarker models into generated assets."
    val outDir = modelAssetsDir
    outputs.dir(outDir)
    doLast {
        val dir = outDir.get().asFile.apply { mkdirs() }
        poseModels.forEach { (name, url) ->
            val target = File(dir, name)
            if (target.exists() && target.length() > 0) return@forEach
            val partial = File(dir, "$name.part")
            try {
                URI(url).toURL().openStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                partial.delete()
                throw GradleException("Could not download pose model $name from $url: ${e.message}", e)
            }
            if (!partial.renameTo(target)) throw GradleException("Could not move downloaded model into $target")
            logger.lifecycle("Downloaded $name (${target.length() / 1024} KB)")
        }
    }
}

android {
    namespace = "com.activekids"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.activekids"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Pixel 8 Pro (and every modern phone) is arm64; dropping other ABIs shrinks MediaPipe's native libs.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Local builds use the existing Android signing key. CI signs the unsigned APK separately.
            if (!unsignedRelease.get()) signingConfig = signingConfigs.getByName("debug")
        }
    }

    sourceSets["main"].assets.srcDir(modelAssetsDir)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    androidResources { noCompress += "task" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.named("preBuild") { dependsOn(downloadPoseModels) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")

    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")

    implementation("com.google.mediapipe:tasks-vision:0.10.26")

    testImplementation("junit:junit:4.13.2")
}
