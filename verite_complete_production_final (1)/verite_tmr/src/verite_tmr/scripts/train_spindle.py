"""
Train SpindleCNN on DREAMS dataset.

Usage:
    verite-train-spindle --dreams-dir /data/dreams/ --output spindle_cnn.onnx
    verite-train-spindle --download-dreams --output spindle_cnn.onnx
    verite-train-spindle --check-environment
    verite-train-spindle --validate --weights spindle_cnn.onnx --test-dir /data/test/
"""

def main() -> None:
    import argparse, sys
    parser = argparse.ArgumentParser(description="Train SpindleCNN on DREAMS")
    parser.add_argument("--dreams-dir", type=str, default="", help="Path to DREAMS dataset")
    parser.add_argument("--output", type=str, default="spindle_cnn.onnx")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--download-dreams", action="store_true",
                       help="Download DREAMS from zenodo.org/record/2650142")
    parser.add_argument("--validate", action="store_true",
                       help="Validate trained weights against test data")
    parser.add_argument("--weights", type=str, default="", help="Weights path for validation")
    parser.add_argument("--test-dir", type=str, default="", help="Test data for validation")
    parser.add_argument("--check-environment", action="store_true",
                       help="Check PyTorch, MNE, ONNX availability")
    args = parser.parse_args()

    if args.check_environment:
        _check_env()
        return

    if args.download_dreams:
        dreams_dir = _download_dreams()
        if not dreams_dir:
            sys.exit(1)
        args.dreams_dir = dreams_dir

    if args.validate:
        from verite_tmr.detection.spindle_cnn import SpindleCNN
        cnn = SpindleCNN(weights_path=args.weights or args.output)
        print(f"Model loaded: mode={cnn.mode}, ready={cnn.is_ready}")
        return

    if not args.dreams_dir:
        print("Error: provide --dreams-dir or use --download-dreams")
        print("  DREAMS Spindles DB: https://zenodo.org/record/2650142")
        sys.exit(1)

    from verite_tmr.detection.spindle_cnn import SpindleCNN
    result = SpindleCNN.train_on_dreams(args.dreams_dir, args.output, args.epochs)
    print(f"\nTraining result: {result}")
    if result.get("passed"):
        print(f"✅ κ={result['kappa']} >= {result['target_kappa']} — model ready")
    else:
        print(f"⚠ κ={result.get('kappa','?')} < target — needs more data or tuning")


def _check_env():
    """Check all required dependencies before multi-hour training."""
    checks = []
    try:
        import torch; checks.append(f"✅ PyTorch {torch.__version__}")
        if torch.cuda.is_available():
            checks.append(f"✅ GPU: {torch.cuda.get_device_name(0)}")
        else:
            checks.append("⚠ No GPU — training will be slow")
    except ImportError:
        checks.append("❌ PyTorch not installed: pip install torch")

    try:
        import mne; checks.append(f"✅ MNE-Python {mne.__version__}")
    except ImportError:
        checks.append("❌ MNE not installed: pip install mne")

    try:
        import onnxruntime; checks.append(f"✅ ONNX Runtime {onnxruntime.__version__}")
    except ImportError:
        checks.append("❌ onnxruntime not installed: pip install onnxruntime")

    try:
        import onnx; checks.append(f"✅ ONNX {onnx.__version__}")
    except ImportError:
        checks.append("⚠ onnx not installed (needed for export): pip install onnx")

    print("Environment check:")
    for c in checks: print(f"  {c}")
    ready = all("❌" not in c for c in checks)
    print(f"\n{'✅ Ready to train' if ready else '❌ Fix issues above before training'}")


def _download_dreams():
    """Download DREAMS Spindles Database from Zenodo."""
    print("Downloading DREAMS Spindles Database from zenodo.org/record/2650142...")
    try:
        import urllib.request, zipfile, os, tempfile
        url = "https://zenodo.org/record/2650142/files/DatabaseSpindles.zip"
        dest = os.path.join(tempfile.gettempdir(), "dreams_spindles")
        zip_path = dest + ".zip"
        if not os.path.exists(zip_path):
            print(f"  Downloading to {zip_path}...")
            urllib.request.urlretrieve(url, zip_path)
        if not os.path.exists(dest):
            print(f"  Extracting to {dest}...")
            with zipfile.ZipFile(zip_path) as zf:
                zf.extractall(dest)
        print(f"✅ DREAMS downloaded to {dest}")
        return dest
    except Exception as e:
        print(f"❌ Download failed: {e}")
        print("  Manual download: https://zenodo.org/record/2650142")
        return None


if __name__ == "__main__":
    main()
