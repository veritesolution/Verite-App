# 🧠 Vérité TMR v10.0 — Closed-Loop Targeted Memory Reactivation

A scientifically-grounded, production-validated system for delivering targeted memory reactivation (TMR) cues during sleep to enhance memory consolidation.

## What Changed: v9 → v10

v10 is a **complete architectural rebuild** addressing every issue from the independent peer review and production readiness audit. The notebook is now a proper Python package.

### Critical Fixes (Experiment-Blocking)

| ID | Issue | v9 Status | v10 Status |
|----|-------|-----------|------------|
| T0-1 | Phase estimation | LMS/AR only (not validated) | **ECHT implemented** (Zrenner 2020) |
| T0-2 | SpindleCNN weights | WEIGHTS_URL empty → returns 0.0 | **Training pipeline + DREAMS integration** |
| T1-1 | K-complex detection | Missing entirely | **Implemented** with calibration |
| T1-2 | hilbert_buffer trap | Selectable but non-causal | **Removed** from valid options |
| T1-4 | Arousal predictor | Empty path, no model | **MESA/SHHS training pipeline** |
| T1-5 | ICA artefact rejection | Referenced but not connected | **MNE-Python integration** |
| T1-6 | Online weight learning | Runs from session 1 | **Gated at 30 sessions minimum** |

### Architectural Fixes

| ID | Issue | v9 Status | v10 Status |
|----|-------|-----------|------------|
| T2-1 | Python package | 78 cells, global vars | **`pip install verite-tmr`** |
| T2-2 | Tests | Range checks only | **Correctness tests** (known input → expected output) |
| T2-3 | CI/CD | None | **GitHub Actions** (pytest + mypy + latency regression) |
| T2-4 | Docker | None | **Dockerfile + docker-compose** |
| T2-5 | PSG replay | CSV only | **MNE-Python EDF ingestion** |

### New Capabilities

| ID | Feature | Description |
|----|---------|-------------|
| T3-1 | Contextual bandit | LinUCB replaces heuristic delivery policy |
| T3-2 | SO-spindle coupling | PAC modulation index as delivery criterion |
| T3-3 | Cue calibration | Per-participant TTS parameter optimization |
| T3-6 | GDPR compliance | Pseudonymization, right-to-erasure, retention |
| T3-7 | Pre-registration | Protocol hash + OSF integration |

## Installation

```bash
# Core installation
pip install verite-tmr

# With all optional dependencies
pip install "verite-tmr[all]"

# Development
pip install "verite-tmr[dev]"
```

## Quick Start

```python
from verite_tmr import VeriteSession, Config

# Create session with custom config
config = Config(
    phase_predictor="echt",      # production phase estimation
    use_polly=True,              # Amazon Polly TTS
    pac_enabled=True,            # SO-spindle coupling gate
    kcomplex_enabled=True,       # K-complex detection
)

session = VeriteSession(config=config)
report = session.initialize(mode="simulation")
print(report)

results = session.run(mode="simulation", hours=8.0)
```

## CLI Usage

```bash
# Simulation run
verite run --doc notes.pdf --simulate

# Live overnight session
verite run --doc notes.pdf --ws ws://192.168.1.5:8765 --hours 8

# Validate readiness
verite validate --config config.json --mode live

# Train models
verite train-spindle --dreams-dir /data/dreams/ --output spindle_cnn.onnx
verite train-arousal --mesa-dir /data/mesa/ --output arousal_gbc.joblib
```

## Docker Deployment

```bash
docker build -t verite-tmr .
docker run -v /data:/data verite-tmr verite validate --mode simulation
```

## Package Structure

```
verite_tmr/
├── pyproject.toml
├── Dockerfile
├── .github/workflows/ci.yml
├── src/verite_tmr/
│   ├── __init__.py
│   ├── config.py              # Centralized, validated configuration
│   ├── session.py             # Main session runner
│   ├── orchestrator.py        # Delivery gate + cue selection + LinUCB bandit
│   ├── safety.py              # Pipeline validation + GDPR + pre-registration
│   ├── cli.py                 # Command-line interface
│   ├── phase/                 # Phase estimation algorithms
│   │   ├── base.py            # Abstract base + factory + synthetic validation
│   │   ├── echt.py            # ECHT (Zrenner 2020) — RECOMMENDED
│   │   ├── ar.py              # Burg AR(30) — backup
│   │   └── lms.py             # LMS — demo only
│   ├── detection/             # Sleep event detection
│   │   ├── spindle_cnn.py     # CNN + DREAMS training + ONNX inference
│   │   ├── kcomplex.py        # K-complex detector (NEW)
│   │   ├── arousal.py         # GBC predictor + MESA training
│   │   ├── artefact.py        # Rule-based + ICA (MNE-Python)
│   │   └── coupling.py        # SO-spindle PAC (NEW)
│   ├── memory/                # Memory assessment
│   │   └── assessor.py        # Sweet-spot targeting + formula validation
│   └── audio/                 # TTS generation
│       └── __init__.py        # Polly → gTTS → noise fallback
├── tests/
│   └── test_correctness.py    # Correctness tests (not just range checks)
├── notebooks/                 # Demo notebooks
└── scripts/                   # Training scripts
```

## Running Tests

```bash
# All tests
pytest tests/ -v

# Correctness tests only
pytest tests/test_correctness.py -v

# Skip slow tests
pytest tests/ -m "not slow"

# With coverage
pytest tests/ --cov=verite_tmr --cov-report=html
```

## Build Order (for contributors)

1. **Month 1-2**: Train SpindleCNN on DREAMS + package structure (done in v10)
2. **Month 3**: Validate ECHT on real PSG data
3. **Month 4**: K-complex detector + ICA integration + MNE replay
4. **Month 5**: Train ArousalPredictor on MESA + TTS pilot
5. **Month 6**: Correctness tests + CI/CD + Docker + internal pilot (5 participants)
6. **Month 7-9**: Contextual bandit + PAC coupling + real-time dashboard
7. **Month 10+**: Cue calibration, multimodal, GDPR, pre-registration

## References

- Antony et al. (2012) *Nat Neurosci* — TMR sweet-spot targeting
- Ngo et al. (2013) *Neuron* — Closed-loop SO auditory stimulation
- Staresina et al. (2015) *Nat Neurosci* — SO-spindle nesting
- Zrenner et al. (2020) *Brain Stimulation* — ECHT phase estimator
- Tort et al. (2010) — Modulation index for PAC
- Mölle et al. (2002) — SO phase classification
- Chambon et al. (2018) — Spindle detection CNN architecture
- Rudoy et al. (2009) — Sleep TMR with auditory cues

## License

MIT

## Version History

### v10.5 (current)
| ID | Issue | v10.4 Status | v10.5 Status |
|----|-------|-------------|--------------|
| P0-A | Null assertion in run_tests.py | `or True` (untestable) | **Real assertion** |
| P0-B | CI benchmarks wrong estimator | ECHTEstimator | **CausalPhaseEstimator** |
| P0-C | Band-edge test missing from pytest | run_tests.py only | **test_correctness.py** |
| P0-D | Simulation phase stuck in 57° sector | Fixed offset | **Random offset — full 360°** |
| P1-A | GDPR pseudonymize not in data path | Instantiated only | **Called on every tracker write** |
| P1-B | No sim phase quality in pytest | run_tests.py only | **TestSimulation class** |
| P2-A | SpindleCNN no default URL | Empty | **DEFAULT_WEIGHTS_URL + auto-download** |
| P2-B | Memory formula not in session report | Silent | **Reported with validation guidance** |
| P2-C | No real-PSG validation method | Synthetic only | **validate_on_real_psg() scaffolded** |
| P3-A | Single-channel EEG only | Hard-coded | **Multi-channel scaffolding** |
| P3-B | TTS intelligibility not validated | Unaddressed | **TTSIntelligibilityValidator** |
| P3-C | No README changelog | Missing | **Full version history** |

### v10.4
- Phase estimator pre-warmed (1500 samples in __init__)
- CausalPhaseEstimator band-edge limitation documented
- GDPRDataManager wired into session.initialize()
- Live-mode hard interlock (RuntimeError blocks untrained components)

### v10.3
- Renamed from "Production" to "Research Platform"
- Safety gates: hard interlocks (not advisory)
- Simulation: physics-based spindle/arousal (phase-correlated)

### v10.1 → v10.2
- ECHT: true Zrenner 2020 algorithm (causal sosfilt + Burg AR + predict)
- CausalPhaseEstimator: honest naming (~18° mean, NOT Zrenner)
- 3 empty modules restored (hardware, document, analysis)
- K-complex test: asserts actual detection
- Session wires all modules end-to-end
- OSF.io upload implemented

### v9 → v10 (architectural rebuild)
- 78-cell notebook → pip install verite-tmr
- Global variables → typed Python package
- 20 range-check tests → 34+ correctness tests
- GitHub Actions CI/CD + Docker
- ECHT + K-complex + PAC coupling + LinUCB bandit
- Model plugin pipeline for custom EEG/HRV/EMG models
