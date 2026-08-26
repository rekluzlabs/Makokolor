# Makokolor

# Please note: This app is no longer being developed. The app has been redesigned and is now called MakoRevive and is live on the Google Playstore. It is still free to download and does not contain any ads or IAP nor subscriptions. ([Download link](https://play.google.com/store/apps/details?id=com.rekluzlabs.makorevive ))

AI-powered photo restoration & enhancement for Android. 
Makokolor restores old, faded, or damaged photos directly on your device — no cloud needed. It upscales resolution, removes noise/artifacts, restores faces, and colorizes black-and-white images using on-device ONNX models.

**NOTE: Source code file links are deprecated as the host has been changed to Hugging face. I will update this page soon once I return to working on this project.  This project does build and run with the proper links, but it is currently in an Alpha State.**


BEFORE<br>
<img alt="makokolor_before_testimage" src="https://github.com/user-attachments/assets/da7e334a-2332-4107-b46c-2ad100b87185" width="400">

AFTER<br>
<img alt="makokolor_AFTER" src="https://github.com/user-attachments/assets/5dbfbbbb-94f3-4bdc-99a4-23f873487cbf" width="400">

Testing was done on a Samsung Galaxy S24

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
* Android 34+ recommended but may work on older versions.
* ~800MB free storage for models
* Wi-Fi for initial setup download

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

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
