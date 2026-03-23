"""
Train ArousalPredictor on MESA/SHHS.

Usage:
    verite-train-arousal --mesa-dir /data/mesa/ --output arousal_gbc.joblib
    verite-train-arousal --check-environment
"""

def main() -> None:
    import argparse, sys
    parser = argparse.ArgumentParser(description="Train ArousalPredictor on MESA/SHHS")
    parser.add_argument("--mesa-dir", type=str, default="")
    parser.add_argument("--output", type=str, default="arousal_gbc.joblib")
    parser.add_argument("--check-environment", action="store_true")
    parser.add_argument("--validate", action="store_true")
    parser.add_argument("--model-path", type=str, default="")
    args = parser.parse_args()

    if args.check_environment:
        checks = []
        try:
            import sklearn; checks.append(f"✅ scikit-learn {sklearn.__version__}")
        except: checks.append("❌ scikit-learn: pip install scikit-learn")
        try:
            import joblib; checks.append(f"✅ joblib")
        except: checks.append("❌ joblib: pip install joblib")
        try:
            import mne; checks.append(f"✅ MNE {mne.__version__}")
        except: checks.append("⚠ MNE not installed (needed for EDF loading)")
        print("Environment check:")
        for c in checks: print(f"  {c}")
        return

    if args.validate:
        from verite_tmr.detection.arousal import ArousalPredictor
        ap = ArousalPredictor(model_path=args.model_path or args.output)
        print(f"Model: mode={ap.mode}, trained={ap.is_trained}")
        return

    if not args.mesa_dir:
        print("Error: provide --mesa-dir")
        print("  Download MESA: https://sleepdata.org/datasets/mesa")
        sys.exit(1)

    from verite_tmr.detection.arousal import ArousalPredictor
    result = ArousalPredictor.train_on_mesa(args.mesa_dir, args.output)
    print(f"\nTraining result: {result}")

if __name__ == "__main__":
    main()
