package com.brokenbored.neo

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.brokenbored.neo.data.KeyStore
import com.brokenbored.neo.data.NeoDatabase
import com.brokenbored.neo.data.SettingsStore
import com.brokenbored.neo.providers.ProviderFactory
import com.brokenbored.neo.terminal.TermuxRunner
/** Manual dependency container — no DI framework, everything is built here. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: NeoDatabase = Room.databaseBuilder(
        appContext,
        NeoDatabase::class.java,
        "neo.db"
    ).fallbackToDestructiveMigration().build()

    val dao = database.dao()
    val settings = SettingsStore(appContext)
    val keys = KeyStore(appContext)
    val termux = TermuxRunner(appContext)
    val providers = ProviderFactory(settings, keys)
}

class NeoApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The result receiver can fire before any Activity exists.
        TermuxRunner.shared = container.termux
    }
}

/** Convenience accessor from Activities and Composables. */
fun Context.appContainer(): AppContainer =
    (applicationContext as NeoApplication).container
