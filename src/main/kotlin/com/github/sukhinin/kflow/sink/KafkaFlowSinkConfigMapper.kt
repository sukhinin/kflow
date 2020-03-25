package com.github.sukhinin.kflow.sink

import com.github.sukhinin.simpleconfig.Config
import com.github.sukhinin.simpleconfig.scoped

object KafkaFlowSinkConfigMapper {
    fun from(config: Config) =
        KafkaFlowSinkConfig(
            topic = config.get("topic"),
            producers = config.getInteger("producers"),
            props = config.scoped("props").toProperties()
        )
}
