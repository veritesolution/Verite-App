package com.verite.tmr.data.repository

import com.verite.tmr.data.audio.VeriteAudioPlayer
import com.verite.tmr.data.models.*
import com.verite.tmr.data.network.StudyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.Response

class StudyRepository(
    private val api: StudyApi,
    private val audioPlayer: VeriteAudioPlayer,
) {
    // ── Audio state ──────────────────────────────────────────────────────────
    val isPlaying: StateFlow<Boolean> = audioPlayer.isPlaying
    val currentConcept: StateFlow<String> = audioPlayer.currentConcept

    // ── Flashcard ────────────────────────────────────────────────────────────
    suspend fun startStudy(shuffle: Boolean = true): Result<StartStudyResponse> =
        safeCall { api.startStudy(StartStudyRequest(shuffle)) }

    suspend fun nextCard(): Result<FlashCard> =
        safeCall { api.nextCard() }

    suspend fun answerCard(req: CardAnswerRequest): Result<CardAnswerResponse> =
        safeCall { api.answerCard(req) }

    suspend fun completeStudy(): Result<StudyCompleteResponse> =
        safeCall { api.completeStudy() }

    // ── Quiz ─────────────────────────────────────────────────────────────────
    suspend fun startQuiz(mode: String, nQuestions: Int = 10): Result<StartQuizResponse> =
        safeCall { api.startQuiz(StartQuizRequest(mode, nQuestions)) }

    suspend fun getQuestion(): Result<QuizQuestion> =
        safeCall { api.getQuestion() }

    suspend fun answerQuestion(req: QuizAnswerRequest): Result<QuizAnswerResponse> =
        safeCall { api.answerQuestion(req) }

    suspend fun completeQuiz(): Result<QuizCompleteResponse> =
        safeCall { api.completeQuiz() }

    // ── Audio ────────────────────────────────────────────────────────────────
    suspend fun getAudioConcepts(): Result<AudioConceptsResponse> =
        safeCall { api.getAudioConcepts() }

    suspend fun playStudyAudio(conceptKey: String) {
        audioPlayer.playFromUrl("/audio/study/$conceptKey", conceptKey)
    }

    suspend fun playDefinitionAudio(conceptKey: String) {
        audioPlayer.playFromUrl("/audio/definition/$conceptKey", conceptKey)
    }

    suspend fun playCuePreview(conceptKey: String) {
        audioPlayer.playFromUrl("/audio/cue/$conceptKey", conceptKey)
    }

    fun stopAudio() = audioPlayer.stop()

    // ── Helpers ───────────────────────────────────────────────────────────────
    private suspend fun <T> safeCall(block: suspend () -> Response<T>): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val resp = block()
                if (resp.isSuccessful) resp.body()
                    ?: error("Empty response body (HTTP ${resp.code()})")
                else {
                    val err = resp.errorBody()?.string()?.take(500) ?: "Unknown error"
                    throw Exception("HTTP ${resp.code()}: $err")
                }
            }
        }
}
