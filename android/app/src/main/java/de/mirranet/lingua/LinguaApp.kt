package de.mirranet.lingua

import android.app.Application
import de.mirranet.lingua.data.Repository

class LinguaApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
    }
}
