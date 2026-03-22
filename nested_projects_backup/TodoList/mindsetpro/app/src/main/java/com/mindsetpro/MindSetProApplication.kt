package com.mindsetpro

import android.app.Application
import com.mindsetpro.data.local.MindSetDatabase

class MindSetProApplication : Application() {

    lateinit var database: MindSetDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = MindSetDatabase.getInstance(this)
    }

    companion object {
        lateinit var instance: MindSetProApplication
            private set
    }
}
