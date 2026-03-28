"""
verite CLI — Command-line interface for TMR sessions.

Usage:
    verite run --doc notes.pdf --simulate
    verite run --doc notes.pdf --ws ws://192.168.1.5:8765 --hours 8
    verite validate --config config.json
    verite train-spindle --dreams-dir /path/to/dreams/ --output model.onnx
"""

from __future__ import annotations

import argparse
import sys


def main() -> None:
    parser = argparse.ArgumentParser(
        prog="verite",
        description="Vérité TMR — Closed-Loop Targeted Memory Reactivation",
    )
    sub = parser.add_subparsers(dest="command")

    # Run command
    run_p = sub.add_parser("run", help="Run a TMR session")
    run_p.add_argument("--doc", type=str, help="Study material document path")
    run_p.add_argument("--config", type=str, help="Config JSON path")
    run_p.add_argument("--simulate", action="store_true", help="Simulation mode")
    run_p.add_argument("--ws", type=str, default="", help="NeuroBand WebSocket URI")
    run_p.add_argument("--hours", type=float, default=8.0, help="Session hours")
    run_p.add_argument("--replay", type=str, default="", help="PSG CSV for replay")

    # Validate command
    val_p = sub.add_parser("validate", help="Validate configuration and readiness")
    val_p.add_argument("--config", type=str, help="Config JSON path")
    val_p.add_argument("--mode", choices=["simulation", "live"], default="simulation")

    # Train commands
    ts_p = sub.add_parser("train-spindle", help="Train SpindleCNN on DREAMS data")
    ts_p.add_argument("--dreams-dir", type=str, required=True)
    ts_p.add_argument("--output", type=str, default="spindle_cnn.onnx")
    ts_p.add_argument("--epochs", type=int, default=50)

    ta_p = sub.add_parser("train-arousal", help="Train ArousalPredictor on MESA/SHHS")
    ta_p.add_argument("--mesa-dir", type=str, required=True)
    ta_p.add_argument("--output", type=str, default="arousal_gbc.joblib")

    args = parser.parse_args()

    if args.command == "run":
        _cmd_run(args)
    elif args.command == "validate":
        _cmd_validate(args)
    elif args.command == "train-spindle":
        _cmd_train_spindle(args)
    elif args.command == "train-arousal":
        _cmd_train_arousal(args)
    else:
        parser.print_help()


def _cmd_run(args: argparse.Namespace) -> None:
    from verite_tmr import VeriteSession
    from verite_tmr.config import load_config

    config = load_config(args.config)
    session = VeriteSession(config=config)

    mode = "simulation" if args.simulate else ("replay" if args.replay else "live")
    result = session.run(mode=mode, hours=args.hours, document_path=args.doc)
    print(f"\n✅ Session complete: {result}")


def _cmd_validate(args: argparse.Namespace) -> None:
    from verite_tmr.config import load_config
    from verite_tmr.safety import validate_pipeline

    config = load_config(args.config)
    report = validate_pipeline(config, mode=args.mode)

    print("\n🔬 Pipeline Validation Report")
    print("=" * 60)
    for component, info in report.items():
        if component.startswith("_"):
            continue
        print(f"  {info['status']:40s}  [{info['level']}]")
        if info.get("detail"):
            print(f"    → {info['detail']}")
    print("=" * 60)


def _cmd_train_spindle(args: argparse.Namespace) -> None:
    from verite_tmr.detection import SpindleCNN
    result = SpindleCNN.train_on_dreams(
        dreams_dir=args.dreams_dir,
        output_path=args.output,
        epochs=args.epochs,
    )
    print(f"\nSpindleCNN training: {result}")


def _cmd_train_arousal(args: argparse.Namespace) -> None:
    from verite_tmr.detection import ArousalPredictor
    result = ArousalPredictor.train_on_mesa(
        mesa_dir=args.mesa_dir,
        output_path=args.output,
    )
    print(f"\nArousalPredictor training: {result}")


if __name__ == "__main__":
    main()
