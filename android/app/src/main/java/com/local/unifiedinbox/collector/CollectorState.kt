package com.local.unifiedinbox.collector

import java.util.concurrent.atomic.AtomicBoolean

object CollectorState {
    private val connected = AtomicBoolean(false)

    fun setConnected(value: Boolean) {
        connected.set(value)
    }

    fun isConnected(): Boolean = connected.get()
}
