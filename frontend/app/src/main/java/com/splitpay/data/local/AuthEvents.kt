package com.splitpay.data.local

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object AuthEvents {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired = _sessionExpired.asSharedFlow()
    fun notifyExpired() { _sessionExpired.tryEmit(Unit) }

    private val _accountSuspended = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val accountSuspended = _accountSuspended.asSharedFlow()
    fun notifySuspended(message: String) { _accountSuspended.tryEmit(message) }
}
