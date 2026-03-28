package com.verite.tmr.data.network

import com.verite.tmr.data.models.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface StudyApi {

    // ── Flashcard Study ──────────────────────────────────────────────────────
    @POST("study/start")
    suspend fun startStudy(@Body req: StartStudyRequest): Response<StartStudyResponse>

    @GET("study/card/next")
    suspend fun nextCard(): Response<FlashCard>

    @POST("study/card/answer")
    suspend fun answerCard(@Body req: CardAnswerRequest): Response<CardAnswerResponse>

    @POST("study/complete")
    suspend fun completeStudy(): Response<StudyCompleteResponse>

    // ── Quiz ─────────────────────────────────────────────────────────────────
    @POST("quiz/start")
    suspend fun startQuiz(@Body req: StartQuizRequest): Response<StartQuizResponse>

    @GET("quiz/question")
    suspend fun getQuestion(): Response<QuizQuestion>

    @POST("quiz/answer")
    suspend fun answerQuestion(@Body req: QuizAnswerRequest): Response<QuizAnswerResponse>

    @POST("quiz/complete")
    suspend fun completeQuiz(): Response<QuizCompleteResponse>

    // ── Audio ────────────────────────────────────────────────────────────────
    @GET("audio/concepts")
    suspend fun getAudioConcepts(): Response<AudioConceptsResponse>

    @GET("audio/study/{key}")
    @Streaming
    suspend fun getStudyAudio(@Path("key") key: String): Response<ResponseBody>

    @GET("audio/definition/{key}")
    @Streaming
    suspend fun getDefinitionAudio(@Path("key") key: String): Response<ResponseBody>

    @GET("audio/cue/{key}")
    @Streaming
    suspend fun getCuePreview(@Path("key") key: String): Response<ResponseBody>
}
