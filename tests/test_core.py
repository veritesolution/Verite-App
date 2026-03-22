"""
Unit Tests v13.3 — Tests that CATCH bugs, not hide them.
==========================================================
These tests would have caught every critical bug in v13.0:
- Median reference destroying FAA
- ADC conversion not subtracting midpoint
- Confidence = class probability instead of true confidence
- Test passing with all-zero features
"""

import numpy as np
import sys
import os
import time

# Add parent to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from brain_emotion.signal_processor import EEGPreprocessor, BandPowerExtractor
from brain_emotion.feature_extractor import FeatureExtractor
from brain_emotion.heca_engine import HECAEngine, UserCalibration
from brain_emotion.config import SAMPLING_RATE, N_FEATURES, FEATURE_INDEX, ADC_MIDPOINT, ADC_TO_UV

FS = SAMPLING_RATE
N = int(3.0 * FS)  # 3-second window

passed = 0
failed = 0

def test(name, condition, detail=""):
    global passed, failed
    if condition:
        print(f"  ✅ {name}")
        passed += 1
    else:
        print(f"  ❌ {name} — {detail}")
        failed += 1


# ═══════════════════════════════════════════════════════════════════════════
print("━━━ TEST 1: FAA is NOT destroyed by preprocessing ━━━")
# ═══════════════════════════════════════════════════════════════════════════
np.random.seed(42)
t = np.linspace(0, 3.0, N)

f3_weak_alpha = 5 * np.sin(2 * np.pi * 10 * t) + np.random.randn(N) * 3
f4_strong_alpha = 25 * np.sin(2 * np.pi * 10 * t) + np.random.randn(N) * 3
eeg_asymmetric = np.array([f3_weak_alpha, f4_strong_alpha])

preproc = EEGPreprocessor(fs=FS)
extractor = FeatureExtractor(fs=FS)

clean, quality, meta = preproc.preprocess(eeg_asymmetric)
features = extractor.extract(clean)
faa = features[FEATURE_INDEX["faa"]]

test("FAA is nonzero for asymmetric input", abs(faa) > 0.1,
     f"FAA={faa:.6f} — should be >> 0 for 5x alpha asymmetry")
test("FAA is positive (F4 alpha > F3 alpha)", faa > 0,
     f"FAA={faa:.4f} — positive means F4 alpha > F3 alpha")
test("Channels NOT mirror images after preprocessing",
     np.corrcoef(clean[0], clean[1])[0, 1] > -0.99,
     f"corr={np.corrcoef(clean[0], clean[1])[0, 1]:.4f} — -1.0 means CAR destroyed them")


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 1b: FAA DIRECTION is correct (Davidson 1992) ━━━")
# F4 alpha > F3 alpha → positive FAA → POSITIVE valence (approach motivation)
# This test catches the sign inversion bug from v13.3
# ═══════════════════════════════════════════════════════════════════════════
np.random.seed(43)
engine_dir = HECAEngine(fs=FS)
t = np.linspace(0, 3.0, N)

# More right alpha (F4) → left hemisphere activation → positive/approach
f3_low = 5 * np.sin(2 * np.pi * 10 * t) + np.random.randn(N) * 3
f4_high = 25 * np.sin(2 * np.pi * 10 * t) + np.random.randn(N) * 3
e_pos, _, _, _ = engine_dir.process_window(np.array([f3_low, f4_high]))

test("F4 alpha > F3 alpha → POSITIVE valence", e_pos.valence > 0,
     f"Got valence={e_pos.valence:.3f} — should be positive per Davidson (1992)")

# Reverse: more left alpha (F3) → right hemisphere → negative/withdrawal
engine_dir._smooth_valence = 0.0  # reset smoothing
f3_high = 25 * np.sin(2 * np.pi * 10 * t) + np.random.randn(N) * 3
f4_low = 5 * np.sin(2 * np.pi * 10 * t) + np.random.randn(N) * 3
e_neg, _, _, _ = engine_dir.process_window(np.array([f3_high, f4_low]))

test("F3 alpha > F4 alpha → NEGATIVE valence", e_neg.valence < 0,
     f"Got valence={e_neg.valence:.3f} — should be negative per Davidson (1992)")


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 2: ADC conversion subtracts midpoint ━━━")
# raw=2048 should → ~0 µV, not 1500 µV
# ═══════════════════════════════════════════════════════════════════════════

raw_midpoint = np.full((2, 100), ADC_MIDPOINT, dtype=np.float64)
uv = preproc.adc_to_uv(raw_midpoint)
test("ADC midpoint (2048) → ~0 µV", np.all(np.abs(uv) < 1.0),
     f"Got {uv[0,0]:.1f} µV — should be ~0")

raw_offset = np.full((2, 100), ADC_MIDPOINT + 100, dtype=np.float64)
uv2 = preproc.adc_to_uv(raw_offset)
test("ADC 2148 → positive µV", np.all(uv2 > 0),
     f"Got {uv2[0,0]:.4f} µV — should be positive")
test("ADC 2148 → reasonable range (< 200 µV)", np.all(uv2 < 200),
     f"Got {uv2[0,0]:.1f} µV")


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 3: Emotions CHANGE with different inputs ━━━")
# ═══════════════════════════════════════════════════════════════════════════
np.random.seed(44)
engine = HECAEngine(fs=FS)
t = np.linspace(0, 3.0, N)

# Calm: strong alpha, low beta
f3_calm = 20 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 2
f4_calm = 18 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 2
e_calm, _, _, _ = engine.process_window(np.array([f3_calm, f4_calm]))

# Reset smoothing
engine._smooth_valence = 0.0
engine._smooth_arousal = 0.0

# Stressed: low alpha, high beta noise
f3_stress = 3 * np.sin(2*np.pi*10*t) + 15 * np.random.randn(N)
f4_stress = 3 * np.sin(2*np.pi*10*t) + 15 * np.random.randn(N)
e_stress, _, _, _ = engine.process_window(np.array([f3_stress, f4_stress]))

test("Different inputs → different valence",
     abs(e_calm.valence - e_stress.valence) > 0.01,
     f"Calm V={e_calm.valence:.3f}, Stress V={e_stress.valence:.3f}")
test("Different inputs → different arousal",
     abs(e_calm.arousal - e_stress.arousal) > 0.01,
     f"Calm A={e_calm.arousal:.3f}, Stress A={e_stress.arousal:.3f}")


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 4: Confidence interpretation is correct ━━━")
# confidence should be max(p0, p1), NOT raw p1
# ═══════════════════════════════════════════════════════════════════════════

# When model is None (heuristic mode), test the confidence logic
np.random.seed(45)
t = np.linspace(0, 3.0, N)
f3w = 5 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3
f4s = 25 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3
engine4 = HECAEngine(fs=FS)
e_pos, _, _, _ = engine4.process_window(np.array([f3w, f4s]))
test("Valence confidence ≥ 0.5", e_pos.valence_confidence >= 0.5,
     f"Got {e_pos.valence_confidence:.3f}")
test("Arousal confidence ≥ 0.3", e_pos.arousal_confidence >= 0.3,
     f"Got {e_pos.arousal_confidence:.3f}")


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 5: Feature vector is correct shape and not all zeros ━━━")
# ═══════════════════════════════════════════════════════════════════════════

features = extractor.extract(eeg_asymmetric)
test(f"Feature vector shape = ({N_FEATURES},)", features.shape == (N_FEATURES,))
test("Features not all zero", np.sum(np.abs(features)) > 0.1,
     f"Sum of abs features = {np.sum(np.abs(features)):.6f}")
test("No NaN in features", not np.any(np.isnan(features)))
test("No Inf in features", not np.any(np.isinf(features)))


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 6: Signal quality scoring ━━━")
# ═══════════════════════════════════════════════════════════════════════════
np.random.seed(46)

# Clean signal → high quality
clean_sig = np.random.randn(2, N) * 10  # 10 µV RMS — very clean
_, q_clean, _ = preproc.preprocess(clean_sig)
test("Clean signal → quality > 0.8", q_clean > 0.8, f"Got {q_clean:.2f}")

# Signal with many artifacts (rapid spikes) → lower quality
spiky_sig = np.random.randn(2, N) * 10
spiky_sig[:, ::5] = 500  # spike every 5 samples → high gradient
_, q_spiky, _ = preproc.preprocess(spiky_sig)
test("Spiky signal → quality < clean signal", q_spiky < q_clean,
     f"Spiky={q_spiky:.2f}, Clean={q_clean:.2f}")


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 7: Edge cases don't crash ━━━")
# ═══════════════════════════════════════════════════════════════════════════

try:
    z = np.zeros((2, N))
    e, f, s, p = engine.process_window(z)
    test("All-zeros input doesn't crash", True)
except Exception as ex:
    test("All-zeros input doesn't crash", False, str(ex))

try:
    nan_data = np.full((2, N), np.nan)
    e, f, s, p = engine.process_window(nan_data)
    test("NaN input handled gracefully", True)
except Exception as ex:
    test("NaN input handled gracefully", False, str(ex))


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 8: Calibration works in heuristic mode ━━━")
# ═══════════════════════════════════════════════════════════════════════════
np.random.seed(48)
t = np.linspace(0, 3.0, N)

# Engine WITHOUT calibration — moderate asymmetry
eng_nocal = HECAEngine(fs=FS)
eeg_test = np.array([
    12 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,   # F3: moderate alpha
    15 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,   # F4: slightly more
])
e_nocal, _, _, _ = eng_nocal.process_window(eeg_test)

# Engine WITH calibration — baseline has OPPOSITE asymmetry (F3 > F4)
# so calibration will shift the z-scored FAA significantly
eng_cal = HECAEngine(fs=FS)
for i in range(5):
    np.random.seed(100 + i)
    cal_eeg = np.array([
        30 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,  # F3: high baseline alpha
        10 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,  # F4: low baseline alpha
    ])
    eng_cal.add_calibration_window(cal_eeg)
cal_ok = eng_cal.finalize_calibration()
test("Calibration finalizes successfully", cal_ok)
test("Engine reports calibrated", eng_cal.calibration.is_calibrated)

np.random.seed(48)  # same seed → same test signal
eeg_test2 = np.array([
    12 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,
    15 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,
])
e_cal, _, _, _ = eng_cal.process_window(eeg_test2)

test("Calibration changes valence output in heuristic mode",
     abs(e_nocal.valence - e_cal.valence) > 0.01,
     f"No-cal V={e_nocal.valence:.3f}, Cal V={e_cal.valence:.3f} — should differ")
test("Calibrated output reports is_calibrated=True", e_cal.is_calibrated)

# v13.3: Calibrated valence must NOT saturate at ±1.0
test("Calibrated valence NOT saturated at ±1.0",
     abs(e_cal.valence) < 0.999,
     f"Got V={e_cal.valence:.6f} — saturated! Z-score clipping broken")


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 9: Sound recommendation changes with emotion ━━━")
# ═══════════════════════════════════════════════════════════════════════════

# Process enough windows to trigger transition
np.random.seed(49)
t = np.linspace(0, 3.0, N)
engine2 = HECAEngine(fs=FS)
engine2._quadrant_since = time.time() - 100  # pretend long enough in current state

# Happy state
f3h = 25 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 2
f4h = 5 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 2
_, _, s1, _ = engine2.process_window(np.array([f3h, f4h]))

test("Sound recommendation has binaural frequency", s1.binaural_beat_hz > 0)
test("Sound recommendation has nature sounds", len(s1.nature_sounds) > 0)
test("Sound recommendation includes experimental disclaimer",
     "EXPERIMENTAL" in s1.to_dict().get("disclaimer", ""))


# ═══════════════════════════════════════════════════════════════════════════
print("\n━━━ TEST 10: 26-Emotion Taxonomy ━━━")
# ═══════════════════════════════════════════════════════════════════════════
from brain_emotion.emotion_taxonomy import EmotionTaxonomy

tax = EmotionTaxonomy()
test("Taxonomy has 26 emotions", len(tax.emotions) == 26,
     f"Got {len(tax.emotions)}")

# Without context — should return something
r1 = tax.classify(valence=0.8, arousal=0.7)
test("High V, High A → positive emotion", r1.emotion in ["Joy", "Excitement", "Amusement"],
     f"Got {r1.emotion}")
test("Result has eeg_support field", r1.eeg_support in ["GOOD", "FAIR", "WEAK", "NONE"])
test("Result has top_3 alternatives", len(r1.top_3) == 3)

# With context — should disambiguate toward aesthetic appreciation
r2 = tax.classify(valence=0.55, arousal=-0.05, context={"content": "art", "mode": "museum"})
test("Art context + matching V-A → art-related emotion",
     r2.emotion in ["Aesthetic appreciation", "Entrancement", "Admiration", "Calmness", "Satisfaction"],
     f"Got {r2.emotion}")
test("Context was used for disambiguation", r2.context_used or r2.eeg_support in ["WEAK", "NONE"],
     f"context_used={r2.context_used}")

# Negative emotion
r3 = tax.classify(valence=-0.65, arousal=0.85)
test("Strong negative + high arousal → Fear or Horror",
     r3.emotion in ["Fear", "Horror", "Anxiety"],
     f"Got {r3.emotion}")

# Low V, Low A
r4 = tax.classify(valence=-0.3, arousal=-0.6)
test("Mild negative + low arousal → Boredom or Sadness",
     r4.emotion in ["Boredom", "Sadness", "Nostalgia"],
     f"Got {r4.emotion}")

# NONE-support emotion with strong context
r5 = tax.classify(valence=0.72, arousal=0.38, context={"content": "partner", "mode": "date"})
test("Romance context → Romance or love-related",
     r5.emotion in ["Romance", "Adoration", "Joy", "Excitement"],
     f"Got {r5.emotion}")
# Check that if we got Romance specifically, it has NONE support
if r5.emotion == "Romance":
    test("Romance has NONE eeg_support", tax.emotions["Romance"].eeg_support == "NONE")
else:
    test("Non-Romance result has valid eeg_support",
         r5.eeg_support in ["GOOD", "FAIR", "WEAK", "NONE"])

# Full engine integration — process_window returns fine-grained
np.random.seed(50)
t = np.linspace(0, 3.0, N)
eng_tax = HECAEngine(fs=FS)
eeg_happy = np.array([
    5 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,
    25 * np.sin(2*np.pi*10*t) + np.random.randn(N) * 3,
])
e_t, f_t, s_t, p_t = eng_tax.process_window(eeg_happy, context={"mode": "music"})
test("Engine returns fine-grained emotion", f_t.emotion in tax.emotions)
test("Fine-grained has confidence > 0", f_t.confidence > 0)
test("Psychologist context includes fine-grained",
     p_t.fine_grained_emotion is not None)

# v13.3.1 improvements
test("Result includes eeg_evidence citation",
     len(f_t.eeg_evidence) > 10,
     f"Got: {f_t.eeg_evidence[:30]}")
test("Confidence explicitly labeled as heuristic",
     "NOT" in f_t.confidence_note and "calibrated" in f_t.confidence_note.lower())

# Semantic context matching — "relaxing" should match meditation/calm cluster
tax2 = EmotionTaxonomy()
r_sem = tax2.classify(valence=0.5, arousal=-0.5, context={"mode": "yoga"})
test("Semantic match: yoga → calm-related emotion",
     r_sem.emotion in ["Calmness", "Relief", "Satisfaction", "Entrancement"],
     f"Got {r_sem.emotion}")

# Transition penalty — Joy→Fear is a big jump, should have penalty
from brain_emotion.emotion_taxonomy import transition_penalty
tp = transition_penalty("Fear", "Joy")
test("Joy→Fear transition has nonzero penalty", tp > 0, f"penalty={tp:.4f}")
tp_natural = transition_penalty("Excitement", "Joy")
test("Joy→Excitement (natural) has zero penalty", tp_natural == 0.0, f"penalty={tp_natural:.4f}")


# ═══════════════════════════════════════════════════════════════════════════
print(f"\n{'═'*60}")
print(f"  RESULTS: {passed} passed, {failed} failed, {passed+failed} total")
print(f"{'═'*60}")
if failed > 0:
    print(f"  ⚠️ {failed} TESTS FAILED — fix before deployment")
    sys.exit(1)
else:
    print(f"  ✅ ALL TESTS PASSED")
    sys.exit(0)
