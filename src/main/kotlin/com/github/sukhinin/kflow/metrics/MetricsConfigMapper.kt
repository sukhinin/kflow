package com.github.sukhinin.kflow.metrics

import com.github.sukhinin.simpleconfig.Config

object MetricsConfigMapper {
    fun from(config: Config) =
        MetricsConfig(
            port = config.getInteger("port")
        )
}
