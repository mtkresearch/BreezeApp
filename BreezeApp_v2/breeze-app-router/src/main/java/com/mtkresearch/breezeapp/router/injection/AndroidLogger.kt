package com.mtkresearch.breezeapp.router.injection

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.common.Logger

/**
 * An implementation of the [Logger] interface that uses the standard Android Log class.
 * This class acts as the bridge between the abstract domain logger and the concrete
 * Android framework implementation.
 */
class AndroidLogger : Logger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
} 