package com.github.sukhinin.kflow.server

import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

class SocketStatsCachingReader(
    private val reader: SocketStatsReader,
    private val validity: Duration = Duration.ofSeconds(15)
) : SocketStatsReader {

    private val logger = LoggerFactory.getLogger(SocketStatsCachingReader::class.java)

    // Should only be changed in synchronized getSocketStats()
    private var stats = SocketStats.UNKNOWN
    private var updated = Instant.EPOCH

    @Synchronized
    override fun getSocketStats(): SocketStats {
        if (Duration.between(updated, Instant.now()) > validity) {
            try {
                stats = reader.getSocketStats()
                updated = Instant.now()
            } catch (e: Exception) {
                logger.error("Error reading socket stats, disabling further updates", e)
                stats = SocketStats.UNKNOWN
                updated = Instant.MAX
            }
        }
        return stats
    }
}
