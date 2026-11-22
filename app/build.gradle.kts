import java.net.URI
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.heysia.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.heysia.app"
        minSdk = 30          // Android 11+ (needed for background microphone). Your A55 runs Android 14+.
        versionCode = 10
        versionName = "1.0.0"
        targetSdk = 35

        // Phone (arm64) + Android Studio emulator (x86_64). Skips other CPUs to keep the APK small.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

/**
 * Downloads Sia's offline English speech model (Vosk, free, ~40 MB) once, and packs it into the APK.
 * The zip is cached in Gradle's home folder, so later builds don't download it again.
 * Nothing is committed to GitHub and the app never downloads anything at runtime.
 */
abstract class DownloadSpeechModel : DefaultTask() {
    @get:Input abstract val modelName: Property<String>
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val name = modelName.get()
        val zip = cacheDir.get().file("$name.zip").asFile
        if (!zip.exists()) {
            logger.lifecycle("Downloading speech model $name (one time only)...")
            zip.parentFile.mkdirs()
            val partial = File(zip.path + ".part")
            URI("https://alphacephei.com/vosk/models/$name.zip").toURL().openStream().use { input ->
                partial.outputStream().use { input.copyTo(it) }
            }
            partial.renameTo(zip)
        }

        // Unzip into assets/model-en-us/, dropping the zip's top-level folder.
        val target = outputDir.get().dir(ASSET_FOLDER).asFile
        target.deleteRecursively()
        ZipInputStream(zip.inputStream().buffered()).use { stream ->
            generateSequence { stream.nextEntry }.forEach { entry ->
                val relative = entry.name.substringAfter('/', "")
                if (relative.isEmpty() || entry.isDirectory) return@forEach
                val out = File(target, relative)
                require(out.canonicalPath.startsWith(target.canonicalPath)) { "Bad zip entry: ${entry.name}" }
                out.parentFile.mkdirs()
                out.outputStream().use { stream.copyTo(it) }
            }
        }
        // Vosk's StorageService uses this file to know when the bundled model changed.
        File(target, "uuid").writeText(name)
    }

    companion object {
        const val ASSET_FOLDER = "model-en-us"
    }
}

val downloadSpeechModel = tasks.register<DownloadSpeechModel>("downloadSpeechModel") {
    modelName.set("vosk-model-small-en-us-0.15")
    cacheDir.set(File(gradle.gradleUserHomeDir, "caches/sia-speech-models"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(downloadSpeechModel, DownloadSpeechModel::outputDir)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // Offline speech recognition (free, open source). Pulls in JNA automatically.
    implementation("com.alphacephei:vosk-android:0.3.47")

    testImplementation("junit:junit:4.13.2")
}
