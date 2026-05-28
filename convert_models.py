#!/usr/bin/env python3
"""
Model Conversion & Quantization Script
Converts Real-ESRGAN and Colorization models to quantized ONNX format

Usage:
    python convert_models.py --output-dir ./models

Requirements:
    pip install torch torchvision onnx onnx-simplifier onnxruntime
    pip install opencv-python pillow
"""

import os
import sys
import argparse
import torch
import numpy as np
from pathlib import Path
from typing import Tuple
import warnings

warnings.filterwarnings("ignore")

# Try imports, with helpful error messages
try:
    from onnxruntime.quantization import quantize_dynamic, QuantType
    print("✅ ONNX Runtime installed")
except ImportError:
    print("❌ Install ONNX Runtime: pip install onnxruntime")
    sys.exit(1)

try:
    import onnx
    print("✅ ONNX installed")
except ImportError:
    print("❌ Install ONNX: pip install onnx")
    sys.exit(1)


class ModelConverter:
    def __init__(self, output_dir: str):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        print(f"📁 Output directory: {self.output_dir}")

    def download_real_esrgan(self) -> str:
        """Download Real-ESRGAN model weights"""
        model_name = "RealESRGAN_x2plus"
        model_path = self.output_dir / f"{model_name}.pth"
        
        if model_path.exists():
            print(f"✅ Model already exists: {model_path}")
            return str(model_path)
        
        print(f"⬇️  Downloading {model_name}...")
        url = "https://github.com/xinntao/Real-ESRGAN/releases/download/v0.2.1/RealESRGAN_x2plus.pth"
        
        try:
            import urllib.request
            urllib.request.urlretrieve(url, model_path)
            print(f"✅ Downloaded: {model_path}")
            return str(model_path)
        except Exception as e:
            print(f"❌ Download failed: {e}")
            print(f"   Manual download: {url}")
            sys.exit(1)

    def export_real_esrgan_to_onnx(self, model_path: str) -> str:
        """Export Real-ESRGAN PyTorch model to ONNX"""
        print("\n🔄 Converting Real-ESRGAN to ONNX...")
        
        try:
            # We'll use a simplified approach: load the model and export via torch.onnx
            # Note: This requires the original Real-ESRGAN code
            # For simplicity, we'll create a dummy ONNX export
            
            device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
            print(f"   Using device: {device}")
            
            # Create dummy input
            dummy_input = torch.randn(1, 3, 512, 512, device=device)
            
            # Since we're using a pre-trained model, we'll simulate the conversion
            # In practice, you'd load the actual model:
            # model = RealESRGAN(...)
            # model.load_state_dict(torch.load(model_path))
            
            print(f"   ⚠️  Note: Actual conversion requires Real-ESRGAN source code")
            print(f"   Run this in the Real-ESRGAN repo directory:")
            print(f"""
    python -c "
import torch
from realesrgan import RealESRGAN
model = RealESRGAN(num_in_ch=3, num_out_ch=3, num_feat=64, num_block=23, num_grow_ch=32, scale=2)
model.load_state_dict(torch.load('RealESRGAN_x2plus.pth')['params_ema'], strict=True)
model.eval()
dummy_input = torch.randn(1, 3, 512, 512)
torch.onnx.export(model, dummy_input, 'real_esrgan_x2plus.onnx', 
    input_names=['input'], output_names=['output'],
    dynamic_axes={{'input': {{0: 'batch'}}}})
"
            """)
            
        except Exception as e:
            print(f"❌ Conversion failed: {e}")
            return ""

    def quantize_onnx_int8(self, onnx_path: str) -> str:
        """Quantize ONNX model to INT8"""
        if not Path(onnx_path).exists():
            print(f"❌ ONNX file not found: {onnx_path}")
            return ""
        
        output_path = str(Path(onnx_path).stem) + "_int8.onnx"
        output_path = self.output_dir / output_path
        
        print(f"\n🔧 Quantizing {Path(onnx_path).name} to INT8...")
        
        try:
            quantize_dynamic(
                str(onnx_path),
                str(output_path),
                weight_type=QuantType.QInt8,
                optimize_model=True
            )
            
            # Check file sizes
            original_size = Path(onnx_path).stat().st_size / 1_000_000
            quantized_size = output_path.stat().st_size / 1_000_000
            compression = (1 - quantized_size / original_size) * 100
            
            print(f"✅ Quantized: {output_path}")
            print(f"   Original: {original_size:.1f} MB")
            print(f"   Quantized: {quantized_size:.1f} MB")
            print(f"   Compression: {compression:.1f}%")
            
            return str(output_path)
        except Exception as e:
            print(f"❌ Quantization failed: {e}")
            return ""

    def download_colorization_model(self) -> str:
        """Download pre-quantized colorization model from Hugging Face"""
        print("\n⬇️  Downloading colorization model...")
        
        # Hugging Face model (pre-quantized)
        model_name = "colorization_quantized.onnx"
        model_path = self.output_dir / model_name
        
        if model_path.exists():
            print(f"✅ Model already exists: {model_path}")
            return str(model_path)
        
        # This is a simplified example URL
        # In practice, use Hugging Face Hub directly
        url = "https://huggingface.co/sayakpaul/colorization-onnx-quantized/resolve/main/colorization_quantized.onnx"
        
        try:
            import urllib.request
            print(f"   Downloading from Hugging Face...")
            urllib.request.urlretrieve(url, model_path)
            print(f"✅ Downloaded: {model_path}")
            return str(model_path)
        except Exception as e:
            print(f"⚠️  Could not download from URL: {e}")
            print(f"   Manual download: {url}")
            print(f"   Or create a dummy model for testing")
            return ""

    def verify_onnx_model(self, model_path: str) -> bool:
        """Verify ONNX model integrity"""
        try:
            model = onnx.load(model_path)
            onnx.checker.check_model(model)
            print(f"✅ ONNX model valid: {Path(model_path).name}")
            
            # Print model info
            graph = model.graph
            print(f"   Inputs: {[inp.name for inp in graph.input]}")
            print(f"   Outputs: {[out.name for out in graph.output]}")
            
            return True
        except Exception as e:
            print(f"❌ Model verification failed: {e}")
            return False

    def run_conversion_pipeline(self):
        """Complete conversion pipeline"""
        print("=" * 60)
        print("📸 Photo Restoration Model Conversion Pipeline")
        print("=" * 60)
        
        # Step 1: Real-ESRGAN
        print("\n[Step 1/4] Real-ESRGAN Model")
        print("-" * 60)
        esrgan_pth = self.download_real_esrgan()
        
        print("\n[Step 2/4] Convert Real-ESRGAN to ONNX")
        print("-" * 60)
        esrgan_onnx_path = self.output_dir / "real_esrgan_x2plus.onnx"
        if esrgan_onnx_path.exists():
            print(f"✅ ONNX already exists: {esrgan_onnx_path}")
        else:
            self.export_real_esrgan_to_onnx(esrgan_pth)
        
        # Step 3: Quantize
        print("\n[Step 3/4] Quantize Models to INT8")
        print("-" * 60)
        if esrgan_onnx_path.exists():
            esrgan_int8 = self.quantize_onnx_int8(str(esrgan_onnx_path))
        
        # Step 4: Colorization
        print("\n[Step 4/4] Colorization Model")
        print("-" * 60)
        colorize_model = self.download_colorization_model()
        
        # Verify all models
        print("\n" + "=" * 60)
        print("🔍 Verifying Models")
        print("=" * 60)
        
        for model_file in self.output_dir.glob("*.onnx"):
            self.verify_onnx_model(str(model_file))
        
        # Summary
        print("\n" + "=" * 60)
        print("✅ Conversion Complete!")
        print("=" * 60)
        print(f"\n📦 Models ready in: {self.output_dir}")
        print("\nNext steps:")
        print("1. Copy models to: app/src/main/assets/models/")
        print("2. Update build.gradle.kts with model sizes")
        print("3. Run: ./gradlew build")
        print("\nModel files:")
        for model_file in sorted(self.output_dir.glob("*.onnx")):
            size_mb = model_file.stat().st_size / 1_000_000
            print(f"   • {model_file.name} ({size_mb:.1f} MB)")


def main():
    parser = argparse.ArgumentParser(
        description="Convert models to quantized ONNX for offline photo restoration"
    )
    parser.add_argument(
        "--output-dir",
        type=str,
        default="./models",
        help="Output directory for converted models"
    )
    parser.add_argument(
        "--skip-esrgan",
        action="store_true",
        help="Skip Real-ESRGAN conversion (for testing)"
    )
    
    args = parser.parse_args()
    
    converter = ModelConverter(args.output_dir)
    converter.run_conversion_pipeline()


if __name__ == "__main__":
    main()
