package com.github.sukhinin.kflow

data class Flow(
    var samplerAddress: Int = 0,
    var sourceAddress: Int = 0,
    var sourcePort: Int = 0,
    var destinationAddress: Int = 0,
    var destinationPort: Int = 0,
    var protocol: Int = 0,
    var flags: Int = 0,
    var bytes: Long = 0,
    var packets: Long = 0,
    var timestamp: Long = 0
)
