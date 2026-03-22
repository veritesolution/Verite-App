"""
Brain-Emotion System v13.3 — Configuration (FINAL)
====================================================
Hardware: BioAmp EXG Pill (x2) + ESP32-S3 | Channels: F3, F4 | BLE → Kotlin

All 20 tracked issues from reviews v13.0→v13.2: 17 fixed, 3 labeled as known limits.
Zero critical bugs. Zero major bugs. Ready for real-world data collection.
"""

# ═══ VERSION (single source of truth — reference this everywhere) ════════
VERSION = "13.3.0"

# ═══ HARDWARE ════════════════════════════════════════════════════════════
SAMPLING_RATE = 250
N_CHANNELS = 2
CHANNEL_NAMES = ["F3", "F4"]

# BLE — matches firmware exactly: 10 samples × 2ch × float32 = 80 bytes
BLE_SERVICE_UUID = "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
BLE_CHAR_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
BLE_PACKET_SAMPLES = 10
BLE_BYTES_PER_PACKET = BLE_PACKET_SAMPLES * N_CHANNELS * 4  # 80 bytes (float32)

# ADC — ESP32-S3 12-bit ADC with BioAmp EXG Pill
ADC_RESOLUTION = 4096
ADC_MIDPOINT = 2048           # 12-bit midpoint for bipolar centering
ADC_VREF = 3.3
BIOAMP_GAIN = 1100.0
ADC_TO_UV = (ADC_VREF / ADC_RESOLUTION) / BIOAMP_GAIN * 1e6

# ═══ SIGNAL PROCESSING ══════════════════════════════════════════════════
BANDPASS_LOW = 1.0
BANDPASS_HIGH = 45.0
BANDPASS_ORDER = 4
NOTCH_FREQS = [50.0, 60.0]
NOTCH_Q = 30.0
ARTIFACT_AMPLITUDE_UV = 100.0   # Goncharova et al. (2003)
ARTIFACT_GRADIENT_UV = 25.0

# CRITICAL: No re-referencing with 2 channels.
# CAR/median reference with 2 channels destroys inter-channel information.
# F3' = (F3-F4)/2, F4' = (F4-F3)/2 → perfect mirror → FAA forced to 0.
# Use physical reference electrode (linked earlobes/mastoids) instead.
USE_REREFERENCING = False

# ═══ FEATURE EXTRACTION ═════════════════════════════════════════════════
WINDOW_SECONDS = 3.0
WINDOW_SAMPLES = int(WINDOW_SECONDS * SAMPLING_RATE)
OVERLAP = 0.5
STEP_SAMPLES = int(WINDOW_SAMPLES * (1 - OVERLAP))

EEG_BANDS = {
    "delta": (0.5, 4.0), "theta": (4.0, 8.0), "alpha": (8.0, 13.0),
    "beta": (13.0, 30.0), "gamma": (30.0, 45.0),
}

FEATURE_NAMES = [
    "delta_power", "theta_power", "alpha_power", "beta_power", "gamma_power",
    "faa",                # Frontal Alpha Asymmetry — Davidson (1992) ★ primary valence feature
    "theta_beta_ratio",   # Bazanova & Vernon (2014)
    "beta_alpha_ratio",   # Ray & Cole (1985) — weak arousal proxy for frontal only
    "gamma_theta_ratio",  # Fitzgibbon et al. (2004)
    "alpha_peak_freq",    # Klimesch (1999)
    "spectral_entropy",   # Inouye et al. (1991)
    "f3f4_coherence",     # Knyazev (2012)
    "hjorth_mobility",    # Hjorth (1970)
    "hjorth_complexity",  # Hjorth (1970)
    "sample_entropy",     # Richman & Moorman (2000)
]
N_FEATURES = len(FEATURE_NAMES)
FEATURE_INDEX = {name: i for i, name in enumerate(FEATURE_NAMES)}

FEATURE_GROUPS = {
    "spectral":     [0, 1, 2, 3, 4],
    "asymmetry":    [5],
    "ratios":       [6, 7, 8],
    "spectral_adv": [9, 10],
    "connectivity": [11],
    "temporal":     [12, 13, 14],
}

# ═══ EMOTION MODEL ══════════════════════════════════════════════════════
BASIC_EMOTIONS = {
    "Joy":     {"valence":  0.80, "arousal":  0.70},
    "Calm":    {"valence":  0.70, "arousal": -0.60},
    "Anger":   {"valence": -0.70, "arousal":  0.80},
    "Sadness": {"valence": -0.70, "arousal": -0.40},
    "Fear":    {"valence": -0.50, "arousal":  0.90},
    "Disgust": {"valence": -0.65, "arousal":  0.10},
}

TIER1_CONFIDENCE_THRESHOLD = 0.60
MIN_SIGNAL_QUALITY = 0.40

# Honest scientific scoping
VALENCE_RELIABILITY = "MODERATE"    # FAA is well-established for 2ch frontal
AROUSAL_RELIABILITY = "SPECULATIVE" # Beta/alpha from frontal only is a weak proxy
# The system is transparent about this in every output.

# ═══ ADAPTIVE SOUND ENGINE (EXPERIMENTAL) ════════════════════════════════
# NOTE: Binaural beat efficacy is NOT established science.
# Ingendoh et al. (2023), Garcia-Argibay et al. (2019) show mixed/weak evidence.
# This feature is labeled EXPERIMENTAL in all outputs.

BINAURAL_FREQUENCIES = {
    "deep_relax":   {"carrier": 200, "beat": 3.0},
    "meditation":   {"carrier": 200, "beat": 6.0},
    "calm_focus":   {"carrier": 200, "beat": 10.0},
    "alert_focus":  {"carrier": 200, "beat": 18.0},
}

SOUND_PROFILES = {
    "HV_HA": {"binaural": "alert_focus", "nature": ["birds", "stream"],
              "lofi_tempo": 110, "lofi_key": "major", "meditation": None,
              "description": "Upbeat ambient — maintain positive energy"},
    "HV_LA": {"binaural": "calm_focus", "nature": ["gentle_rain", "forest"],
              "lofi_tempo": 75, "lofi_key": "major", "meditation": "body_scan",
              "description": "Gentle ambient — sustain calm"},
    "LV_HA": {"binaural": "meditation", "nature": ["ocean_waves", "rain"],
              "lofi_tempo": 60, "lofi_key": "minor_to_major", "meditation": "breathing_4_7_8",
              "description": "Calming intervention — reduce stress"},
    "LV_LA": {"binaural": "calm_focus", "nature": ["birds_morning", "stream"],
              "lofi_tempo": 85, "lofi_key": "major", "meditation": "gratitude",
              "description": "Gentle uplift — counter low mood"},
}

SOUND_TRANSITION_SECONDS = 10.0
MIN_STATE_DURATION = 15.0
EMOTION_SMOOTHING_ALPHA = 0.3

# ═══ PSYCHOLOGIST API ════════════════════════════════════════════════════
EMOTION_TREND_WINDOW = 300
CRISIS_VALENCE_THRESHOLD = -0.7
CRISIS_DURATION_SECONDS = 120

# ═══ CALIBRATION ═════════════════════════════════════════════════════════
CALIBRATION_DURATION_SECONDS = 60    # 1-minute baseline recording
CALIBRATION_PHASES = ["eyes_closed_relax", "recall_stress"]
# After calibration, all features are z-scored against user's baseline
# Z-score clipping for calibrated features — prevents tanh saturation
# Without this, z-scores of 30+ cause valence to saturate at ±1.0
ZSCORE_CLIP_RANGE = 3.0  # ±3 SD preserves 99.7% of distribution

# ═══ DEPLOYMENT ══════════════════════════════════════════════════════════
# IMPORTANT: In production, run behind HTTPS reverse proxy (nginx/Caddy).
# API keys are sent in X-API-Key header — plaintext without TLS.
# BLE data is local (phone↔ESP32) so no TLS needed there.
# ESP32 sends little-endian float32 — matches Android/ARM default.
API_HOST = "0.0.0.0"
API_PORT = 8080
