import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val offlineSpeechAssetsDir = layout.buildDirectory.dir("generated/voskAssets")
val prepareOfflineSpeechModel by tasks.registering {
    outputs.dir(offlineSpeechAssetsDir)
    doLast {
        val assetsRoot = offlineSpeechAssetsDir.get().asFile
        val modelOutput = File(assetsRoot, "offline_model")
        if (File(modelOutput, "am/final.mdl").isFile &&
            File(modelOutput, "conf/model.conf").isFile) return@doLast

        assetsRoot.deleteRecursively()
        if (!modelOutput.mkdirs()) error("Cannot create generated offline speech assets.")
        val archive = File(temporaryDir, "vosk-model-small-en-us-0.15.zip")
        val connection = (URL("https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 20000
            readTimeout = 120000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MattyAI-Android-Build")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("Could not download the official Vosk English model (HTTP ${connection.responseCode}).")
            }
            if (connection.contentLengthLong > 120L * 1024L * 1024L) {
                error("Vosk model archive exceeded the configured size limit.")
            }
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 120L * 1024L * 1024L) error("Vosk model archive exceeded the configured size limit.")
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        val prefix = "vosk-model-small-en-us-0.15/"
        val safeRoot = modelOutput.canonicalFile
        ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.startsWith(prefix)) {
                    val relative = entry.name.removePrefix(prefix)
                    if (relative.isNotEmpty()) {
                        val output = File(modelOutput, relative).canonicalFile
                        if (output != safeRoot && !output.path.startsWith(safeRoot.path + File.separator)) {
                            error("Invalid path in Vosk model archive.")
                        }
                        if (entry.isDirectory) {
                            if (!output.exists() && !output.mkdirs()) error("Cannot extract Vosk model directory.")
                        } else {
                            output.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Cannot extract Vosk model file.") }
                            BufferedOutputStream(FileOutputStream(output)).use { out ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    out.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        archive.delete()
        if (!File(modelOutput, "am/final.mdl").isFile ||
            !File(modelOutput, "conf/model.conf").isFile ||
            !File(modelOutput, "graph/phones/word_boundary.int").isFile) {
            error("Downloaded Vosk model is incomplete.")
        }
    }
}

val releaseStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
val releaseSigningConfigured = !releaseStorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    sourceSets.getByName("main").assets.srcDir(offlineSpeechAssetsDir)
    namespace = "com.mattyai.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mattyai.assistant"
        minSdk = 31
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.0"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions { unitTests.isIncludeAndroidResources = false }
}

tasks.named("preBuild").configure { dependsOn(prepareOfflineSpeechModel) }

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.75")
    testImplementation("junit:junit:4.13.2")
}
