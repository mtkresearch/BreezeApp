package com.mtkresearch.breezeapp.router.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages the connection to the AI Router Service.
 *
 * This client encapsulates the logic of binding and unbinding to the service,
 * handling connection lifecycle events, and exposing the service interface and
 * its connection state in a reactive way.
 */
class AIRouterClient(private val context: Context) {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _routerService = MutableStateFlow<IAIRouterService?>(null)
    val routerService: StateFlow<IAIRouterService?> = _routerService.asStateFlow()

    private val TAG = "AIRouterClient"
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            _routerService.value = IAIRouterService.Stub.asInterface(service)
            _connectionState.value = ConnectionState.CONNECTED
            isBound = true
            Log.i(TAG, "✅ AI Router Service connected.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _routerService.value = null
            _connectionState.value = ConnectionState.DISCONNECTED
            isBound = false
            Log.w(TAG, "ℹ️ AI Router Service disconnected.")
        }

        override fun onBindingDied(name: ComponentName?) {
            _routerService.value = null
            _connectionState.value = ConnectionState.ERROR
            isBound = false
            Log.e(TAG, "❌ Binding to AI Router Service died.")
        }
    }

    /**
     * Binds to the AI Router Service.
     * It will try to find the service and establish a connection.
     */
    fun connect() {
        if (isBound) return
        _connectionState.update { ConnectionState.CONNECTING }
        Log.d(TAG, "🔄 Attempting to connect to AI Router Service...")

        val intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService").apply {
            setPackage("com.mtkresearch.breezeapp.router")
        }

        try {
            val success = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!success) {
                _connectionState.value = ConnectionState.ERROR
                Log.e(TAG, "❌ bindService returned false. Is the router app installed?")
            }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.ERROR
            Log.e(TAG, "❌ SecurityException on bind. Check client permissions.", e)
        }
    }

    /**
     * Unbinds from the AI Router Service.
     */
    fun disconnect() {
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
            _routerService.value = null
            _connectionState.value = ConnectionState.DISCONNECTED
            Log.i(TAG, "🔌 Disconnected from service.")
        }
    }
}

/**
 * Represents the connection state to the AI Router Service.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
} 