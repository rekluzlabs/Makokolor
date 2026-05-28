package com.rekluzlabs.makokolor.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.pow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

data class RestorationSettings(
    val useColorization: Boolean = true,
    val upscaleFactor: Int = 2,
    val faceStrength: Float = 0.35f,
    val denoiseLevel: Float = 0.6f,
    val faceFastMode: Boolean = false,
    val colorVibrancy: Float = 1.0f,
    val useAiDenoise: Boolean = true,
    val colorRenderFactor: Int = 32,
)

enum class TensorLayout {
    NCHW,
    NHWC
}

data class ModelInputOutput(
    val inputName: String,
    val outputName: String,
    val inputShape: LongArray? = null,
    val outputShape: LongArray? = null,
    val normalization: Normalization = Normalization.ZERO_TO_ONE,
    val layout: TensorLayout = TensorLayout.NCHW,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ModelInputOutput

        if (inputName != other.inputName) return false
        if (outputName != other.outputName) return false
        if (inputShape != null) {
            if (other.inputShape == null) return false
            if (!inputShape.contentEquals(other.inputShape)) return false
        } else if (other.inputShape != null) return false
        if (outputShape != null) {
            if (other.outputShape == null) return false
            if (!outputShape.contentEquals(other.outputShape)) return false
        } else if (other.outputShape != null) return false
        return normalization == other.normalization
    }

    override fun hashCode(): Int {
        var result = inputName.hashCode()
        result = 31 * result + outputName.hashCode()
        result = 31 * result + (inputShape?.contentHashCode() ?: 0)
        result = 31 * result + (outputShape?.contentHashCode() ?: 0)
        result = 31 * result + normalization.hashCode()
        return result
    }
}

enum class Normalization {
    ZERO_TO_ONE,      // [0, 1]
    MINUS_ONE_TO_ONE, // [-1, 1]
    IMAGE_NET,        // Mean [0.485, 0.456, 0.406], Std [0.229, 0.224, 0.225]
}

class PhotoRestorationEngine(private val context: Context) {

    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var srSession: OrtSession? = null
    private var colorSession: OrtSession? = null
    private var faceSession: OrtSession? = null
    private var scuSession: OrtSession? = null

    private var srInputOutput: ModelInputOutput? = null
    private var colorInputOutput: ModelInputOutput? = null
    private var faceInputOutput: ModelInputOutput? = null
    private var scuInputOutput: ModelInputOutput? = null

    private val tileSize = 256

    data class RestorationResult(
        val bitmap: Bitmap,
        val processingTimeMs: Long,
    )

    fun areModelsReady(): Boolean = ModelDownloader.areModelsDownloaded(context)

    suspend fun loadModels(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            srSession?.close(); srSession = null
            colorSession?.close(); colorSession = null
            faceSession?.close(); faceSession = null
            scuSession?.close(); scuSession = null

            srInputOutput = null
            colorInputOutput = null
            faceInputOutput = null
            scuInputOutput = null

            val paths = ModelDownloader.getModelPaths(context)

            srSession = loadSession(File(paths["realesrgan-x4.onnx"] ?: return@withContext Result.failure(Exception("SR model missing"))))
            srInputOutput = extractModelIOInfo(srSession!!, "RealESRGAN-x4", Normalization.ZERO_TO_ONE)
            Timber.i("SR Model loaded: $srInputOutput")

            faceSession = loadSession(File(paths["codeformer.onnx"] ?: return@withContext Result.failure(Exception("Face model missing"))))
            faceInputOutput = extractModelIOInfo(faceSession!!, "CodeFormer", Normalization.MINUS_ONE_TO_ONE)
            Timber.i("Face Model loaded: $faceInputOutput")

            colorSession = loadSession(File(paths["deoldify.onnx"] ?: return@withContext Result.failure(Exception("Color model missing"))))
            colorInputOutput = extractModelIOInfo(colorSession!!, "Deoldify", Normalization.IMAGE_NET)
            Timber.i("Color Model loaded: $colorInputOutput")

            scuSession = loadSession(File(paths["scunet.onnx"] ?: return@withContext Result.failure(Exception("SCUNet model missing"))))
            scuInputOutput = extractModelIOInfo(scuSession!!, "SCUNet", Normalization.ZERO_TO_ONE)
            Timber.i("SCUNet Model loaded: $scuInputOutput")

            if (srInputOutput == null || faceInputOutput == null || colorInputOutput == null || scuInputOutput == null) {
                return@withContext Result.failure(Exception("One or more models could not be properly initialized"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Model loading failed")
            Result.failure(e)
        }
    }

    private fun extractModelIOInfo(session: OrtSession, modelName: String, defaultNorm: Normalization): ModelInputOutput {
        val inputNames = session.inputNames
        val outputNames = session.outputNames

        Timber.i("$modelName - Input names: $inputNames, Output names: $outputNames")

        val inputName = inputNames?.firstOrNull() ?: "input"
        val outputName = outputNames?.firstOrNull() ?: "output"

        var inputShape: LongArray? = null
        var outputShape: LongArray? = null

        try {
            val nodeInfo = session.inputInfo[inputName]
            inputShape = (nodeInfo?.info as? TensorInfo)?.shape
            Timber.i("$modelName input '$inputName' shape: ${inputShape?.toList()}")
        } catch (e: Exception) {
            Timber.w("Could not get input shape for $modelName: ${e.message}")
        }

        try {
            val nodeInfo = session.outputInfo[outputName]
            outputShape = (nodeInfo?.info as? TensorInfo)?.shape
            Timber.i("$modelName output '$outputName' shape: ${outputShape?.toList()}")
        } catch (e: Exception) {
            Timber.w("Could not get output shape for $modelName: ${e.message}")
        }

        val layout = if (inputShape != null && inputShape.size >= 4 && inputShape[3] == 3L) {
            TensorLayout.NHWC
        } else {
            TensorLayout.NCHW
        }

        return ModelInputOutput(
            inputName = inputName,
            outputName = outputName,
            inputShape = inputShape,
            outputShape = outputShape,
            normalization = defaultNorm,
            layout = layout,
        )
    }

    private fun loadSession(modelFile: File): OrtSession {
        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtLeast(2))
        try {
            sessionOptions.addNnapi()
            Timber.i("NNAPI acceleration enabled")
        } catch (e: Exception) {
            Timber.w("NNAPI not available: ${e.message}")
        }
        sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        return ortEnv.createSession(modelFile.absolutePath, sessionOptions)
    }

    suspend fun restore(uri: Uri, settings: RestorationSettings, onProgress: (Float) -> Unit): Result<RestorationResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val inputBitmap = decodeBitmap(uri) ?: throw Exception("Decode failed")
            val restored = processWithTiling(inputBitmap, settings, onProgress)
            Result.success(RestorationResult(restored, System.currentTimeMillis() - startTime))
        } catch (e: Exception) {
            Timber.e(e, "Restoration failed")
            Result.failure(e)
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inMutable = true }) }
    } catch (e: Exception) {
        Timber.e(e, "Failed to decode bitmap")
        null
    }

    private suspend fun processWithTiling(bitmap: Bitmap, settings: RestorationSettings, onProgress: (Float) -> Unit): Bitmap {
        // Step 1: Colorization (DeOldify) first
        val colorized = if (settings.useColorization) {
            onProgress(0.05f)
            addColorization(bitmap, settings, onProgress)
        } else {
            bitmap
        }

        val scale = settings.upscaleFactor
        val outputBitmap = Bitmap.createBitmap(colorized.width * scale, colorized.height * scale, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(outputBitmap)

        // TILING PARAMETERS
        val tilePad = 10 // Padding to avoid seams
        val effectiveTileSize = tileSize // 256
        
        val tilesX = (colorized.width + effectiveTileSize - 1) / effectiveTileSize
        val tilesY = (colorized.height + effectiveTileSize - 1) / effectiveTileSize

        Timber.i("Processing image as $tilesX x $tilesY tiles (size: $effectiveTileSize, pad: $tilePad)")

        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                val tileIndex = ty * tilesX + tx
                val tileCount = tilesX * tilesY

                val x = tx * effectiveTileSize
                val y = ty * effectiveTileSize
                
                // Calculate crop bounds with padding
                val left = maxOf(0, x - tilePad)
                val top = maxOf(0, y - tilePad)
                val right = minOf(colorized.width, x + effectiveTileSize + tilePad)
                val bottom = minOf(colorized.height, y + effectiveTileSize + tilePad)
                
                val cropW = right - left
                val cropH = bottom - top
                
                val tileBitmap = Bitmap.createBitmap(colorized, left, top, cropW, cropH)

                // Step 2: Denoise (SCUNet) - Applied BEFORE upscaling
                var currentTile = tileBitmap
                if (settings.useAiDenoise && settings.denoiseLevel > 0f) {
                    onProgress(0.15f + 0.35f * ((tileIndex + 0.25f) / tileCount))
                    val denoised = runSCUNet(currentTile)
                    
                    if (denoised != currentTile) {
                        val blended = blendBitmaps(currentTile, denoised, settings.denoiseLevel)
                        if (denoised != currentTile) denoised.recycle()
                        currentTile = blended
                    }
                }

                // Step 3: Upscale (RealESRGAN)
                onProgress(0.15f + 0.35f * ((tileIndex + 0.75f) / tileCount))
                val srTile = if (scale > 1) {
                    runSuperResolution(currentTile)
                } else {
                    currentTile.copy(Bitmap.Config.ARGB_8888, true)
                }
                
                // Manually handle scaling and padding removal
                val expectedTileW = (right - left) * scale
                val expectedTileH = (bottom - top) * scale
                
                var finalSrTile = if (srTile.width != expectedTileW || srTile.height != expectedTileH) {
                    val scaled = Bitmap.createScaledBitmap(srTile, expectedTileW, expectedTileH, true)
                    if (srTile != currentTile && srTile != tileBitmap) srTile.recycle()
                    scaled
                } else {
                    srTile
                }

                // Remove padding from the upscaled tile
                val padL = (x - left) * scale
                val padT = (y - top) * scale
                val actualW = minOf(effectiveTileSize, colorized.width - x) * scale
                val actualH = minOf(effectiveTileSize, colorized.height - y) * scale
                
                val unpaddedTile = Bitmap.createBitmap(finalSrTile, padL, padT, actualW, actualH)
                
                if (finalSrTile != srTile && finalSrTile != currentTile && finalSrTile != tileBitmap) {
                    finalSrTile.recycle()
                }
                if (srTile != currentTile && srTile != tileBitmap) {
                    srTile.recycle()
                }
                if (currentTile != tileBitmap) {
                    currentTile.recycle()
                }
                tileBitmap.recycle()

                canvas.drawBitmap(unpaddedTile, (x * scale).toFloat(), (y * scale).toFloat(), null)
                unpaddedTile.recycle()
            }
        }

        // Cleanup colorized copy if it was created
        if (colorized != bitmap) {
            colorized.recycle()
        }

        // Step 4: Face Restoration (CodeFormer) - Last step
        val faceRestored = if (settings.faceStrength > 0f) {
            onProgress(0.90f)
            val result = runFaceRestoration(outputBitmap, settings.faceStrength, settings.faceFastMode)
            if (result != outputBitmap) {
                outputBitmap.recycle()
            }
            result
        } else {
            outputBitmap
        }
        
        onProgress(1.0f)
        return faceRestored
    }

    private fun runSuperResolution(bitmap: Bitmap): Bitmap {
        val session = srSession ?: return bitmap
        val ioInfo = srInputOutput ?: return bitmap

        val startTime = System.currentTimeMillis()
        return try {
            Timber.i("Running Super-Resolution on ${bitmap.width}x${bitmap.height}")

            val isNCHW = ioInfo.layout == TensorLayout.NCHW
            
            // Check for fixed input shape
            var inputW = bitmap.width
            var inputH = bitmap.height
            var needsResizing = false
            
            if (ioInfo.inputShape != null && ioInfo.inputShape.size >= 4) {
                val modelH = if (isNCHW) ioInfo.inputShape[2] else ioInfo.inputShape[1]
                val modelW = if (isNCHW) ioInfo.inputShape[3] else ioInfo.inputShape[2]
                
                if (modelH > 0 && modelW > 0 && (modelH != inputH.toLong() || modelW != inputW.toLong())) {
                    inputH = modelH.toInt()
                    inputW = modelW.toInt()
                    needsResizing = true
                }
            }
            
            val resizedBitmap = if (needsResizing) Bitmap.createScaledBitmap(bitmap, inputW, inputH, true) else bitmap
            val inputShape = if (isNCHW) longArrayOf(1, 3, inputH.toLong(), inputW.toLong())
                             else longArrayOf(1, inputH.toLong(), inputW.toLong(), 3)
            
            val floatArray = bitmapToFloatArray(resizedBitmap, ioInfo.normalization, inputShape)
            if (needsResizing && resizedBitmap != bitmap) resizedBitmap.recycle()

            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatArray), inputShape)
            val results = session.run(mapOf(ioInfo.inputName to tensor))
            val output = results[ioInfo.outputName].orElseThrow() as OnnxTensor

            val outputBuffer = output.floatBuffer
            val shapeInfo = output.info.shape

            val outArray = FloatArray(outputBuffer.capacity())
            outputBuffer.rewind()
            outputBuffer.get(outArray)
            
            // LOG RANGE
            var minV = Float.MAX_VALUE; var maxV = Float.MIN_VALUE
            for (v in outArray) { if (v < minV) minV = v; if (v > maxV) maxV = v }
            Timber.i("SR output range: [$minV, $maxV], shape: ${shapeInfo.toList()}")

            val isOutputNCHW = ioInfo.layout == TensorLayout.NCHW
            val outH = if (isOutputNCHW) shapeInfo[2].toInt() else shapeInfo[1].toInt()
            val outW = if (isOutputNCHW) shapeInfo[3].toInt() else shapeInfo[2].toInt()

            tensor.close()
            results.close()

            val resultBitmap = floatArrayToBitmap(outArray, outW, outH, ioInfo.normalization, shapeInfo)
            
            // If we resized the input, we might need to resize the output back to maintain the expected scale factor
            val finalResult = if (needsResizing) {
                val targetW = bitmap.width * 4
                val targetH = bitmap.height * 4
                val scaled = Bitmap.createScaledBitmap(resultBitmap, targetW, targetH, true)
                resultBitmap.recycle()
                scaled
            } else {
                resultBitmap
            }
            
            Timber.i("SR completed in ${System.currentTimeMillis() - startTime}ms -> ${finalResult.width}x${finalResult.height}")
            finalResult
        } catch (e: Exception) {
            Timber.e(e, "SR inference failed: ${e.message}")
            bitmap
        }
    }

    private fun runSCUNet(bitmap: Bitmap): Bitmap {
        val session = scuSession ?: return bitmap
        val ioInfo = scuInputOutput ?: return bitmap

        val startTime = System.currentTimeMillis()
        return try {
            Timber.i("Running SCUNet denoising on ${bitmap.width}x${bitmap.height}")

            val isNCHW = ioInfo.inputShape == null || 
                        (ioInfo.inputShape.size > 1 && (ioInfo.inputShape[1] == 3L || ioInfo.inputShape[1] == -1L))
            
            // Check for fixed input shape
            var inputW = bitmap.width
            var inputH = bitmap.height
            var needsResizing = false
            
            if (ioInfo.inputShape != null && ioInfo.inputShape.size >= 4) {
                val modelH = if (isNCHW) ioInfo.inputShape[2] else ioInfo.inputShape[1]
                val modelW = if (isNCHW) ioInfo.inputShape[3] else ioInfo.inputShape[2]
                
                if (modelH > 0 && modelW > 0 && (modelH != inputH.toLong() || modelW != inputW.toLong())) {
                    inputH = modelH.toInt()
                    inputW = modelW.toInt()
                    needsResizing = true
                    Timber.i("Resizing input to $inputW x $inputH for SCUNet model")
                }
            }

            val resizedBitmap = if (needsResizing) Bitmap.createScaledBitmap(bitmap, inputW, inputH, true) else bitmap
            val actualShape = if (isNCHW) longArrayOf(1, 3, inputH.toLong(), inputW.toLong()) 
                              else longArrayOf(1, inputH.toLong(), inputW.toLong(), 3)
            
            val floatArray = bitmapToFloatArray(resizedBitmap, ioInfo.normalization, actualShape)
            if (needsResizing && resizedBitmap != bitmap) resizedBitmap.recycle()

            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatArray), actualShape)
            val results = session.run(mapOf(ioInfo.inputName to tensor))
            val output = results[ioInfo.outputName].orElseThrow() as OnnxTensor

            val outputBuffer = output.floatBuffer
            val shapeInfo = output.info.shape
            
            val outArray = FloatArray(outputBuffer.capacity())
            outputBuffer.rewind()
            outputBuffer.get(outArray)

            // LOG RANGE
            var minV = Float.MAX_VALUE; var maxV = Float.MIN_VALUE
            for (v in outArray) { if (v < minV) minV = v; if (v > maxV) maxV = v }
            Timber.i("SCUNet output range: [$minV, $maxV]")

            val isOutputNCHW = ioInfo.layout == TensorLayout.NCHW
            val outH = if (isOutputNCHW) shapeInfo[2].toInt() else shapeInfo[1].toInt()
            val outW = if (isOutputNCHW) shapeInfo[3].toInt() else shapeInfo[2].toInt()

            tensor.close()
            results.close()

            val resultBitmap = floatArrayToBitmap(outArray, outW, outH, ioInfo.normalization, shapeInfo)
            
            val finalResult = if (needsResizing) {
                val scaled = Bitmap.createScaledBitmap(resultBitmap, bitmap.width, bitmap.height, true)
                resultBitmap.recycle()
                scaled
            } else {
                resultBitmap
            }

            Timber.i("SCUNet completed in ${System.currentTimeMillis() - startTime}ms")
            finalResult
        } catch (e: Exception) {
            Timber.e(e, "SCUNet inference failed: ${e.message}")
            bitmap
        }
    }

    private suspend fun runFaceRestoration(bitmap: Bitmap, faceStrength: Float = 1.0f, faceFastMode: Boolean = false): Bitmap {
        val session = faceSession ?: return bitmap
        val ioInfo = faceInputOutput ?: return bitmap

        return try {
            Timber.i("Running Targeted Face Restoration...")
            val performanceMode = if (faceFastMode)
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
            else
                FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(performanceMode)
                .build()
            val detector = FaceDetection.getClient(options)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val faces = detector.process(inputImage).await()

            if (faces.isEmpty()) {
                Timber.i("No faces detected to enhance")
                return bitmap
            }

            val frontalFaces = faces.filter { face ->
                val eulerX = abs(face.headEulerAngleX)
                val eulerY = abs(face.headEulerAngleY)
                val eulerZ = abs(face.headEulerAngleZ)
                val isFrontal = eulerX < 25f && eulerY < 25f && eulerZ < 15f
                if (!isFrontal) {
                    Timber.i("Skipping non-frontal face: euler($eulerX, $eulerY, $eulerZ)")
                }
                isFrontal
            }

            if (frontalFaces.isEmpty()) {
                Timber.i("No frontal faces to enhance")
                return bitmap
            }

            Timber.i("Detected ${frontalFaces.size} frontal faces out of ${faces.size}. Applying CodeFormer...")
            val resultBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(resultBitmap)
            val paint = android.graphics.Paint()

            for (face in frontalFaces) {
                val bounds = face.boundingBox
                
                // SQUARE CROP: CodeFormer expects 1:1 aspect ratio. 
                // Using a rectangular crop and squashing it causes blur/distortion.
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()
                val rawSize = maxOf(bounds.width(), bounds.height())
                val sizeWithPadding = (rawSize * 1.6f).toInt() // 60% padding for better context
                
                val size = minOf(sizeWithPadding, bitmap.width, bitmap.height)
                var left = centerX - size / 2
                var top = centerY - size / 2
                
                // Adjust if out of bounds
                if (left < 0) left = 0
                if (top < 0) top = 0
                if (left + size > bitmap.width) left = bitmap.width - size
                if (top + size > bitmap.height) top = bitmap.height - size

                val faceBitmap = try {
                    Bitmap.createBitmap(bitmap, left, top, size, size)
                } catch (e: Exception) {
                    continue
                }
                
                val isNCHW = ioInfo.layout == TensorLayout.NCHW

                // Check for fixed input shape, default to 512x512
                var inputW = 512
                var inputH = 512
                if (ioInfo.inputShape != null && ioInfo.inputShape.size >= 4) {
                    val modelH = if (isNCHW) ioInfo.inputShape[2] else ioInfo.inputShape[1]
                    val modelW = if (isNCHW) ioInfo.inputShape[3] else ioInfo.inputShape[2]
                    if (modelH > 0) inputH = modelH.toInt()
                    if (modelW > 0) inputW = modelW.toInt()
                }

                val resized = Bitmap.createScaledBitmap(faceBitmap, inputW, inputH, true)
                val actualShape = if (isNCHW) longArrayOf(1, 3, inputH.toLong(), inputW.toLong()) 
                                  else longArrayOf(1, inputH.toLong(), inputW.toLong(), 3)
                
                val floatArray = bitmapToFloatArray(resized, ioInfo.normalization, actualShape)

                val inputMap = mutableMapOf<String, OnnxTensor>()
                val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatArray), actualShape)
                inputMap[ioInfo.inputName] = tensor
                
                // FIDELITY CHECK: CodeFormer models accept a 'weight' or 'w' or 'fidelity' input.
                // According to inference docs, 0.0 is max restoration and 1.0 is original.
                // We map faceStrength (1.0 = full AI) to fidelity weight (0.0 = full AI).
                session.inputNames.forEach { name ->
                    if (name != ioInfo.inputName && (name == "weight" || name == "w" || name == "fidelity")) {
                        val wValue = (1.0f - faceStrength).coerceIn(0f, 1f)
                        val weightTensor = OnnxTensor.createTensor(ortEnv, floatArrayOf(wValue))
                        inputMap[name] = weightTensor
                        Timber.i("Passing fidelity weight $wValue to CodeFormer input: $name")
                    }
                }

                val results = session.run(inputMap)
                val output = results[ioInfo.outputName].orElseThrow() as OnnxTensor

                val outputBuffer = output.floatBuffer
                val shapeInfo = output.info.shape
                
                val outArray = FloatArray(outputBuffer.capacity())
                outputBuffer.rewind()
                outputBuffer.get(outArray)
                
                // LOG RANGE
                var minV = Float.MAX_VALUE; var maxV = Float.MIN_VALUE
                for (v in outArray) { if (v < minV) minV = v; if (v > maxV) maxV = v }
                Timber.i("CodeFormer face output range: [$minV, $maxV]")

                inputMap.values.forEach { it.close() }
                results.close()

                val isOutputNCHW = ioInfo.layout == TensorLayout.NCHW
                val outH = if (isOutputNCHW) shapeInfo[2].toInt() else shapeInfo[1].toInt()
                val outW = if (isOutputNCHW) shapeInfo[3].toInt() else shapeInfo[2].toInt()

                val enhanced = floatArrayToBitmap(outArray, outW, outH, ioInfo.normalization, shapeInfo)
                val scaledBack = Bitmap.createScaledBitmap(enhanced, size, size, true)

                // FEATHERED BLENDING: Using a Radial Gradient to avoid edge halos
                val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
                val maskCanvas = android.graphics.Canvas(mask)
                val featherPaint = android.graphics.Paint().apply {
                    shader = android.graphics.RadialGradient(
                        size / 2f, size / 2f, size / 2f,
                        intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
                        floatArrayOf(0.0f, 0.85f, 1.0f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                }
                maskCanvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), featherPaint)

                // Apply mask to face
                val maskedFace = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val maskedCanvas = android.graphics.Canvas(maskedFace)
                maskedCanvas.drawBitmap(scaledBack, 0f, 0f, null)
                val xferPaint = android.graphics.Paint().apply {
                    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
                }
                maskedCanvas.drawBitmap(mask, 0f, 0f, xferPaint)

                val originalFace = Bitmap.createBitmap(resultBitmap, left, top, size, size)
                val blendedFace = blendBitmaps(originalFace, maskedFace, faceStrength)
                canvas.drawBitmap(blendedFace, left.toFloat(), top.toFloat(), paint)

                mask.recycle()
                maskedFace.recycle()
                originalFace.recycle()
                blendedFace.recycle()
                faceBitmap.recycle()
                resized.recycle()
                enhanced.recycle()
                scaledBack.recycle()
            }

            detector.close()
            Timber.i("Face restoration completed")
            resultBitmap
        } catch (e: Exception) {
            Timber.e(e, "Face restoration failed")
            bitmap
        }
    }

    private fun addColorization(bitmap: Bitmap, settings: RestorationSettings, onProgress: (Float) -> Unit): Bitmap {
        val session = colorSession ?: return bitmap
        val ioInfo = colorInputOutput ?: return bitmap

        val startTime = System.currentTimeMillis()
        return try {
            Timber.i("Running colorization with render factor ${settings.colorRenderFactor}...")
            val w = bitmap.width
            val h = bitmap.height

            val grayFloatArray = bitmapToGrayscaleArray(bitmap)
            val greyBitmap = toGreyscale(bitmap)
            
            // PROPORTIONAL SCALING
            val maxDim = maxOf(w, h)
            val scale = settings.colorRenderFactor.toFloat() / maxDim.toFloat()
            val targetW = (w * scale).toInt().coerceAtLeast(32)
            val targetH = (h * scale).toInt().coerceAtLeast(32)
            
            val resized = Bitmap.createScaledBitmap(greyBitmap, targetW, targetH, true)

            val isNCHW = ioInfo.layout == TensorLayout.NCHW
            val actualShape = if (isNCHW) longArrayOf(1, 3, targetH.toLong(), targetW.toLong()) 
                              else longArrayOf(1, targetH.toLong(), targetW.toLong(), 3)
            val floatArray = bitmapToFloatArray(resized, ioInfo.normalization, actualShape)

            val tensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(floatArray), actualShape)
            val results = session.run(mapOf(ioInfo.inputName to tensor))
            val output = results[ioInfo.outputName].orElseThrow() as OnnxTensor

            val outputBuffer = output.floatBuffer
            val shapeInfo = output.info.shape
            
            val outArray = FloatArray(outputBuffer.capacity())
            outputBuffer.rewind()
            outputBuffer.get(outArray)

            var minV = Float.MAX_VALUE; var maxV = Float.MIN_VALUE
            for (v in outArray) { if (v < minV) minV = v; if (v > maxV) maxV = v }
            Timber.i("Colorization output range: [$minV, $maxV], shape: ${shapeInfo.toList()}")
            Timber.i("Colorization input shape: ${actualShape.toList()}, original: ${bitmap.width}x${bitmap.height}, resized: ${resized.width}x${resized.height}")

            tensor.close()
            results.close()

            onProgress(0.12f)

            val outPixels = IntArray(w * h)

            val isOutputNCHW = ioInfo.layout == TensorLayout.NCHW
            val outH = if (isOutputNCHW) shapeInfo[2].toInt() else shapeInfo[1].toInt()
            val outW = if (isOutputNCHW) shapeInfo[3].toInt() else shapeInfo[2].toInt()
            val numChannels = if (isOutputNCHW) shapeInfo[1].toInt() else shapeInfo[3].toInt()
            val blockHw = outH * outW

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val srcX = (x.toFloat() / w * outW).toInt().coerceIn(0, outW - 1)
                    val srcY = (y.toFloat() / h * outH).toInt().coerceIn(0, outH - 1)
                    val srcIdx = srcY * outW + srcX

                    val (rFinal, gFinal, bFinal) = when (numChannels) {
                        2 -> {
                            var aVal = outArray[0 * blockHw + srcIdx]
                            var bVal = outArray[1 * blockHw + srcIdx]

                            // DeOldify outputs a/b channels in [-1, 1]; scale to LAB range [-100, 100]
                            aVal = aVal * 100f
                            bVal = bVal * 100f

                            val lVal = (grayFloatArray[y * w + x] / 255f) * 100f
                            labToRgb(lVal, aVal, bVal)
                        }
                        3 -> {
                            // DeOldify ONNX models exported via OpenCV pipelines output in BGR format
                            // We need to swap channel 0 (Blue) and channel 2 (Red)
                            var bn = outArray[0 * blockHw + srcIdx]
                            var gn = outArray[1 * blockHw + srcIdx]
                            var rn = outArray[2 * blockHw + srcIdx]

                            when (ioInfo.normalization) {
                                Normalization.IMAGE_NET -> {
                                    // De-normalize using ImageNet values (BGR order for standard mean/std arrays)
                                    // Mean: R=0.485, G=0.456, B=0.406
                                    // Std:  R=0.229, G=0.224, B=0.225
                                    rn = (rn * 0.229f) + 0.485f
                                    gn = (gn * 0.224f) + 0.456f
                                    bn = (bn * 0.225f) + 0.406f
                                }
                                Normalization.MINUS_ONE_TO_ONE -> {
                                    rn = (rn + 1f) / 2f
                                    gn = (gn + 1f) / 2f
                                    bn = (bn + 1f) / 2f
                                }
                                Normalization.ZERO_TO_ONE -> {}
                            }

                            rn = rn.coerceIn(0f, 1f)
                            gn = gn.coerceIn(0f, 1f)
                            bn = bn.coerceIn(0f, 1f)

                            if (settings.colorVibrancy != 1.0f) {
                                val v = settings.colorVibrancy
                                val mean = (rn + gn + bn) / 3f
                                rn = (mean + (rn - mean) * v).coerceIn(0f, 1f)
                                gn = (mean + (gn - mean) * v).coerceIn(0f, 1f)
                                bn = (mean + (bn - mean) * v).coerceIn(0f, 1f)
                            }

                            val (_, aOut, bOutVal) = rgbToLab(rn, gn, bn)
                            val lOrig = (grayFloatArray[y * w + x] / 255f) * 100f
                            labToRgb(lOrig, aOut, bOutVal)
                        }
                        4 -> {
                            // Some DeOldify exports output RGBA; use first 3 channels as BGR
                            var bn = outArray[0 * blockHw + srcIdx]
                            var gn = outArray[1 * blockHw + srcIdx]
                            var rn = outArray[2 * blockHw + srcIdx]

                            rn = (rn + 1f) / 2f
                            gn = (gn + 1f) / 2f
                            bn = (bn + 1f) / 2f

                            rn = rn.coerceIn(0f, 1f)
                            gn = gn.coerceIn(0f, 1f)
                            bn = bn.coerceIn(0f, 1f)

                            val lOrig = (grayFloatArray[y * w + x] / 255f) * 100f
                            val (_, aOut, bOutVal) = rgbToLab(rn, gn, bn)
                            labToRgb(lOrig, aOut, bOutVal)
                        }
                        else -> {
                            Timber.w("Unexpected colorization channel count: $numChannels, falling back to grayscale")
                            val gray = grayFloatArray[y * w + x] / 255f
                            Triple(gray, gray, gray)
                        }
                    }

                    val ri = (rFinal * 255f).toInt().coerceIn(0, 255)
                    val gi = (gFinal * 255f).toInt().coerceIn(0, 255)
                    val bi = (bFinal * 255f).toInt().coerceIn(0, 255)
                    outPixels[y * w + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }

            var colorPixelCount = 0
            for (i in outPixels.indices) {
                val p = outPixels[i]
                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF
                if (abs(pr - pg) > 10 || abs(pg - pb) > 10) colorPixelCount++
            }
            Timber.i("Colorized pixels with actual color: $colorPixelCount / ${outPixels.size}")

            greyBitmap.recycle()
            resized.recycle()

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(outPixels, 0, w, 0, 0, w, h)
            Timber.i("Colorization completed in ${System.currentTimeMillis() - startTime}ms")
            result
        } catch (e: Exception) {
            Timber.e(e, "Colorization failed")
            bitmap
        }
    }

    private fun isColorImage(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var colorPixels = 0
        val sampleCount = minOf(200, pixels.size)
        val step = maxOf(1, pixels.size / sampleCount)
        for (i in 0 until pixels.size step step) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (abs(r - g) > 20 || abs(g - b) > 20 || abs(r - b) > 20) colorPixels++
        }
        return colorPixels > sampleCount * 0.15
    }

    private fun toGreyscale(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val grey = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, w, 0, 0, w, h)
        return result
    }

    private fun bitmapToGrayscaleArray(bitmap: Bitmap): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val floatArray = FloatArray(h * w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = pixels[y * w + x]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                floatArray[y * w + x] = (0.299 * r + 0.587 * g + 0.114 * b).toFloat()
            }
        }
        return floatArray
    }

    companion object {
        private fun bitmapToFloatArray(bitmap: Bitmap, normalization: Normalization, shape: LongArray? = null): FloatArray {
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

            val floatArray = FloatArray(3 * h * w)
            // Layout is NCHW if channel dim is at index 1
            val isNCHW = shape == null || (shape.size > 1 && shape[1] == 3L)

            for (y in 0 until h) {
                for (x in 0 until w) {
                    val pixel = pixels[y * w + x]
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF

                    var rf: Float
                    var gf: Float
                    var bf: Float

                    when (normalization) {
                        Normalization.IMAGE_NET -> {
                            // IMAGE_NET normalization uses specific RGB mean/std
                            rf = (r / 255f - 0.485f) / 0.229f
                            gf = (g / 255f - 0.456f) / 0.224f
                            bf = (b / 255f - 0.406f) / 0.225f
                        }
                        Normalization.MINUS_ONE_TO_ONE -> {
                            rf = (r / 255f) * 2f - 1f
                            gf = (g / 255f) * 2f - 1f
                            bf = (b / 255f) * 2f - 1f
                        }
                        Normalization.ZERO_TO_ONE -> {
                            rf = r / 255f
                            gf = g / 255f
                            bf = b / 255f
                        }
                    }

                    if (isNCHW) {
                        floatArray[0 * h * w + y * w + x] = rf
                        floatArray[1 * h * w + y * w + x] = gf
                        floatArray[2 * h * w + y * w + x] = bf
                    } else {
                        floatArray[y * w * 3 + x * 3 + 0] = rf
                        floatArray[y * w * 3 + x * 3 + 1] = gf
                        floatArray[y * w * 3 + x * 3 + 2] = bf
                    }
                }
            }
            return floatArray
        }

        private fun floatArrayToBitmap(floatArray: FloatArray, width: Int, height: Int, normalization: Normalization, shape: LongArray? = null): Bitmap {
            val pixels = IntArray(width * height)
            val hw = height * width
            // Layout is NCHW if channel dim is at index 1
            val isNCHW = shape == null || (shape.size > 1 && shape[1] == 3L)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    var r: Float
                    var g: Float
                    var b: Float

                    val idx = y * width + x
                    if (isNCHW) {
                        r = floatArray[0 * hw + idx]
                        g = floatArray[1 * hw + idx]
                        b = floatArray[2 * hw + idx]
                    } else {
                        r = floatArray[idx * 3 + 0]
                        g = floatArray[idx * 3 + 1]
                        b = floatArray[idx * 3 + 2]
                    }

                    when (normalization) {
                        Normalization.IMAGE_NET -> {
                            r = (r * 0.229f) + 0.485f
                            g = (g * 0.224f) + 0.456f
                            b = (b * 0.225f) + 0.406f
                        }
                        Normalization.MINUS_ONE_TO_ONE -> {
                            r = (r + 1f) / 2f
                            g = (g + 1f) / 2f
                            b = (b + 1f) / 2f
                        }
                        Normalization.ZERO_TO_ONE -> {
                            // Already in correct range
                        }
                    }

                    val ri = (r * 255f).toInt().coerceIn(0, 255)
                    val gi = (g * 255f).toInt().coerceIn(0, 255)
                    val bi = (b * 255f).toInt().coerceIn(0, 255)
                    pixels[y * width + x] = (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            return bitmap
        }

        private fun blendBitmaps(original: Bitmap, processed: Bitmap, alpha: Float): Bitmap {
            val w = original.width
            val h = original.height
            val origPixels = IntArray(w * h)
            val procPixels = IntArray(w * h)
            original.getPixels(origPixels, 0, w, 0, 0, w, h)
            processed.getPixels(procPixels, 0, w, 0, 0, w, h)

            val blended = IntArray(w * h)
            for (i in blended.indices) {
                val o = origPixels[i]
                val p = procPixels[i]

                val or = (o shr 16) and 0xFF
                val og = (o shr 8) and 0xFF
                val ob = o and 0xFF

                val pr = (p shr 16) and 0xFF
                val pg = (p shr 8) and 0xFF
                val pb = p and 0xFF

                val r = (or * (1 - alpha) + pr * alpha).toInt()
                val g = (og * (1 - alpha) + pg * alpha).toInt()
                val b = (ob * (1 - alpha) + pb * alpha).toInt()

                blended[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }

            val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            result.setPixels(blended, 0, w, 0, 0, w, h)
            return result
        }

        private fun rgbToLab(r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
            fun linearize(v: Float): Float {
                return if (v > 0.04045f) ((v + 0.055f) / 1.055f).pow(2.4f)
                else v / 12.92f
            }

            val rl = linearize(r)
            val gl = linearize(g)
            val bl = linearize(b)

            val x = 0.4124564f * rl + 0.3575761f * gl + 0.1804375f * bl
            val y = 0.2126729f * rl + 0.7151522f * gl + 0.0721750f * bl
            val z = 0.0193339f * rl + 0.1191920f * gl + 0.9503041f * bl

            val xn = 0.95047f
            val yn = 1.0f
            val zn = 1.08883f

            fun f(t: Float): Float {
                return if (t > 0.008856f) t.pow(1f / 3f)
                else (7.787f * t) + (16f / 116f)
            }

            val fx = f(x / xn)
            val fy = f(y / yn)
            val fz = f(z / zn)

            val l = 116f * fy - 16f
            val a = 500f * (fx - fy)
            val bv = 200f * (fy - fz)

            return Triple(l, a, bv)
        }

        private fun labToRgb(l: Float, a: Float, b: Float): Triple<Float, Float, Float> {
            var y = (l + 16f) / 116f
            var x = a / 500f + y
            var z = y - b / 200f

            x = if (x > 0.2068966f) x * x * x else 0.1284185f * (x - 0.137931f)
            y = if (y > 0.2068966f) y * y * y else 0.1284185f * (y - 0.137931f)
            z = if (z > 0.2068966f) z * z * z else 0.1284185f * (z - 0.137931f)

            val rn = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
            val gn = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
            val bn = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z

            fun gammaCorrect(v: Float): Float {
                return if (v > 0.0031308f) 1.055f * v.pow(1f / 2.4f) - 0.055f
                else 12.92f * v
            }

            return Triple(
                gammaCorrect(rn).coerceIn(0f, 1f),
                gammaCorrect(gn).coerceIn(0f, 1f),
                gammaCorrect(bn).coerceIn(0f, 1f),
            )
        }
    }
}
