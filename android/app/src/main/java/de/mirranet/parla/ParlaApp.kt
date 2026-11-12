package de.mirranet.parla

import android.app.Application
import de.mirranet.parla.data.AndroidPlatform
import de.mirranet.parla.data.Repository

class ParlaApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = AndroidPlatform.createRepository(this)
    }
}
