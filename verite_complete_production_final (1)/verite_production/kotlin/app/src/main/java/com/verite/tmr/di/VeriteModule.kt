package com.verite.tmr.di

import android.content.Context
import com.verite.tmr.data.audio.VeriteAudioPlayer
import com.verite.tmr.data.network.StudyApi
import com.verite.tmr.data.network.VeriteApi
import com.verite.tmr.data.network.VeriteClient
import com.verite.tmr.data.network.VeriteWebSocket
import com.verite.tmr.data.repository.StudyRepository
import com.verite.tmr.data.repository.VeriteRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VeriteModule {

    @Provides @Singleton
    fun provideVeriteApi(): VeriteApi = VeriteClient.api

    @Provides @Singleton
    fun provideVeriteWebSocket(): VeriteWebSocket =
        VeriteWebSocket(apiKey = VeriteClient.apiKey)

    @Provides @Singleton
    fun provideVeriteRepository(
        api: VeriteApi, webSocket: VeriteWebSocket,
    ): VeriteRepository = VeriteRepository(api, webSocket)

    // ── Study system providers ───────────────────────────────────────────────

    @Provides @Singleton
    fun provideStudyApi(): StudyApi =
        VeriteClient.createService(StudyApi::class.java)

    @Provides @Singleton
    fun provideVeriteAudioPlayer(@ApplicationContext context: Context): VeriteAudioPlayer =
        VeriteAudioPlayer(context)

    @Provides @Singleton
    fun provideStudyRepository(
        api: StudyApi, audioPlayer: VeriteAudioPlayer,
    ): StudyRepository = StudyRepository(api, audioPlayer)
}
