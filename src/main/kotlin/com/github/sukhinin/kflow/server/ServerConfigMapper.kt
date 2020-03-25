package com.github.sukhinin.kflow.server

import com.github.sukhinin.simpleconfig.Config

object ServerConfigMapper {
    fun from(config: Config) =
        ServerConfig(
            port = config.getInteger("port"),
            threads = config.getInteger("threads"),
            bufferSize = config.getInteger("buffer.size")
        )
}
