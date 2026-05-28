package com.rekluzlabs.makokolor.ui

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.rekluzlabs.makokolor.BuildConfig
import com.rekluzlabs.makokolor.R

@Composable
fun PhotoRestorationScreen(
    viewModel: PhotoRestorationViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showAiDenoiseInfo by remember { mutableStateOf(false) }
    var showFaceDetectInfo by remember { mutableStateOf(false) }
    var showColorizationInfo by remember { mutableStateOf(false) }
    var showRenderFactorInfo by remember { mutableStateOf(false) }
    var showUpscaleInfo by remember { mutableStateOf(false) }
    var showFaceStrengthInfo by remember { mutableStateOf(false) }
    var showDenoiseInfo by remember { mutableStateOf(false) }
    var showVibrancyInfo by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let { viewModel.selectImage(it) }
    }

    LaunchedEffect(uiState.isProcessing) {
        if (uiState.isProcessing && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    LaunchedEffect(uiState.restoredBitmap) {
        if (uiState.restoredBitmap != null && !uiState.isProcessing && listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF64B5F6))) {
                        append("Mako")
                    }
                    val rainbow = listOf(
                        Color(0xFFE53935), // red
                        Color(0xFFFF9800), // orange
                        Color(0xFFFDD835), // yellow
                        Color(0xFF43A047), // green
                        Color(0xFF1E88E5), // blue
                    )
                    "kolor".forEachIndexed { i, c ->
                        withStyle(SpanStyle(color = rainbow[i])) {
                            append(c.toString())
                        }
                    }
                },
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "© 2026 by Rekluz Labs",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
            
            if (uiState.downloadState == DownloadState.INITIAL) {
                Spacer(Modifier.height(24.dp))
                Image(
                    painter = painterResource(id = R.drawable.makokolor_icon),
                    contentDescription = "Makokolor Logo",
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.checkModels() },
                    modifier = Modifier.fillMaxWidth(0.7f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tap to load AI models")
                }
            }

            Spacer(Modifier.height(4.dp))
            if (uiState.downloadState != DownloadState.INITIAL) {
                Text(
                    text = "AI-powered photo restoration & enhancement",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        when (uiState.downloadState) {
            DownloadState.INITIAL -> {
                // Handled in the header item for better layout
            }

            DownloadState.CHECKING -> {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Checking AI models...")
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            DownloadState.NEEDS_DOWNLOAD -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "AI Models Required",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "This app needs ~400MB of AI models to restore photos. " +
                                        "Download them now? (Wi-Fi recommended)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.startDownload() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Download AI Models (~510MB)")
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            DownloadState.DOWNLOADING -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Downloading AI Models",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(12.dp))
                            val animatedProgress by animateFloatAsState(
                                targetValue = uiState.downloadProgress,
                                label = "download_progress",
                            )
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = uiState.downloadProgressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${(uiState.downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            DownloadState.LOADING -> {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Loading AI models into memory...\nPlease be patient, this takes time...")
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            DownloadState.FAILED -> {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = uiState.error ?: "Something went wrong",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(onClick = { viewModel.retry() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            DownloadState.READY -> {
                // App is ready - show image picker and restoration UI
            }
        }

        if (uiState.downloadState == DownloadState.READY) {
            item {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isProcessing,
                ) {
                    Text(
                        if (uiState.selectedImageUri == null) "Select Photo to Restore"
                        else "Choose Different Photo"
                    )
                }
            }
        }

        uiState.selectedImageUri?.let { uri ->
            if (uiState.downloadState == DownloadState.READY) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Original",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    // Preset Selection
                    Text(
                        text = "Restoration Presets",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Fast", "Balanced", "Maximum").forEach { preset ->
                            FilterChip(
                                selected = false, 
                                onClick = { viewModel.applyPreset(preset) },
                                label = { Text(preset) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Advanced Settings Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleAdvancedMode() }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (uiState.isAdvancedMode) "Hide Advanced Settings" else "Show Advanced Settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (uiState.isAdvancedMode) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = uiState.isAdvancedMode) {
                        Column {
                            // Colorization Toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Enable Colorization")
                                    Text(
                                        "\u24D8",
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .clickable { showColorizationInfo = true },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = uiState.useColorization,
                                    onCheckedChange = { viewModel.toggleColorization(it) },
                                )
                            }

                            if (uiState.useColorization) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Color Render Factor",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    Text(
                                        "\u24D8",
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .clickable { showRenderFactorInfo = true },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(16, 24, 32).forEach { factor ->
                                        FilterChip(
                                            selected = uiState.colorRenderFactor == factor,
                                            onClick = { viewModel.setColorRenderFactor(factor) },
                                            label = { Text(if (factor == 16) "Fast" else if (factor == 24) "Balanced" else "High") },
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Upscale Factor Selector
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Upscale Factor",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    "\u24D8",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable { showUpscaleInfo = true },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 2, 4).forEach { factor ->
                                    FilterChip(
                                        selected = uiState.upscaleFactor == factor,
                                        onClick = { viewModel.setUpscaleFactor(factor) },
                                        label = { Text(if (factor == 1) "1x" else "${factor}x") },
                                    )
                                }
                            }
                            if (uiState.upscaleFactor == 4) {
                                Text(
                                    "Note: 4x upscale requires significant memory and time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontStyle = FontStyle.Italic
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            // Face Strength Slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Face Strength: ${"%.0f".format(uiState.faceStrength * 100)}%",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    "\u24D8",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable { showFaceStrengthInfo = true },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = uiState.faceStrength,
                                onValueChange = { viewModel.setFaceStrength(it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(8.dp))

                            // Denoise Level Slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Denoise Strength: ${"%.0f".format(uiState.denoiseLevel * 100)}%",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    "\u24D8",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable { showDenoiseInfo = true },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = uiState.denoiseLevel,
                                onValueChange = { viewModel.setDenoiseLevel(it) },
                                valueRange = 0f..0.8f,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            // Advanced Noise Removal + Fast Face Detection row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Advanced Noise Removal")
                                        Text(
                                            "\u24D8",
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .clickable { showAiDenoiseInfo = true },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("Fast Face Detection")
                                        Text(
                                            "\u24D8",
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .clickable { showFaceDetectInfo = true },
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Switch(
                                        checked = uiState.useAiDenoise,
                                        onCheckedChange = { viewModel.toggleAiDenoise(it) },
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Switch(
                                        checked = uiState.faceFastMode,
                                        onCheckedChange = { viewModel.setFaceFastMode(it) },
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Color Vibrancy Slider
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Color Vibrancy: ${"%.1f".format(uiState.colorVibrancy)}x",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    "\u24D8",
                                    modifier = Modifier
                                        .padding(start = 4.dp)
                                        .clickable { showVibrancyInfo = true },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = uiState.colorVibrancy,
                                onValueChange = { viewModel.setColorVibrancy(it) },
                                valueRange = 0.5f..2.0f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Processing Estimate Note
                    val estimatedTime = when {
                        uiState.upscaleFactor == 4 -> "Several minutes"
                        uiState.upscaleFactor == 2 -> "~30-60 seconds"
                        else -> "~10-20 seconds"
                    }
                    Text(
                        text = "Estimated processing time: $estimatedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Button(
                        onClick = { viewModel.startRestoration() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isProcessing,
                    ) {
                        Text("Enhance & Restore")
                    }
                }
            }
        }

        if (uiState.isProcessing) {
            item {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val animatedProgress by animateFloatAsState(
                            targetValue = uiState.progress,
                            label = "progress",
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = uiState.progressText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${(uiState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Device may warm up. Keep app open.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        uiState.restoredBitmap?.let { bitmap ->
            item {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (showOriginal) "Original (Hold for After)" else "Restored (Hold for Before)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (!showOriginal) {
                                uiState.processingTimeMs?.let { ms ->
                                    Text(
                                        text = "${ms / 1000}.${(ms % 1000) / 100}s",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            showOriginal = true
                                            tryAwaitRelease()
                                            showOriginal = false
                                        }
                                    )
                                }
                        ) {
                            if (showOriginal) {
                                AsyncImage(
                                    model = uiState.selectedImageUri,
                                    contentDescription = "Original photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    "BEFORE",
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Restored photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                )
                                Text(
                                    "AFTER",
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.savePhoto() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save Restored Photo")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        uiState.error?.let { error ->
            if (uiState.downloadState != DownloadState.FAILED) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }
    }

    if (showAiDenoiseInfo) {
        AlertDialog(
            onDismissRequest = { showAiDenoiseInfo = false },
            title = { Text("Advanced Noise Removal") },
            text = {
                Text("Uses the SCUNet AI model to deeply clean grain, noise and JPEG artifacts " +
                        "before upscaling. This prevents artifacts from being sharpened, " +
                        "resulting in a much cleaner final image.")
            },
            confirmButton = {
                TextButton(onClick = { showAiDenoiseInfo = false }) { Text("Got it") }
            },
        )
    }

    if (showFaceDetectInfo) {
        AlertDialog(
            onDismissRequest = { showFaceDetectInfo = false },
            title = { Text("Fast Face Detection") },
            text = {
                Text("When enabled, uses Google ML Kit's fast performance mode " +
                        "for face detection. Faster but may miss faces at odd " +
                        "angles or in poor lighting. Disable for more reliable " +
                        "face detection at the cost of speed.")
            },
            confirmButton = {
                TextButton(onClick = { showFaceDetectInfo = false }) { Text("Got it") }
            },
        )
    }

    if (showColorizationInfo) {
        AlertDialog(
            onDismissRequest = { showColorizationInfo = false },
            title = { Text("Colorization") },
            text = { Text("Uses DeOldify to add color to black & white photos. " +
                    "When enabled, the model predicts realistic colors from " +
                    "grayscale input using Lab color space fusion.") },
            confirmButton = {
                TextButton(onClick = { showColorizationInfo = false }) { Text("Got it") }
            },
        )
    }

    if (showRenderFactorInfo) {
        AlertDialog(
            onDismissRequest = { showRenderFactorInfo = false },
            title = { Text("Color Render Factor") },
            text = { Text("Controls the resolution used for colorization. " +
                    "Standard (16) provides fast, consistent colors. " +
                    "High (32) can colorize finer details " +
                    "but take more memory and time.") },
            confirmButton = {
                TextButton(onClick = { showRenderFactorInfo = false }) { Text("Got it") }
            },
        )
    }

    if (showUpscaleInfo) {
        AlertDialog(
            onDismissRequest = { showUpscaleInfo = false },
            title = { Text("Upscale Factor") },
            text = { Text("The multiplier for image resolution. 2x doubles " +
                    "width and height (4x pixels). 4x quadruples both " +
                    "dimensions (16x pixels). Higher factors take more " +
                    "time and memory.") },
            confirmButton = {
                TextButton(onClick = { showUpscaleInfo = false }) { Text("Got it") }
            },
        )
    }

    if (showFaceStrengthInfo) {
        AlertDialog(
            onDismissRequest = { showFaceStrengthInfo = false },
            title = { Text("Face Strength") },
            text = { Text("Controls how strongly AI reconstruction is applied to faces. " +
                    "Higher values produce sharper, more restored faces but may slightly alter facial details. " +
                    "Lower values preserve more of the original appearance.") },
            confirmButton = {
                TextButton(onClick = { showFaceStrengthInfo = false }) { Text("Got it") }
            },
        )
    }

    if (showDenoiseInfo) {
        AlertDialog(
            onDismissRequest = { showDenoiseInfo = false },
            title = { Text("Noise Cleanup Strength") },
            text = { Text("Controls how much of the AI noise removal is " +
                    "blended into the photo. Higher values create a smoother " +
                    "look; lower values retain more of the original texture.") },
            confirmButton = {
                TextButton(onClick = { showDenoiseInfo = false }) { Text("Got it") }
            },
        )
    }

    if (showVibrancyInfo) {
        AlertDialog(
            onDismissRequest = { showVibrancyInfo = false },
            title = { Text("Color Vibrancy") },
            text = { Text("Multiplies the saturation of predicted colors " +
                    "during colorization. Values above 1.0 produce more " +
                    "vivid colors; below 1.0 gives a more muted look. " +
                    "Only affects colorized images.") },
            confirmButton = {
                TextButton(onClick = { showVibrancyInfo = false }) { Text("Got it") }
            },
        )
    }

    if (uiState.showSaveConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveConfirmation() },
            title = { Text("Photo Saved") },
            text = {
                Column {
                    Text("Your restored photo has been saved successfully.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Location: ${uiState.savePath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextButton(
                        onClick = { viewModel.dismissSaveConfirmation() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("OK")
                    }
                    Button(
                        onClick = {
                            viewModel.dismissSaveConfirmation()
                            viewModel.openGallery()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Open Gallery")
                    }
                }
            },
        )
    }
}
