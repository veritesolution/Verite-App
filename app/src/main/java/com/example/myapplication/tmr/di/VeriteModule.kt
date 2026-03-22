package com.example.myapplication.tmr.di

import android.content.Context
import com.example.myapplication.tmr.data.audio.VeriteAudioPlayer
import com.example.myapplication.tmr.data.network.StudyApi
import com.example.myapplication.tmr.data.network.VeriteApi
import com.example.myapplication.tmr.data.network.VeriteClient
import com.example.myapplication.tmr.data.network.VeriteWebSocket
import com.example.myapplication.tmr.data.repository.StudyRepository
import com.example.myapplication.tmr.data.repository.VeriteRepository

object TmrDependencyContainer {

    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    val veriteApi: VeriteApi by lazy { VeriteClient.api }

    val veriteWebSocket: VeriteWebSocket by lazy { VeriteWebSocket(apiKey = VeriteClient.apiKey) }

    val veriteRepository: VeriteRepository by lazy { VeriteRepository(veriteApi, veriteWebSocket) }

    // ── Study system providers ───────────────────────────────────────────────

    val studyApi: StudyApi by lazy { VeriteClient.createService(StudyApi::class.java) }

    val veriteAudioPlayer: VeriteAudioPlayer by lazy { 
        VeriteAudioPlayer(applicationContext ?: error("TmrDependencyContainer not initialized with context")) 
    }

    val studyRepository: StudyRepository by lazy { StudyRepository(studyApi, veriteAudioPlayer) }
}
