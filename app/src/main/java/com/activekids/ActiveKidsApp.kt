package com.activekids

import android.app.Application
import android.content.Context
import com.activekids.audio.SoundFx
import com.activekids.core.NoticeCenter
import com.activekids.game.GameEngine
import com.activekids.settings.SettingsRepository

class ActiveKidsApp : Application() {
    lateinit var notices: NoticeCenter
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var sound: SoundFx
        private set
    lateinit var engine: GameEngine
        private set

    override fun onCreate() {
        super.onCreate()
        notices = NoticeCenter()
        settings = SettingsRepository(this, notices)
        sound = SoundFx(notices)
        engine = GameEngine(settings, sound, notices)
    }
}

val Context.app: ActiveKidsApp get() = applicationContext as ActiveKidsApp
