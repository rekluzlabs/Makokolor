package com.rekluzlabs.makokolor.engine

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

data class ModelFile(
    val name: String,
    val url: String,
    val minSize: Long = 0,
)

fun downloadModel(
    model: ModelFile,
    destinationDir: File,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
) {
    val client = OkHttpClient()
    val request = Request.Builder().url(model.url).build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("Failed to download: ${response.code}")

        val body = response.body
        val contentLength = body?.contentLength() ?: -1L

        val tempFile = File(destinationDir, "${model.name}.tmp")
        val finalFile = File(destinationDir, model.name)

        body?.byteStream()?.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead: Long = 0

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    onProgress(totalRead, contentLength)
                }
                output.flush()
            }
        }

        if (tempFile.length() >= model.minSize) {
            tempFile.renameTo(finalFile)
        } else {
            tempFile.delete()
            throw Exception("Download incomplete or file too small.")
        }
    }
}

object ModelDownloader {

    val MODELS = listOf(
        ModelFile(
            name = "realesrgan-x4.onnx",
            url = "https://github.com/rekluzlabs/Makokolor/releases/download/realesrganx4/realesrgan-x4.onnx",
            minSize = 60_000_000
        ),
        ModelFile(
            name = "deoldify.onnx",
            url = "https://github.com/rekluzlabs/Makokolor/releases/download/deoldify/deoldify_stable.onnx",
            minSize = 150_000_000
        ),
        ModelFile(
            name = "codeformer.onnx",
            url = "https://github.com/rekluzlabs/Makokolor/releases/download/codeformer/codeformer.onnx",
            minSize = 300_000_000
        ),
        ModelFile(
            name = "scunet.onnx",
            url = "https://github.com/rekluzlabs/Makokolor/releases/download/scunet/SCUNet-PSNR.onnx",
            minSize = 90_000_000
        )
    )

    fun modelsDirectory(context: Context): File {
        val dir = File(context.filesDir, "models")
        dir.mkdirs()
        return dir
    }

    fun areModelsDownloaded(context: Context): Boolean {
        val dir = modelsDirectory(context)
        return MODELS.all { model ->
            val file = File(dir, model.name)
            file.exists() && file.length() >= model.minSize
        }
    }

    fun getMissingModels(context: Context): List<ModelFile> {
        val dir = modelsDirectory(context)
        return MODELS.filter { model ->
            val file = File(dir, model.name)
            !file.exists() || file.length() < model.minSize
        }
    }

    fun getModelPaths(context: Context): Map<String, String> {
        val dir = modelsDirectory(context)
        return MODELS.associate { model ->
            model.name to File(dir, model.name).absolutePath
        }
    }

    suspend fun downloadModels(
        context: Context,
        onProgress: (downloadedBytes: Long, totalBytes: Long, modelIndex: Int, modelCount: Int) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val missing = getMissingModels(context)
            if (missing.isEmpty()) {
                return@withContext Result.success(Unit)
            }

            val dir = modelsDirectory(context)
            val totalModels = missing.size

            for ((index, model) in missing.withIndex()) {
                Timber.i("Downloading ${model.name} from ${model.url}")
                onProgress(0, 0, index, totalModels)

                try {
                    downloadModel(model, dir) { downloaded, total ->
                        onProgress(downloaded, total, index, totalModels)
                    }
                } catch (e: Exception) {
                    throw Exception("Failed to download ${model.name}: ${e.message}")
                }

                Timber.i("Downloaded ${model.name}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Model download failed")
            Result.failure(e)
        }
    }
}
