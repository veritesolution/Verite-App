"""
Trainer v13.3 — Readable, with ablation study, saves via joblib.
"""
import numpy as np, os, time, logging
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.svm import SVC
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.calibration import CalibratedClassifierCV
from sklearn.metrics import accuracy_score
from sklearn.base import clone
from .signal_processor import EEGPreprocessor
from .feature_extractor import FeatureExtractor, N_FEATURES
from .config import FEATURE_NAMES, FEATURE_GROUPS, SAMPLING_RATE

logger = logging.getLogger("trainer")
DEAP_F3, DEAP_F4, DEAP_FS = 2, 3, 128
DEAP_BASELINE = 3 * DEAP_FS

def load_deap_subject(data_dir, sid):
    import pickle
    fname = os.path.join(data_dir, f"s{sid:02d}.dat")
    if not os.path.exists(fname): return None
    with open(fname, "rb") as f:
        s = pickle.load(f, encoding="latin1")
    data, labels = s["data"], s["labels"]
    if data.ndim != 3 or data.shape[1] < 4: return None
    return [{"eeg": np.array([data[t, DEAP_F3], data[t, DEAP_F4]]),
             "valence": float(labels[t, 0]), "arousal": float(labels[t, 1]),
             "subject": sid} for t in range(data.shape[0])]

def subject_permutation_test(accs_dict, n_perms=1000, chance=0.5, seed=42):
    rng = np.random.default_rng(seed)
    accs = np.array(list(accs_dict.values()))
    obs = np.mean(accs)
    dev = accs - chance
    count = sum(1 for _ in range(n_perms)
                if chance + np.mean(rng.choice([-1, 1], len(accs)) * dev) >= obs)
    return float(obs), float((count + 1) / (n_perms + 1))

def train_and_validate(data_dir, output_path="model_v13.pkl"):
    print("╔══════════════════════════════════════════════════════════╗")
    print("║   Brain-Emotion v13.3 — Training (F3/F4 only)          ║")
    print("╚══════════════════════════════════════════════════════════╝\n")

    # Phase 1: Extract features
    print("━━━ Phase 1: Feature Extraction ━━━")
    preproc = EEGPreprocessor(fs=DEAP_FS)
    ext = FeatureExtractor(fs=DEAP_FS)
    all_f, all_v, all_a, all_g = [], [], [], []
    sids = sorted([int(f[1:3]) for f in os.listdir(data_dir) if f.startswith("s") and f.endswith(".dat")])
    if not sids: raise FileNotFoundError(f"No .dat in {data_dir}")
    t0 = time.time()
    for sid in sids:
        trials = load_deap_subject(data_dir, sid)
        if not trials: continue
        for tr in trials:
            eeg = tr["eeg"]
            start = DEAP_BASELINE
            ws = int(3 * DEAP_FS)
            trial_feats = []
            while start + ws <= eeg.shape[1]:
                c, q, _ = preproc.preprocess(eeg[:, start:start + ws])
                if q >= 0.40:
                    trial_feats.append(ext.extract(c))
                start += ws
            if len(trial_feats) >= 2:
                all_f.append(np.mean(trial_feats, axis=0))
                all_v.append(tr["valence"])
                all_a.append(tr["arousal"])
                all_g.append(sid)
    X = np.array(all_f)
    y_val = (np.array(all_v) >= 5).astype(int)
    y_aro = (np.array(all_a) >= 5).astype(int)
    groups = np.array(all_g)
    usubs = np.unique(groups)
    print(f"  Trials: {len(X)}, Features: {N_FEATURES}, Subjects: {len(usubs)}, Time: {time.time()-t0:.1f}s")

    # Phase 2: LOSO
    print("\n━━━ Phase 2: LOSO Cross-Validation ━━━")
    clfs = {
        "SVM": make_pipeline(StandardScaler(), SVC(kernel="rbf", C=1, class_weight="balanced", probability=True, random_state=42)),
        "RF":  make_pipeline(StandardScaler(), RandomForestClassifier(n_estimators=100, max_depth=10, class_weight="balanced", n_jobs=-1, random_state=42)),
        "LR":  make_pipeline(StandardScaler(), LogisticRegression(class_weight="balanced", max_iter=1000, random_state=42)),
    }
    results = {}
    for cn, ct in clfs.items():
        vt, vp, at, ap = [], [], [], []
        for ts in usubs:
            tm, trm = groups == ts, groups != ts
            if len(np.unique(y_val[trm])) >= 2 and tm.sum() > 0:
                c = clone(ct); c.fit(X[trm], y_val[trm])
                vp.extend(c.predict(X[tm]).tolist()); vt.extend(y_val[tm].tolist())
            if len(np.unique(y_aro[trm])) >= 2 and tm.sum() > 0:
                c = clone(ct); c.fit(X[trm], y_aro[trm])
                ap.extend(c.predict(X[tm]).tolist()); at.extend(y_aro[tm].tolist())
        va = accuracy_score(vt, vp) if vt else 0
        aa = accuracy_score(at, ap) if at else 0
        results[cn] = {"val": va, "aro": aa}
        print(f"  {cn:<4}: Val={va:.1%}  Aro={aa:.1%}")
    best = max(results, key=lambda k: results[k]["val"])
    bv, ba = results[best]["val"], results[best]["aro"]
    print(f"\n  ★ Best: {best} — Val={bv:.1%}, Aro={ba:.1%}")

    # Phase 3: Per-subject + permutation
    print("\n━━━ Phase 3: Per-Subject + Permutation Test ━━━")
    psv = {}
    for ts in usubs:
        tm, trm = groups == ts, groups != ts
        if tm.sum() < 2: continue
        try:
            c = clone(clfs[best]); c.fit(X[trm], y_val[trm])
            psv[int(ts)] = accuracy_score(y_val[tm], c.predict(X[tm]))
        except Exception as e:
            logger.warning(f"S{ts}: {e}")
    if psv:
        accs = np.array(list(psv.values()))
        mean_a, pval = subject_permutation_test(psv)
        status = "ABOVE_CHANCE" if pval < 0.05 else "AT_CHANCE"
        print(f"  Mean: {mean_a:.1%}, p={pval:.4f} → {status}")
        rng = np.random.default_rng(42)
        boot = [np.mean(rng.choice(accs, len(accs), replace=True)) for _ in range(2000)]
        print(f"  95% CI: [{np.percentile(boot, 2.5):.1%}, {np.percentile(boot, 97.5):.1%}]")
    else:
        status, pval = "PENDING", 1.0

    # Phase 4: Ablation
    print("\n━━━ Phase 4: Ablation Study ━━━")
    for gn, gi in FEATURE_GROUPS.items():
        keep = np.ones(N_FEATURES, dtype=bool); keep[gi] = False
        Xa = X[:, keep]; vt, vp = [], []
        for ts in usubs:
            tm, trm = groups == ts, groups != ts
            if tm.sum() < 2 or len(np.unique(y_val[trm])) < 2: continue
            c = clone(clfs[best]); c.fit(Xa[trm], y_val[trm])
            vp.extend(c.predict(Xa[tm]).tolist()); vt.extend(y_val[tm].tolist())
        aa = accuracy_score(vt, vp) if vt else 0.5
        drop = bv - aa; d = "↓" if drop > 0 else "↑" if drop < 0 else "="
        m = "✅" if drop > 0.005 else "⚠️" if drop > 0 else "❌"
        print(f"  {m} Without {gn:14s}: {aa:.1%} ({d}{abs(drop):.1%})")

    # Phase 5: Train final
    print("\n━━━ Phase 5: Train Final Classifiers ━━━")
    n = len(X); nc = max(int(0.2 * n), 5)
    rng = np.random.default_rng(42)
    ci = rng.choice(n, nc, replace=False)
    ti = np.array([i for i in range(n) if i not in ci])
    bv_c = clone(clfs[best]); bv_c.fit(X[ti], y_val[ti])
    val_clf = CalibratedClassifierCV(bv_c, method="isotonic", cv="prefit"); val_clf.fit(X[ci], y_val[ci])
    ba_c = clone(clfs[best]); ba_c.fit(X[ti], y_aro[ti])
    aro_clf = CalibratedClassifierCV(ba_c, method="isotonic", cv="prefit"); aro_clf.fit(X[ci], y_aro[ci])
    print(f"  ✅ Valence + Arousal classifiers trained (calibrated)")

    # Save
    model = {"version": "13.3", "val_clf": val_clf, "aro_clf": aro_clf,
             "best": best, "val_accuracy": bv, "aro_accuracy": ba,
             "status": status, "p_value": pval,
             "n_subjects": len(usubs), "n_trials": len(X),
             "n_features": N_FEATURES, "feature_names": FEATURE_NAMES}
    try:
        import joblib; joblib.dump(model, output_path)
        print(f"\n  ✅ Saved (joblib): {output_path}")
    except ImportError:
        import pickle
        with open(output_path, "wb") as f: pickle.dump(model, f)
        print(f"\n  ⚠️ Saved (pickle — install joblib for safety): {output_path}")
    return model
