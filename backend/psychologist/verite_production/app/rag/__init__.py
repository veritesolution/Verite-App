"""
Verite Production API — RAG (Retrieval-Augmented Generation)
FAISS-indexed clinical knowledge + dataset retrieval with proper scoring.
"""
from __future__ import annotations

import json
import logging
import os
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

logger = logging.getLogger("verite.rag")


# ═══════════════════════════════════════════════════════════════
# Clinical Knowledge Base
# ═══════════════════════════════════════════════════════════════

CLINICAL_KNOWLEDGE: Dict[str, Dict] = {
    "grounding_5_4_3_2_1": {
        "name": "5-4-3-2-1 Grounding",
        "domains": ["health", "personal"],
        "phases": ["support"],
        "content": (
            "Sensory grounding technique: Name 5 things you SEE, 4 you can TOUCH, "
            "3 you HEAR, 2 you SMELL, 1 you TASTE. Interrupts the fight-or-flight response "
            "by redirecting attention to the present moment."
        ),
        "source": "Linehan, M.M. (1993). Cognitive-Behavioral Treatment of BPD.",
    },
    "box_breathing": {
        "name": "Box Breathing",
        "domains": ["health", "personal", "academic", "work_career"],
        "phases": ["support"],
        "content": (
            "Breathe IN for 4 seconds, HOLD for 4, OUT for 4, HOLD for 4. Repeat 4 cycles. "
            "Activates the parasympathetic nervous system via vagus nerve stimulation."
        ),
        "source": "Ma, X. et al. (2017). The Effect of Diaphragmatic Breathing on Attention.",
    },
    "thought_record": {
        "name": "CBT Thought Record",
        "domains": ["personal", "relationship", "academic", "work_career"],
        "phases": ["support", "action_plan"],
        "content": (
            "Structured form: (1) Situation, (2) Automatic Thought, (3) Emotion & intensity 0-100, "
            "(4) Evidence FOR the thought, (5) Evidence AGAINST, (6) Balanced alternative thought, "
            "(7) Re-rate emotion."
        ),
        "source": "Beck, J.S. (1995). Cognitive Behavior Therapy: Basics and Beyond.",
    },
    "behavioral_activation": {
        "name": "Behavioral Activation",
        "domains": ["health", "personal"],
        "phases": ["support", "action_plan"],
        "content": (
            "Break the depression-inactivity cycle: schedule small activities rated for "
            "pleasure (P) and mastery (M). Action often precedes motivation, not the reverse. "
            "Start with low-effort, high-reward activities."
        ),
        "source": "Martell, C.R. et al. (2010). Behavioral Activation for Depression.",
    },
    "dear_man": {
        "name": "DBT DEAR MAN",
        "domains": ["relationship", "family", "work_career"],
        "phases": ["support", "action_plan"],
        "content": (
            "Interpersonal effectiveness skill: Describe the situation factually, "
            "Express your feelings with I-statements, Assert your needs clearly, "
            "Reinforce by explaining mutual benefits. Stay Mindful of your goal, "
            "Appear confident, be willing to Negotiate."
        ),
        "source": "Linehan, M.M. (2015). DBT Skills Training Manual, 2nd Ed.",
    },
    "cognitive_restructuring": {
        "name": "Cognitive Restructuring",
        "domains": ["personal", "health", "academic", "work_career", "relationship", "family"],
        "phases": ["analysis", "support"],
        "content": (
            "Core CBT technique: (1) Identify the automatic thought, (2) Name the cognitive "
            "distortion, (3) Examine evidence for and against, (4) Generate alternative "
            "interpretations, (5) Rate belief change. Use Socratic questioning throughout."
        ),
        "source": "Beck, A.T. et al. (1979). Cognitive Therapy of Depression.",
    },
    "pmr": {
        "name": "Progressive Muscle Relaxation",
        "domains": ["health", "academic"],
        "phases": ["support", "action_plan"],
        "content": (
            "Systematically tense each muscle group for 5 seconds, then release for 10 seconds. "
            "Work from feet to face. Takes 15-20 minutes. Particularly effective before sleep."
        ),
        "source": "Jacobson, E. (1938). Progressive Relaxation.",
    },
    "worry_time": {
        "name": "Scheduled Worry Time",
        "domains": ["health"],
        "phases": ["support", "action_plan"],
        "content": (
            "Designate a 15-minute daily window (not before bed) for worrying. "
            "During the day, note worries and postpone them to the scheduled time. "
            "At the scheduled time, review notes and problem-solve what you can."
        ),
        "source": "Borkovec, T.D. et al. (1983). Stimulus control applications.",
    },
    "exposure_hierarchy": {
        "name": "Graduated Exposure",
        "domains": ["health"],
        "phases": ["analysis", "support", "action_plan"],
        "content": (
            "Create a fear hierarchy from 2/10 to 10/10. Start with the lowest-rated item. "
            "Stay in the situation until anxiety naturally decreases (habituation). "
            "The person always controls the pace. Never force or rush."
        ),
        "source": "Foa, E.B. & Kozak, M.J. (1986). Emotional Processing of Fear.",
    },
    "values_clarification": {
        "name": "Values Clarification (ACT)",
        "domains": ["personal", "work_career", "academic"],
        "phases": ["analysis", "action_plan"],
        "content": (
            "Explore core values across domains: relationships, work/education, personal growth, "
            "health, community. Key question: 'If nobody was watching and there were no "
            "consequences, what kind of life would you want to live?' Align daily actions with values."
        ),
        "source": "Hayes, S.C. et al. (1999). Acceptance and Commitment Therapy.",
    },
    "self_compassion": {
        "name": "Self-Compassion",
        "domains": ["personal", "academic", "work_career"],
        "phases": ["support"],
        "content": (
            "Three components: (1) Self-kindness: 'What would I say to a friend in this situation?' "
            "(2) Common humanity: 'Everyone struggles, this is part of being human.' "
            "(3) Mindfulness: 'This is a moment of suffering' — acknowledge without over-identifying."
        ),
        "source": "Neff, K.D. (2003). Self-Compassion: An Alternative Conceptualization.",
    },
    "sleep_hygiene": {
        "name": "Sleep Hygiene",
        "domains": ["health"],
        "phases": ["support", "action_plan"],
        "content": (
            "Key principles: consistent wake time (even weekends), bed only for sleep, "
            "no caffeine after 2pm, cool dark room (18-20°C), 30-minute wind-down routine "
            "without screens, avoid clock-watching."
        ),
        "source": "Morin, C.M. et al. (2006). Psychological and behavioral treatment of insomnia.",
    },
    "boundary_setting": {
        "name": "Boundary Setting",
        "domains": ["family", "relationship", "work_career"],
        "phases": ["support", "action_plan"],
        "content": (
            "Steps: (1) Identify your limit clearly, (2) Communicate using I-statements, "
            "(3) Be specific about the behavior and its impact, (4) State the consequence, "
            "(5) Follow through consistently. Guilt is normal and does not mean the boundary is wrong."
        ),
        "source": "Linehan, M.M. (2015). DBT Skills Training Manual.",
    },
    "mindfulness_basics": {
        "name": "Mindfulness Basics",
        "domains": ["health", "personal", "academic", "work_career"],
        "phases": ["support"],
        "content": (
            "Observe thoughts without judgment: 'I notice I'm having the thought that...' "
            "Focus on breath as anchor. When mind wanders, gently redirect without criticism. "
            "Even 5 minutes daily shows measurable benefits for anxiety and rumination."
        ),
        "source": "Kabat-Zinn, J. (1994). Wherever You Go, There You Are.",
    },
    "opposite_action": {
        "name": "DBT Opposite Action",
        "domains": ["health", "personal", "relationship"],
        "phases": ["support"],
        "content": (
            "When an emotion's urge is unjustified or unhelpful, act opposite: "
            "Fear urges avoidance → approach. Sadness urges withdrawal → engage. "
            "Anger urges attack → gently withdraw or be kind. Acts fully opposite, "
            "including posture, tone, and behavior."
        ),
        "source": "Linehan, M.M. (2015). DBT Skills Training Manual.",
    },
    "distress_tolerance_tipp": {
        "name": "DBT TIPP Skills",
        "domains": ["health", "personal"],
        "phases": ["support"],
        "content": (
            "For acute emotional crises: Temperature (cold water on face), "
            "Intense exercise (brief, vigorous), Paced breathing (exhale longer than inhale), "
            "Progressive muscle relaxation. Reduces physiological arousal within minutes."
        ),
        "source": "Linehan, M.M. (2015). DBT Skills Training Manual.",
    },
}


# ═══════════════════════════════════════════════════════════════
# RAG Retriever
# ═══════════════════════════════════════════════════════════════

class RAGRetriever:
    """FAISS-based retrieval for clinical techniques and dataset examples."""

    def __init__(self):
        self._sem_model = None
        self._ck_faiss = None  # Clinical knowledge index
        self._ck_texts: List[str] = []
        self._ck_keys: List[str] = []
        self._ds_faiss = None  # Dataset index
        self._ds_texts: List[str] = []
        self._ds_responses: List[str] = []
        self._initialized = False

    def init(self, sem_model: Any, cache_dir: str) -> None:
        """Initialize FAISS indexes. Called once at startup."""
        import faiss

        self._sem_model = sem_model
        os.makedirs(cache_dir, exist_ok=True)

        if sem_model is None:
            logger.warning("No sentence model — RAG disabled")
            return

        # Build clinical knowledge index
        self._ck_texts = [
            f"{v['name']}: {v['content']}" for v in CLINICAL_KNOWLEDGE.values()
        ]
        self._ck_keys = list(CLINICAL_KNOWLEDGE.keys())

        ck_cache = os.path.join(cache_dir, "clinical_knowledge_v2.index")
        if os.path.exists(ck_cache):
            try:
                self._ck_faiss = faiss.read_index(ck_cache)
                logger.info(f"Clinical knowledge FAISS loaded: {self._ck_faiss.ntotal}")
            except Exception:
                self._ck_faiss = None

        if self._ck_faiss is None:
            embs = sem_model.encode(
                self._ck_texts, convert_to_numpy=True, normalize_embeddings=True
            ).astype("float32")
            self._ck_faiss = faiss.IndexFlatIP(embs.shape[1])
            self._ck_faiss.add(embs)
            try:
                faiss.write_index(self._ck_faiss, ck_cache)
            except Exception as e:
                logger.warning(f"Failed to cache clinical FAISS: {e}")
            logger.info(f"Clinical knowledge FAISS built: {self._ck_faiss.ntotal}")

        self._initialized = True

    def load_datasets(self, cache_dir: str) -> None:
        """Load and index HuggingFace datasets. Called at startup (can be slow)."""
        if not self._sem_model:
            return

        import faiss

        try:
            from datasets import load_dataset

            queries, responses = [], []
            datasets_config = [
                ("ShenLab/MentalChat16K", "input", "output"),
                ("Amod/mental_health_counseling_conversations", "Context", "Response"),
            ]

            for ds_name, q_col, r_col in datasets_config:
                try:
                    logger.info(f"Loading dataset: {ds_name}")
                    ds = load_dataset(ds_name, split="train")
                    count = 0
                    for row in ds:
                        q = str(row.get(q_col, "")).strip()
                        r = str(row.get(r_col, "")).strip()
                        if not (q and r and len(q) > 20 and len(r) > 50):
                            continue

                        # FIX #2: Quality filtering — skip low-quality responses
                        r_lower = r.lower()

                        # Skip responses that are too short to be therapeutically useful
                        if len(r.split()) < 15:
                            continue

                        # Skip responses with harmful patterns
                        harmful_markers = [
                            "just get over", "snap out of", "stop being sad",
                            "others have it worse", "man up", "grow up",
                            "stop taking your med", "you don't need a therapist",
                        ]
                        if any(m in r_lower for m in harmful_markers):
                            continue

                        # Skip responses that look like Reddit comments, not counseling
                        low_quality_markers = [
                            "lol", "lmao", "tbh", "imo", "ngl", "bruh",
                            "upvote", "downvote", "this subreddit",
                            "i'm not a therapist but", "not a doctor but",
                        ]
                        if any(m in r_lower for m in low_quality_markers):
                            continue

                        # Skip responses that are just questions with no substance
                        if r.count("?") > 3 and len(r.split()) < 30:
                            continue

                        queries.append(q[:500])
                        responses.append(r[:800])
                        count += 1
                    logger.info(f"  {ds_name}: {count} examples after quality filter")
                except Exception as e:
                    logger.warning(f"  {ds_name} unavailable: {e}")

            if not queries:
                logger.warning("No dataset examples loaded")
                return

            self._ds_texts = queries
            self._ds_responses = responses

            # Try loading cached index
            ds_cache = os.path.join(cache_dir, "dataset_v2.index")
            meta_cache = os.path.join(cache_dir, "dataset_v2_meta.json")

            if os.path.exists(ds_cache) and os.path.exists(meta_cache):
                try:
                    with open(meta_cache) as f:
                        meta = json.load(f)
                    if meta.get("count") == len(queries):
                        self._ds_faiss = faiss.read_index(ds_cache)
                        logger.info(f"Dataset FAISS loaded from cache: {self._ds_faiss.ntotal}")
                        return
                except Exception:
                    pass

            # Build fresh
            logger.info(f"Embedding {len(queries)} dataset examples...")
            all_embs = []
            batch_size = 256
            for i in range(0, len(queries), batch_size):
                batch = queries[i:i + batch_size]
                embs = self._sem_model.encode(
                    batch, convert_to_numpy=True,
                    normalize_embeddings=True, show_progress_bar=False,
                )
                all_embs.append(embs)

            embs_np = np.vstack(all_embs).astype("float32")
            self._ds_faiss = faiss.IndexFlatIP(embs_np.shape[1])
            self._ds_faiss.add(embs_np)

            try:
                faiss.write_index(self._ds_faiss, ds_cache)
                with open(meta_cache, "w") as f:
                    json.dump({"count": len(queries)}, f)
                logger.info(f"Dataset FAISS built and cached: {self._ds_faiss.ntotal}")
            except Exception as e:
                logger.warning(f"Dataset cache write failed: {e}")

        except ImportError:
            logger.warning("datasets library not available — dataset RAG disabled")
        except Exception as e:
            logger.error(f"Dataset loading failed: {e}")

    def retrieve(
        self,
        domain: str,
        phase: str,
        user_text: str,
        history_texts: Optional[List[str]] = None,
        top_k_techniques: int = 3,
        top_k_examples: int = 3,
    ) -> Tuple[str, str]:
        """
        Retrieve relevant techniques and examples.
        Returns (technique_context, few_shot_examples).
        """
        tech_ctx = ""
        few_shot = ""

        if not self._sem_model:
            return tech_ctx, few_shot

        # Build query from current message + recent history
        query_parts = [user_text]
        if history_texts:
            query_parts.extend(history_texts[-3:])
        combined_query = " ".join(query_parts)[:800]

        # Retrieve techniques
        tech_ctx = self._retrieve_techniques(
            combined_query, domain, phase, top_k_techniques
        )

        # Retrieve dataset examples
        few_shot = self._retrieve_examples(user_text, top_k_examples)

        return tech_ctx, few_shot

    def _retrieve_techniques(
        self, query: str, domain: str, phase: str, top_k: int
    ) -> str:
        """Retrieve clinical techniques with domain/phase boosting."""
        if self._ck_faiss is None:
            return ""

        try:
            emb = self._sem_model.encode(
                [query[:500]], convert_to_numpy=True, normalize_embeddings=True
            ).astype("float32")

            k = min(top_k + 4, len(self._ck_texts))
            scores, indices = self._ck_faiss.search(emb, k)

            max_score = max(float(scores[0][0]), 0.01)
            ranked = []

            for score, idx in zip(scores[0], indices[0]):
                if idx < 0:
                    continue
                key = self._ck_keys[idx]
                item = CLINICAL_KNOWLEDGE[key]

                norm_sim = float(score) / max_score
                domain_boost = 0.2 if domain in item.get("domains", []) else 0.0
                phase_boost = 0.1 if phase in item.get("phases", []) else 0.0
                final = 0.7 * norm_sim + 0.2 * domain_boost + 0.1 * phase_boost
                ranked.append((final, key, item))

            ranked.sort(key=lambda x: -x[0])
            parts = [
                f"[{it['name']}] {it['content']} (Source: {it['source']})"
                for _, _, it in ranked[:top_k]
            ]
            return "\n\n".join(parts)

        except Exception as e:
            logger.error(f"Technique retrieval error: {e}")
            return ""

    def _retrieve_examples(self, query: str, top_k: int) -> str:
        """Retrieve similar conversation examples from dataset."""
        if self._ds_faiss is None or not self._ds_texts:
            return ""

        try:
            emb = self._sem_model.encode(
                [query[:500]], convert_to_numpy=True, normalize_embeddings=True
            ).astype("float32")

            scores, indices = self._ds_faiss.search(emb, top_k)
            examples = []
            for score, idx in zip(scores[0], indices[0]):
                if idx < 0 or float(score) < 0.3:
                    continue
                examples.append(
                    f"User: {self._ds_texts[idx][:300]}\n"
                    f"Counselor: {self._ds_responses[idx][:400]}"
                )

            return "\n---\n".join(examples)

        except Exception as e:
            logger.error(f"Dataset retrieval error: {e}")
            return ""

    def health_status(self) -> Dict[str, Any]:
        return {
            "initialized": self._initialized,
            "techniques": len(CLINICAL_KNOWLEDGE),
            "techniques_indexed": self._ck_faiss.ntotal if self._ck_faiss else 0,
            "dataset_examples": self._ds_faiss.ntotal if self._ds_faiss else 0,
            "semantic_model_loaded": self._sem_model is not None,
        }
