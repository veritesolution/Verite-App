#!/usr/bin/env python3
"""Brain-Emotion System — train / serve / test"""
import argparse, sys

def cmd_train(args):
    from brain_emotion.trainer import train_and_validate
    train_and_validate(args.data_dir, args.output)

def cmd_serve(args):
    from brain_emotion.api_server import run_server
    run_server(args.model, args.host, args.port)

def cmd_test(args):
    import subprocess, os
    cwd = os.path.dirname(os.path.abspath(__file__))
    result = subprocess.run([sys.executable, os.path.join(cwd, "tests", "test_core.py")], cwd=cwd)
    sys.exit(result.returncode)

def main():
    from brain_emotion.config import VERSION
    p = argparse.ArgumentParser(description=f"Brain-Emotion v{VERSION}")
    sub = p.add_subparsers(dest="cmd")
    pt = sub.add_parser("train", help="Train on DEAP dataset")
    pt.add_argument("--data-dir", default="./data_preprocessed_python")
    pt.add_argument("--output", default="model_v13.pkl")
    ps = sub.add_parser("serve", help="Run API server")
    ps.add_argument("--model", default="model_v13.pkl")
    ps.add_argument("--host", default="0.0.0.0")
    ps.add_argument("--port", type=int, default=8080)
    sub.add_parser("test", help="Run unit tests")
    args = p.parse_args()
    if args.cmd == "train": cmd_train(args)
    elif args.cmd == "serve": cmd_serve(args)
    elif args.cmd == "test": cmd_test(args)
    else: p.print_help()

if __name__ == "__main__":
    main()
