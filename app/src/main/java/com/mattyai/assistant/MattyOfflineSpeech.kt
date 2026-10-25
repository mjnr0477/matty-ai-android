package com.mattyai.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/**
 * App-owned offline speech model manager. Recognition runs locally through Vosk;
 * the model is downloaded once inside Matty and reused without network access.
 */
object MattyOfflineSpeech {
    private const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val MAX_ARCHIVE_BYTES = 120L * 1024L * 1024L
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
                val root = File(app.filesDir, "offline-speech")
                if (!root.exists() && !root.mkdirs()) error("Cannot create speech-model storage.")
                val archive = File(root, "model.zip.part")
                val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 20000
                    readTimeout = 120000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "MattyAI-Android")
                }
                try {
                    connection.connect()
                    val code = connection.responseCode
                    if (code !in 200..299) error("Speech model download failed (HTTP $code). Connect to the internet once and retry.")
                    val length = connection.contentLengthLong
                    if (length > MAX_ARCHIVE_BYTES) error("Speech model archive is unexpectedly large.")
                    BufferedInputStream(connection.inputStream).use { input ->
                        FileOutputStream(archive).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                if (total > MAX_ARCHIVE_BYTES) error("Speech model archive exceeds the safe size limit.")
                                output.write(buffer, 0, count)
                            }
                            output.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                val destination = File(root, "model")
                if (destination.exists()) destination.deleteRecursively()
                if (!destination.mkdirs()) error("Cannot prepare speech model folder.")
                val canonicalRoot = destination.canonicalFile
                ZipInputStream(BufferedInputStream(archive.inputStream())).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val output = File(destination, entry.name).canonicalFile
                        if (output != canonicalRoot && !output.path.startsWith(canonicalRoot.path + File.separator)) {
                            error("Invalid path in speech model archive.")
                        }
                        if (entry.isDirectory) {
                            if (!output.exists() && !output.mkdirs()) error("Cannot extract speech model.")
                        } else {
                            output.parentFile?.let { if (!it.exists() && !it.mkdirs()) error("Cannot extract speech model.") }
                            BufferedOutputStream(FileOutputStream(output)).use { out ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val count = zip.read(buffer)
                                    if (count < 0) break
                                    out.write(buffer, 0, count)
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                archive.delete()
                val model = File(destination, MODEL_NAME)
                if (!isUsable(model)) error("The downloaded speech model is incomplete. Please retry the download.")
                model.absolutePath
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
        File(File(context.filesDir, "offline-speech/model"), MODEL_NAME)

    private fun isUsable(directory: File): Boolean =
        File(directory, "am/final.mdl").isFile &&
            File(directory, "conf/model.conf").isFile &&
            File(directory, "graph/phones/word_boundary.int").isFile
}
