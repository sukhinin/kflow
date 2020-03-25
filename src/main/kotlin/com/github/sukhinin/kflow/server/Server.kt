package com.github.sukhinin.kflow.server

import com.github.sukhinin.kflow.decoder.PacketDecoder
import com.github.sukhinin.kflow.sink.FlowSink
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Metrics
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollChannelOption
import io.netty.channel.epoll.EpollDatagramChannel
import io.netty.channel.epoll.EpollEventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioChannelOption
import io.netty.channel.socket.nio.NioDatagramChannel
import org.slf4j.LoggerFactory

class Server(private val config: ServerConfig, private val decoder: PacketDecoder, private val sink: FlowSink) {

    companion object {
        private val logger = LoggerFactory.getLogger(Server::class.java)
    }

    private val bootstrap: Bootstrap = Bootstrap()
    private val channelFutures: MutableList<ChannelFuture> = ArrayList()

    init {
        setupNettyServer()
        setupSocketStats()
    }

    private fun setupNettyServer() {
        if (Epoll.isAvailable()) {
            logger.info("Using native epoll() support")
            bootstrap.group(EpollEventLoopGroup(config.threads))
            bootstrap.channel(EpollDatagramChannel::class.java)
            bootstrap.option(EpollChannelOption.SO_REUSEPORT, true)
        } else {
            logger.warn("Native epoll() support is not available, performance may be severely degraded")
            bootstrap.group(NioEventLoopGroup(config.threads))
            bootstrap.channel(NioDatagramChannel::class.java)
            bootstrap.option(NioChannelOption.SO_REUSEADDR, true)
        }
        bootstrap.option(ChannelOption.SO_RCVBUF, config.bufferSize)
        bootstrap.handler(object : ChannelInitializer<Channel>() {
            override fun initChannel(ch: Channel) {
                val handler = PacketHandler(decoder, sink)
                ch.pipeline().addLast(handler)
            }
        })
    }

    private fun setupSocketStats() {
        val stats = SocketStatsCachingReader(SocketStatsFileReader(config.port))

        // Micrometer holds weak references only so we pass 'this' to builder and capture stats object in closure
        FunctionCounter.builder("server.packet_drops", this) { stats.getSocketStats().droppedPacketsCount }
            .description("Dropped packets count")
            .register(Metrics.globalRegistry)
        Gauge.builder("server.receive_queue", this) { stats.getSocketStats().recvQueueSize }
            .description("Receive queue size")
            .register(Metrics.globalRegistry)
    }

    fun start() {
        logger.info("Starting UDP server on port ${config.port}")
        // EpollDatagramChannel with SO_REUSEPORT load balances received datagrams between all channels.
        // NioDatagramChannel with SO_REUSEADDR allows multiple channels to be bound to the same port
        // but with all datagrams routed to the last channel only.
        for (i in 0 until config.threads) {
            val future = bootstrap.bind(config.port).await()
            if (!future.isSuccess) {
                throw RuntimeException("Failed to bind to port ${config.port}", future.cause())
            }
            channelFutures.add(future)
        }
    }

    fun stop() {
        logger.info("Stopping UDP server on port ${config.port}")
        bootstrap.config().group().shutdownGracefully()
        for (future in channelFutures) {
            future.channel().closeFuture().await()
        }
    }
}
