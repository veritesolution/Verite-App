package com.example.myapplication.tmr.ui.study

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.tmr.data.models.*
import com.example.myapplication.tmr.data.repository.StudyRepository
import com.example.myapplication.tmr.di.TmrDependencyContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModelProvider

private const val TAG = "StudyViewModel"

class StudyViewModel(
    private val repository: StudyRepository,
) : ViewModel() {

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(StudyViewModel::class.java)) {
                    return StudyViewModel(TmrDependencyContainer.studyRepository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    // ── Flashcard state ──────────────────────────────────────────────────────
    private val _studyState = MutableStateFlow(StudyUiState())
    val studyState: StateFlow<StudyUiState> = _studyState.asStateFlow()

    private val _studyComplete = MutableStateFlow<UiState<StudyCompleteResponse>>(UiState.Idle)
    val studyComplete: StateFlow<UiState<StudyCompleteResponse>> = _studyComplete.asStateFlow()

    // ── Quiz state ───────────────────────────────────────────────────────────
    private val _quizState = MutableStateFlow(QuizUiState())
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    private val _quizComplete = MutableStateFlow<UiState<QuizCompleteResponse>>(UiState.Idle)
    val quizComplete: StateFlow<UiState<QuizCompleteResponse>> = _quizComplete.asStateFlow()

    // ── Audio state ──────────────────────────────────────────────────────────
    private val _audioConcepts = MutableStateFlow<List<ConceptAudioItem>>(emptyList())
    val audioConcepts: StateFlow<List<ConceptAudioItem>> = _audioConcepts.asStateFlow()

    val isPlaying: StateFlow<Boolean> = repository.isPlaying
    val currentPlayingConcept: StateFlow<String> = repository.currentConcept

    // ── Error ────────────────────────────────────────────────────────────────
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var feedbackJob: Job? = null

    // ═══════════════════════════════════════════════════════════════════════
    // FLASHCARD STUDY
    // ═══════════════════════════════════════════════════════════════════════

    fun startStudy(shuffle: Boolean = true) {
        viewModelScope.launch {
            repository.startStudy(shuffle)
                .onSuccess { resp ->
                    _studyState.value = StudyUiState(
                        active = true, nTotal = resp.nConcepts
                    )
                    fetchNextCard()
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun fetchNextCard() {
        viewModelScope.launch {
            repository.nextCard()
                .onSuccess { card ->
                    if (card.done) {
                        completeStudy()
                    } else {
                        _studyState.update {
                            it.copy(currentCard = card, isFlipped = false, lastAnswer = null)
                        }
                    }
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun flipCard() {
        _studyState.update { it.copy(isFlipped = true) }
        // Auto-play definition audio on flip
        val key = _studyState.value.currentCard?.concept ?: return
        viewModelScope.launch { repository.playDefinitionAudio(key) }
    }

    fun submitAnswer(rating: Int) {
        val card = _studyState.value.currentCard ?: return
        viewModelScope.launch {
            repository.answerCard(
                CardAnswerRequest(
                    conceptKey = card.concept,
                    rating = rating,
                    shownAt = card.shownAt,
                )
            ).onSuccess { answer ->
                _studyState.update {
                    it.copy(lastAnswer = answer, nStudied = it.nStudied + 1)
                }
                // Brief pause to show feedback, then next card
                delay(800)
                fetchNextCard()
            }.onFailure { _error.value = it.message }
        }
    }

    private fun completeStudy() {
        viewModelScope.launch {
            _studyComplete.value = UiState.Loading
            repository.completeStudy()
                .onSuccess { resp ->
                    _studyComplete.value = UiState.Success(resp)
                    _studyState.update { it.copy(active = false) }
                    Log.i(TAG, "Study complete: ${resp.nCuesQueued} cues from real data")
                }
                .onFailure { _studyComplete.value = UiState.Error(it.message ?: "Failed") }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // QUIZ
    // ═══════════════════════════════════════════════════════════════════════

    fun startQuiz(mode: String = "pre_sleep") {
        viewModelScope.launch {
            repository.startQuiz(mode)
                .onSuccess { resp ->
                    _quizState.value = QuizUiState(
                        active = true, mode = mode, nTotal = resp.nQuestions
                    )
                    _quizComplete.value = UiState.Idle
                    fetchNextQuestion()
                }
                .onFailure { _error.value = it.message }
        }
    }

    private fun fetchNextQuestion() {
        viewModelScope.launch {
            repository.getQuestion()
                .onSuccess { q ->
                    if (q.done) {
                        completeQuiz()
                    } else {
                        _quizState.update {
                            it.copy(currentQuestion = q, lastAnswer = null, showingFeedback = false)
                        }
                    }
                }
                .onFailure { _error.value = it.message }
        }
    }

    fun answerQuestion(selectedIndex: Int) {
        val q = _quizState.value.currentQuestion ?: return
        viewModelScope.launch {
            repository.answerQuestion(
                QuizAnswerRequest(
                    conceptKey = q.concept,
                    selectedIndex = selectedIndex,
                    shownAt = q.shownAt,
                )
            ).onSuccess { answer ->
                _quizState.update {
                    it.copy(
                        lastAnswer = answer,
                        showingFeedback = true,
                        nAnswered = it.nAnswered + 1,
                        nCorrect = it.nCorrect + if (answer.correct) 1 else 0,
                    )
                }
                // Show feedback for 2 seconds then advance
                feedbackJob?.cancel()
                feedbackJob = viewModelScope.launch {
                    delay(2_000)
                    _quizState.update { it.copy(showingFeedback = false) }
                    if (!answer.done) fetchNextQuestion()
                    else completeQuiz()
                }
            }.onFailure { _error.value = it.message }
        }
    }

    private fun completeQuiz() {
        viewModelScope.launch {
            _quizComplete.value = UiState.Loading
            repository.completeQuiz()
                .onSuccess { resp ->
                    _quizComplete.value = UiState.Success(resp)
                    _quizState.update { it.copy(active = false) }
                    Log.i(TAG, "Quiz complete: ${resp.accuracyPct}% (${resp.mode})")
                }
                .onFailure { _quizComplete.value = UiState.Error(it.message ?: "Failed") }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // AUDIO
    // ═══════════════════════════════════════════════════════════════════════

    fun loadAudioConcepts() {
        viewModelScope.launch {
            repository.getAudioConcepts()
                .onSuccess { _audioConcepts.value = it.concepts }
                .onFailure { _error.value = it.message }
        }
    }

    fun playStudyAudio(key: String) {
        viewModelScope.launch { repository.playStudyAudio(key) }
    }

    fun playCuePreview(key: String) {
        viewModelScope.launch { repository.playCuePreview(key) }
    }

    fun stopAudio() = repository.stopAudio()

    fun clearError() { _error.value = null }

    override fun onCleared() {
        super.onCleared()
        feedbackJob?.cancel()
        repository.stopAudio()
    }
}
