package com.github.sukhinin.kflow.server

data class SocketStats(val recvQueueSize: Double, val droppedPacketsCount: Double) {
    companion object {
        val UNKNOWN = SocketStats(Double.NaN, Double.NaN)
    }
}
