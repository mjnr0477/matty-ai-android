package com.mattyai.assistant

import android.content.Context
import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

/**
 * Installs the bundled Vosk model into app-private storage on first use.
 * The model is packaged with Matty; no Android speech provider, language pack,
 * or first-run internet connection is required.
 */
object MattyOfflineSpeech {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var preparing = false
    private val waiting = mutableListOf<(Result<String>) -> Unit>()

    fun prepare(context: Context, callback: (Result<String>) -> Unit) {
        val app = context.applicationContext
        synchronized(this) {
            val ready = modelDirectory(app)
            if (isUsable(ready)) {
                main.post { callback(Result.success(ready.absolutePath)) }
                return
            }
            waiting.add(callback)
            if (preparing) return
            preparing = true
        }
        worker.execute {
            val result = runCatching {
                val destination = modelDirectory(app)
                destination.deleteRecursively()
                if (!destination.mkdirs()) error("Cannot create offline speech-model storage.")
                copyAssetFolder(app.assets, "offline_model", destination)
                if (!isUsable(destination)) {
                    destination.deleteRecursively()
                    error("Matty's bundled English speech model is incomplete. Reinstall the app and retry.")
                }
                destination.absolutePath
            }
            val callbacks: List<(Result<String>) -> Unit>
            synchronized(this) {
                callbacks = waiting.toList()
                waiting.clear()
                preparing = false
            }
            main.post { callbacks.forEach { it(result) } }
        }
    }

    private fun modelDirectory(context: Context): File =
        File(context.filesDir, "offline-speech/model")

    private fun copyAssetFolder(assets: AssetManager, assetPath: String, destination: File) {
        val entries = assets.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            return
        }
        if (!destination.exists() && !destination.mkdirs()) error("Cannot create offline model folder.")
        entries.forEach { name ->
            copyAssetFolder(assets, "$assetPath/$name", File(destination, name))
        }
    }

    private fun isUsable(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/model.conf").isFile &&
            File(directory, "graph/phones/word_boundary.int").isFile
}
