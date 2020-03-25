package com.github.sukhinin.kflow.server

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Reads and parses /proc/net/udp assuming a header followed by lines having the following format:
 * ```
 * sl: local_address:local_port remote_address:remote_port st tx_queue:rx_queue tr:tm->when retrnsmt uid timeout inode ref pointer drops
 * 0   1             2          3              4           5  6        7        8  9        10       11  12      13    14  15      16
 * ```
 *
 * Works on Linux systems only.
 */
class SocketStatsFileReader(
    private val port: Int,
    private val path: Path = Paths.get("/proc/net/udp")
) : SocketStatsReader {

    override fun getSocketStats(): SocketStats {
        val lines = Files.readAllLines(path)
        val rows = lines.asSequence().drop(1)
            .map { line -> line.trim().split(' ', ':').filter(String::isNotEmpty) }
            .filter { cols -> cols.size == 17 && cols[2] == port.toString(16).toUpperCase() }
            .toList()
        val recvQueueSize = rows.sumByDouble { cols -> cols[7].toLong(16).toDouble() }
        val droppedPacketsCount = rows.sumByDouble { cols -> cols[16].toDouble() }
        return SocketStats(recvQueueSize, droppedPacketsCount)
    }
}
