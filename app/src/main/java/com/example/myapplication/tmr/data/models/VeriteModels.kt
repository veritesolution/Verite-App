package com.example.myapplication.tmr.data.models

import com.google.gson.annotations.SerializedName

// ─────────────────────────────────────────────────────────────────────────────
// WebSocket message types
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cue fired during a tick — embedded as [TickEvent.cue], null when no cue delivered.
 * Field names match the server's cue_dict in main.py exactly.
 */
data class CueInfo(
    val concept: String,
    @SerializedName("cue_type")     val cueType: String,
    @SerializedName("phase_deg")    val phaseDeg: Double,
    @SerializedName("spindle_prob") val spindleProb: Double,
    @SerializedName("coupling_mi")  val couplingMi: Double,
)

/**
 * Brain-state snapshot pushed every ~50 ms.
 *
 * All field names match the server tick payload in main.py:
 *   type, ts, stage, phase_deg, spindle_prob, arousal_risk,
 *   coupling_mi, kcomplex, artefact, delivery_window, cue
 *
 * Plain-name fields (no @SerializedName needed — Gson matches by exact name):
 *   type, ts, stage, kcomplex, artefact, cue
 */
data class TickEvent(
    val type: String,
    val ts: Double,
    val stage: String,
    @SerializedName("phase_deg")       val phaseDeg: Double,
    @SerializedName("spindle_prob")    val spindleProb: Double,
    @SerializedName("arousal_risk")    val arousalRisk: Double,
    @SerializedName("coupling_mi")     val couplingMi: Double,
    val kcomplex: Boolean,
    val artefact: Boolean,
    @SerializedName("delivery_window") val deliveryWindow: Boolean,
    val cue: CueInfo? = null,
) {
    /** True when SO phase is within the Mölle 2002 up-state window [135°, 225°]. */
    val isUpState: Boolean get() = phaseDeg in 135.0..225.0

    /** Traffic-light colour for the phase indicator in the UI. */
    val phaseColor: PhaseColor
        get() = when {
            deliveryWindow -> PhaseColor.GREEN
            stage in listOf("N2", "N3") -> PhaseColor.AMBER
            else -> PhaseColor.RED
        }
}

enum class PhaseColor { GREEN, AMBER, RED }

// ─────────────────────────────────────────────────────────────────────────────
// REST — Session lifecycle
// ─────────────────────────────────────────────────────────────────────────────

/** POST /session/start  request body. */
data class StartSessionRequest(
    val mode: String = "simulation",
    @SerializedName("ws_uri") val wsUri: String = "",
    val hours: Double = 8.0,
    val config: Map<String, Any> = emptyMap(),
)

/** POST /session/start  response. */
data class StartSessionResponse(
    @SerializedName("session_id")    val sessionId: String,
    val mode: String,
    val safety: Map<String, String>,
    @SerializedName("spindle_mode")  val spindleMode: String,
    @SerializedName("arousal_mode")  val arousalMode: String,
    @SerializedName("audio_backend") val audioBackend: String,
)

/** GET /session/status  response. */
data class SessionStatusResponse(
    val active: Boolean,
    @SerializedName("session_id") val sessionId: String?,
    val mode: String?,
    @SerializedName("elapsed_s")  val elapsedS: Double?,
    @SerializedName("n_cues")     val nCues: Int?,
)

/** POST /session/stop  response — wraps the final SessionReport. */
data class StopSessionResponse(
    val status: String,
    @SerializedName("final_report") val finalReport: SessionReport,
)

// ─────────────────────────────────────────────────────────────────────────────
// REST — Report  (GET /report)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full session report returned by GET /report and embedded in StopSessionResponse.
 *
 * FIX: added [memoryProfile] — the server always returns "memory_profile" in
 * the report dict (from assessor.get_session_profile()) but it was previously
 * absent from this model, causing Gson to silently discard it.
 */
data class SessionReport(
    @SerializedName("session_id")     val sessionId: String = "",
    val mode: String = "",
    @SerializedName("started_at")     val startedAt: Double = 0.0,
    @SerializedName("elapsed_s")      val elapsedS: Double = 0.0,
    val running: Boolean = false,
    @SerializedName("n_concepts")     val nConcepts: Int = 0,
    val stats: OrchestratorStats = OrchestratorStats(),
    @SerializedName("gate_summary")   val gateSummary: GateSummary = GateSummary(),
    @SerializedName("memory_profile") val memoryProfile: MemoryProfile = MemoryProfile(),
    @SerializedName("cue_log")        val cueLog: List<CueLogEntry> = emptyList(),
    val hardware: HardwareLatency = HardwareLatency(),
    @SerializedName("spindle_mode")   val spindleMode: String = "",
    @SerializedName("arousal_mode")   val arousalMode: String = "",
)

/** Maps to orchestrator.session_stats() in main.py. */
data class OrchestratorStats(
    @SerializedName("total_cues_delivered")  val totalCuesDelivered: Int = 0,
    @SerializedName("spindle_coupled_pct")   val spindleCoupledPct: Double = 0.0,
    @SerializedName("so_upstate_pct")        val soUpstatePct: Double = 0.0,
    @SerializedName("unique_concepts_cued")  val uniqueConceptsCued: Int = 0,
    @SerializedName("policy_mode")           val policyMode: String = "",
)

/** Maps to orchestrator.gate_rejection_summary(). */
data class GateSummary(
    @SerializedName("total_gate_checks")  val totalGateChecks: Int = 0,
    val delivered: Int = 0,
    val blocked: Int = 0,
    @SerializedName("delivery_rate_pct") val deliveryRatePct: Double = 0.0,
    @SerializedName("block_reasons")     val blockReasons: Map<String, Int> = emptyMap(),
)

/**
 * Maps to assessor.get_session_profile() — returned as "memory_profile" in the report.
 * FIX: was missing entirely; server always emits this object.
 */
data class MemoryProfile(
    val timestamp: String = "",
    @SerializedName("n_assessed")        val nAssessed: Int = 0,
    @SerializedName("sweet_spot_count")  val sweetSpotCount: Int = 0,
    @SerializedName("avg_strength")      val avgStrength: Double = 0.0,
    val weights: List<Double> = emptyList(),
    @SerializedName("weights_source")    val weightsSource: String = "",
    @SerializedName("n_weight_updates")  val nWeightUpdates: Int = 0,
)

/** Maps to hardware.latency_report() in the report. */
data class HardwareLatency(
    val n: Int = 0,
    @SerializedName("mean_ms") val meanMs: Double? = null,
    @SerializedName("p95_ms")  val p95Ms: Double? = null,
    @SerializedName("hw_synced") val hwSynced: Boolean = false,
)

/** One entry in the cue_log list. */
data class CueLogEntry(
    val ts: Double = 0.0,
    val concept: String = "",
    @SerializedName("cue_type")     val cueType: String = "",
    @SerializedName("phase_deg")    val phaseDeg: Double = 0.0,
    @SerializedName("spindle_prob") val spindleProb: Double = 0.0,
    @SerializedName("coupling_mi")  val couplingMi: Double = 0.0,
)

// ─────────────────────────────────────────────────────────────────────────────
// REST — Document upload  (POST /document)
// ─────────────────────────────────────────────────────────────────────────────

data class DocumentResponse(
    @SerializedName("n_concepts")    val nConcepts: Int = 0,
    @SerializedName("n_cues_queued") val nCuesQueued: Int = 0,
    val concepts: List<String> = emptyList(),
    @SerializedName("audio_backend") val audioBackend: String = "",
    val filename: String = "",
    @SerializedName("size_bytes")    val sizeBytes: Long = 0L,
    val error: String? = null,       // populated when server-side extraction fails
)

// ─────────────────────────────────────────────────────────────────────────────
// REST — Config  (GET /config, POST /config)
// ─────────────────────────────────────────────────────────────────────────────

data class ConfigRequest(val config: Map<String, Any>)

data class ConfigResponse(
    val ok: Boolean,
    val config: Map<String, Any>,
)

// ─────────────────────────────────────────────────────────────────────────────
// UI state helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Generic async-operation state used by ViewModels. */
sealed class UiState<out T> {
    object Idle    : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T)      : UiState<T>()
    data class Error(val message: String)   : UiState<Nothing>()
}

/** Current session status shown in the Home and Session screens. */
data class SessionUiState(
    val active:    Boolean = false,
    val sessionId: String? = null,
    val mode:      String? = null,
    val elapsedS:  Double  = 0.0,
    val nCues:     Int     = 0,
)
