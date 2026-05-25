# Makokolor

**AI-powered photo restoration & enhancement for Android.**

Makokolor restores old, faded, or damaged photos directly on your device — no cloud needed. It upscales resolution, restores faces, and colorizes black-and-white images using on-device ONNX models.

## Pipeline

```
INPUT → Real-ESRGAN x4 (upscale) → CodeFormer (face restoration) → Deoldify (colorization) → OUTPUT
```

| Stage | Model | What it does |
|-------|-------|-------------|
| **Upscaling** | Real-ESRGAN x4 | 4x resolution increase with detail enhancement |
| **Face Restoration** | CodeFormer | Natural face repair — better than GFPGAN |
| **Colorization** | Deoldify Artistic | Vibrant B&W colorization & color correction |

## Features

- **Fully on-device** — all processing via ONNX Runtime, no internet after model download
- **3-stage pipeline** — upscale → restore faces → colorize in one tap
- **Save to gallery** — results saved to `Pictures/MakokolorAI`
- **~10–15s per image** on modern devices (Galaxy S24)
- **~510MB** one-time model download

## Requirements

- Android 9+ (API 28)
- ~600MB free storage for models
- Wi-Fi recommended for initial download

## Models

Downloaded once on first launch from Hugging Face:

| Model | Size | Source |
|-------|------|--------|
| `Real-ESRGAN-x4plus.onnx` | ~67MB | qualcomm/Real-ESRGAN-x4plus |
| `codeformer.onnx` | ~377MB | facefusion/models-3.0.0 |
| `deoldify-art.onnx` | ~200MB | facefusion/models-3.0.0 |

## Tech Stack

- **Kotlin** + Jetpack Compose
- **ONNX Runtime** for Android
- **Coroutines** + Flow for async pipeline

## Build

```bash
./gradlew assembleDebug
```

## License

MIT
