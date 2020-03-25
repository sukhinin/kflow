package com.github.sukhinin.kflow.sink

import com.github.sukhinin.kflow.Flow

interface FlowSink {
    fun write(flow: Flow)
}
