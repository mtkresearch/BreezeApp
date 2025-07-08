package com.mtkresearch.breezeapp.router.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.IAIRouterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages the connection to the remote IAIRouterService.
 *
 * This class encapsulates the logic for binding, unbinding, and monitoring the
 * connection state to the AIDL service. It exposes the service interface and
 * the connection status through reactive [StateFlow]s, abstracting away the
 * complexities of [ServiceConnection].
 *
 * This is the lowest-level networking class in the client app.
 */
class AIRouterClient(private val context: Context) {

    private val _routerService = MutableStateFlow<IAIRouterService?>(null)
    val routerService = _routerService.asStateFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState = _connectionState.asStateFlow()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service connected.")
            _routerService.value = IAIRouterService.Stub.asInterface(service)
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected.")
            _routerService.value = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.e(TAG, "Service binding died.")
            _routerService.value = null
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Initiates a connection to the remote service.
     */
    fun connect() {
        if (_connectionState.value != ConnectionState.DISCONNECTED) {
            Log.w(TAG, "Already connected or connecting.")
            return
        }
        
        _connectionState.value = ConnectionState.CONNECTING
        val intent = Intent().apply {
            component = ComponentName(
                "com.mtkresearch.breezeapp.router",
                "com.mtkresearch.breezeapp.router.AIRouterService"
            )
        }
        try {
            val success = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!success) {
                Log.e(TAG, "Failed to bind to service. Is the router app installed?")
                _connectionState.value = ConnectionState.ERROR
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to bind to service due to SecurityException. Check permissions.", e)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    /**
     * Disconnects from the remote service.
     */
    fun disconnect() {
        if (_connectionState.value == ConnectionState.DISCONNECTED) return
        context.unbindService(serviceConnection)
        _routerService.value = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Log.d(TAG, "Service unbound and disconnected.")
    }

    companion object {
        private const val TAG = "AIRouterClient"
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
} 