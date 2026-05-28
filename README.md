# Makokolor

AI-powered photo restoration & enhancement for Android. 
Makokolor restores old, faded, or damaged photos directly on your device — no cloud needed. It upscales resolution, removes noise/artifacts, restores faces, and colorizes black-and-white images using on-device ONNX models.

**NOTE: This app runs as a completely offline project. The AI models will download to the device on first run. These models are quite large and processor-heavy. Do not attempt to run these on older devices. Having said that, I assume no responsibility if you overheat or damage your device by using this project. This project does build and run, but it is currently in an Alpha State.**

## Pipeline
INPUT → SCUnet (denoise/deblur) → Real-ESRGAN x4 (upscale) → CodeFormer (face restoration) → Deoldify (colorization) → OUTPUT

| Stage | Model | What it does |
|---|---|---|
| **Denoising** | SCUnet | Removes sensor noise, JPEG artifacts, and blur before upscaling |
| **Upscaling** | Real-ESRGAN x4 | 4x resolution increase with fine detail enhancement |
| **Face Restoration** | CodeFormer | Natural face repair and clarity — better than GFPGAN |
| **Colorization** | Deoldify Artistic | Vibrant B&W colorization & deep color correction |

## Features
* **Fully on-device** — all processing via ONNX Runtime, no internet after model download.
* **4-stage pipeline** — clean noise → upscale → restore faces → colorize in one tap.
* **Save to gallery** — results saved cleanly to `Pictures/MakokolorAI`.
* **High Performance** — ~10–15s per image on modern devices (Galaxy S24).
* **Direct Asset Streaming** — efficient one-time model downloads directly into app sandbox storage.

## Requirements
* Android 9+ (API 28)
* ~800MB free storage for models
* Wi-Fi recommended for initial setup download

## Models
Downloaded once on first launch from project releases / model hubs:

| Model | Size | Source |
|---|---|---|
| **scunet.onnx** | ~50MB | cszn/SCUnet |
| **Real-ESRGAN-x4plus.onnx** | ~67MB | qualcomm/Real-ESRGAN-x4plus |
| **codeformer.onnx** | ~377MB | facefusion/models-3.0.0 |
| **deoldify-art.onnx** | ~200MB | facefusion/models-3.0.0 |

## Tech Stack
* **Kotlin + Jetpack Compose** — Modern native UI architecture
* **ONNX Runtime for Android** — On-device hardware-accelerated inference
* **Coroutines + Flow** — For asynchronous pipeline execution and state tracking

## Build
```bash
./gradlew assembleDebug
