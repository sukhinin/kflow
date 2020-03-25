package com.github.sukhinin.kflow.decoder

import com.github.sukhinin.kflow.Flow
import io.netty.channel.socket.DatagramPacket

interface PacketDecoder {
    fun decode(pkt: DatagramPacket): Collection<Flow>
}
