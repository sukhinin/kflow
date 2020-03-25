package com.github.sukhinin.kflow.server

import com.github.sukhinin.kflow.decoder.PacketDecoder
import com.github.sukhinin.kflow.sink.FlowSink
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.socket.DatagramPacket

class PacketHandler(private val decoder: PacketDecoder, private val sink: FlowSink) :
    SimpleChannelInboundHandler<DatagramPacket>() {

    override fun channelRead0(ctx: ChannelHandlerContext, pkt: DatagramPacket) {
        val flows = decoder.decode(pkt)
        for (flow in flows) {
            sink.write(flow)
        }
    }
}
