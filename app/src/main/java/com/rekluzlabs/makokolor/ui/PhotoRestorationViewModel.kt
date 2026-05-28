package com.rekluzlabs.makokolor.ui

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rekluzlabs.makokolor.engine.ModelDownloader
import com.rekluzlabs.makokolor.engine.PhotoRestorationEngine
import com.rekluzlabs.makokolor.engine.RestorationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class DownloadState {
    INITIAL,
    CHECKING,
    NEEDS_DOWNLOAD,
    DOWNLOADING,
    LOADING,
    READY,
    FAILED,
}

data class RestorationUiState(
    val selectedImageUri: Uri? = null,
    val restoredBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val progressText: String = "",
    val error: String? = null,
    val processingTimeMs: Long? = null,
    val downloadState: DownloadState = DownloadState.INITIAL,
    val downloadProgress: Float = 0f,
    val downloadProgressText: String = "",
    val useColorization: Boolean = true,
    val upscaleFactor: Int = 2,
    val faceStrength: Float = 1.0f,
    val denoiseLevel: Float = 0.5f,
    val faceFastMode: Boolean = false,
    val colorVibrancy: Float = 1.0f,
    val useAiDenoise: Boolean = true,
    val colorRenderFactor: Int = 24,
    val showSaveConfirmation: Boolean = false,
    val savePath: String = "",
    val savedImageUri: Uri? = null,
    val isAdvancedMode: Boolean = false,
    val previewBitmap: Bitmap? = null,
)

class PhotoRestorationViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PhotoRestorationEngine(application)

    private val _uiState = MutableStateFlow(RestorationUiState())
    val uiState: StateFlow<RestorationUiState> = _uiState.asStateFlow()

    init {
        // Models will be checked when the user taps the load button
    }

    fun checkModels() {
        _uiState.value = _uiState.value.copy(downloadState = DownloadState.CHECKING)
        viewModelScope.launch {
            val ready = engine.areModelsReady()
            if (ready) {
                loadModels()
            } else {
                _uiState.value = _uiState.value.copy(
                    downloadState = DownloadState.NEEDS_DOWNLOAD,
                    progressText = "AI models not found",
                )
            }
        }
    }

    private fun loadModels() {
        _uiState.value = _uiState.value.copy(downloadState = DownloadState.LOADING)
        viewModelScope.launch {
            val result = engine.loadModels()
            result.onSuccess {
                _uiState.value = _uiState.value.copy(downloadState = DownloadState.READY)
                Timber.i("Models loaded successfully")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    downloadState = DownloadState.FAILED,
                    error = "Failed to load AI models: ${e.message}",
                )
                Timber.e(e, "Model loading failed")
            }
        }
    }

    fun startDownload() {
        val app = getApplication<Application>()
        _uiState.value = _uiState.value.copy(
            downloadState = DownloadState.DOWNLOADING,
            downloadProgress = 0f,
            downloadProgressText = "Starting download...",
            error = null,
        )

        viewModelScope.launch {
            val result = ModelDownloader.downloadModels(app) { downloaded, total, modelIndex, modelCount ->
                val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                val overallProgress = if (total > 0) (modelIndex + progress) / modelCount else 0f
                val progressText = if (total == -1L) {
                    "Downloading model ${modelIndex + 1} of $modelCount (${downloaded / 1024 / 1024}MB)"
                } else {
                    "Downloading model ${modelIndex + 1} of $modelCount (${downloaded / 1024 / 1024}MB / ${total / 1024 / 1024}MB)"
                }
                _uiState.value = _uiState.value.copy(
                    downloadProgress = overallProgress,
                    downloadProgressText = progressText,
                )
            }

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    downloadProgress = 1f,
                    downloadProgressText = "Models downloaded! Loading...",
                )
                loadModels()
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    downloadState = DownloadState.FAILED,
                    error = "Download failed: ${e.message}. Check your internet connection and try again.",
                )
                Timber.e(e, "Model download failed")
            }
        }
    }

    fun toggleColorization(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(useColorization = enabled)
    }

    fun setUpscaleFactor(factor: Int) {
        _uiState.value = _uiState.value.copy(upscaleFactor = factor)
    }

    fun setFaceStrength(strength: Float) {
        _uiState.value = _uiState.value.copy(faceStrength = strength)
    }

    fun setDenoiseLevel(level: Float) {
        _uiState.value = _uiState.value.copy(denoiseLevel = level)
    }

    fun setFaceFastMode(fast: Boolean) {
        _uiState.value = _uiState.value.copy(faceFastMode = fast)
    }

    fun setColorVibrancy(vibrancy: Float) {
        _uiState.value = _uiState.value.copy(colorVibrancy = vibrancy)
    }

    fun toggleAiDenoise(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(useAiDenoise = enabled)
    }

    fun setColorRenderFactor(factor: Int) {
        _uiState.value = _uiState.value.copy(colorRenderFactor = factor)
    }

    fun selectImage(uri: Uri) {
        _uiState.value = _uiState.value.copy(
            selectedImageUri = uri,
            restoredBitmap = null,
            error = null,
            processingTimeMs = null,
            progress = 0f,
        )
    }

    fun toggleAdvancedMode() {
        _uiState.value = _uiState.value.copy(isAdvancedMode = !_uiState.value.isAdvancedMode)
    }

    fun applyPreset(preset: String) {
        when (preset) {
            "Fast" -> {
                _uiState.value = _uiState.value.copy(
                    upscaleFactor = 1,
                    useAiDenoise = false,
                    useColorization = true,
                    colorRenderFactor = 16,
                    faceStrength = 0.5f,
                    denoiseLevel = 0.0f
                )
            }
            "Balanced" -> {
                _uiState.value = _uiState.value.copy(
                    upscaleFactor = 2,
                    useAiDenoise = true,
                    useColorization = true,
                    colorRenderFactor = 24,
                    faceStrength = 0.7f,
                    denoiseLevel = 0.4f
                )
            }
            "Maximum" -> {
                _uiState.value = _uiState.value.copy(
                    upscaleFactor = 4,
                    useAiDenoise = true,
                    useColorization = true,
                    colorRenderFactor = 32,
                    faceStrength = 0.9f,
                    denoiseLevel = 0.6f
                )
            }
        }
    }

    private var fullRestoredBitmap: Bitmap? = null

    fun startRestoration() {
        val uri = _uiState.value.selectedImageUri ?: return
        val settings = RestorationSettings(
            useColorization = _uiState.value.useColorization,
            upscaleFactor = _uiState.value.upscaleFactor,
            faceStrength = _uiState.value.faceStrength,
            denoiseLevel = _uiState.value.denoiseLevel,
            faceFastMode = _uiState.value.faceFastMode,
            colorVibrancy = _uiState.value.colorVibrancy,
            useAiDenoise = _uiState.value.useAiDenoise,
            colorRenderFactor = _uiState.value.colorRenderFactor,
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                progress = 0f,
                progressText = "Initializing...",
                error = null,
                restoredBitmap = null,
            )

            val useColorization = _uiState.value.useColorization
            val useAiDenoise = _uiState.value.useAiDenoise
            val faceStrength = _uiState.value.faceStrength
            val result = engine.restore(uri, settings) { progress ->
                _uiState.value = _uiState.value.copy(
                    progress = progress,
                    progressText = when {
                        progress < 0.15f -> if (useColorization) "Colorizing image..." else "Initializing..."
                        progress < 0.50f -> "Upscaling (${((progress - 0.15f) / 0.35f * 100).toInt()}%)..."
                        progress < 0.85f -> if (useAiDenoise) "AI Denoising (${((progress - 0.50f) / 0.35f * 100).toInt()}%)..." else "Processing..."
                        progress < 0.95f -> if (faceStrength > 0f) "Enhancing faces..." else "Finalizing..."
                        else -> "Finalizing..."
                    },
                )
            }

            result.onSuccess { res ->
                fullRestoredBitmap = res.bitmap
                
                // Create downscaled preview to avoid OOM and lag
                val preview = if (res.bitmap.width > 2000 || res.bitmap.height > 2000) {
                    val scale = 2000f / maxOf(res.bitmap.width, res.bitmap.height)
                    Bitmap.createScaledBitmap(res.bitmap, (res.bitmap.width * scale).toInt(), (res.bitmap.height * scale).toInt(), true)
                } else {
                    res.bitmap.copy(res.bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                }

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progress = 1f,
                    progressText = "Complete",
                    restoredBitmap = preview,
                    previewBitmap = preview,
                    processingTimeMs = res.processingTimeMs,
                )
                Timber.i("Restoration completed in ${res.processingTimeMs}ms")
            }.onFailure { e ->
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    progress = 0f,
                    progressText = "",
                    error = "Restoration failed: ${e.message}",
                )
                Timber.e(e, "Restoration failed")
            }
        }
    }

    fun savePhoto() {
        val bitmap = fullRestoredBitmap ?: return
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val filename = "makokolor_${System.currentTimeMillis()}.jpg"
                    val folder = "${Environment.DIRECTORY_PICTURES}/MakokolorAI"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val resolver = getApplication<Application>().contentResolver
                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                        }
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(it, values, null, null)
                    }
                    Timber.i("Saved restored photo: $filename")
                    uri to "Pictures/$folder"
                } catch (e: Exception) {
                    Timber.e(e, "Failed to save photo")
                    null to null
                }
            }
            val (savedUri, savedPath) = saved
            _uiState.value = _uiState.value.copy(
                showSaveConfirmation = savedUri != null,
                savePath = savedPath ?: "",
                savedImageUri = savedUri,
            )
        }
    }

    fun openGallery() {
        val uri = _uiState.value.savedImageUri ?: return
        val context = getApplication<Application>()
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/jpeg")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Open with"))
    }

    fun dismissSaveConfirmation() {
        _uiState.value = _uiState.value.copy(showSaveConfirmation = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun retry() {
        _uiState.value = _uiState.value.copy(error = null)
        checkModels()
    }
}
