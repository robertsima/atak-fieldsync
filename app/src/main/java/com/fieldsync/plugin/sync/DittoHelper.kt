package com.fieldsync.plugin.sync

import android.content.Context
import androidx.startup.AppInitializer
import com.ditto.kotlin.Ditto
import com.ditto.kotlin.DittoAuthenticationProvider
import com.ditto.kotlin.DittoConfig
import com.ditto.kotlin.DittoException
import com.ditto.kotlin.DittoFactory
import com.ditto.kotlin.DittoInitializer
import com.ditto.kotlin.DittoStoreObserver
import com.ditto.kotlin.DittoSyncSubscription
import kotlinx.coroutines.runBlocking

object DittoHelper {

    @JvmStatic
    fun initialize(ctx: Context) {
        AppInitializer.getInstance(ctx.applicationContext)
            .initializeComponent(DittoInitializer::class.java)
    }

    @JvmStatic
    fun createDitto(appId: String, serverUrl: String): Ditto {
        val config = DittoConfig(
            databaseId = appId,
            connect = DittoConfig.Connect.Server(serverUrl)
        )
        return DittoFactory.create(config)
    }

    @JvmStatic
    fun setupAuth(ditto: Ditto, token: String) {
        ditto.auth?.let { auth ->
            auth.expirationHandler = { dittoInstance, _ ->
                dittoInstance.auth?.login(token, DittoAuthenticationProvider.development())
            }
        }
    }

    @JvmStatic
    @Throws(DittoException::class)
    fun execute(ditto: Ditto, query: String, args: Map<String, Any?>) {
        runBlocking { ditto.store.execute(query, args) }
    }

    @JvmStatic
    fun registerSubscription(ditto: Ditto, query: String): DittoSyncSubscription =
        ditto.sync.registerSubscription(query)

    @JvmStatic
    fun registerObserver(
        ditto: Ditto,
        query: String,
        callback: ResultCallback
    ): DittoStoreObserver =
        ditto.store.registerObserver(query) { callback.onResult(it) }

    @JvmStatic
    fun registerObserverWithArgs(
        ditto: Ditto,
        query: String,
        args: Map<String, Any?>,
        callback: ResultCallback
    ): DittoStoreObserver =
        ditto.store.registerObserver(query, args) { callback.onResult(it) }

    @JvmStatic
    fun startSync(ditto: Ditto) = ditto.sync.start()

    @JvmStatic
    fun stopSync(ditto: Ditto) = ditto.sync.stop()

    @JvmStatic
    fun isSyncActive(ditto: Ditto): Boolean = ditto.sync.isActive
}
