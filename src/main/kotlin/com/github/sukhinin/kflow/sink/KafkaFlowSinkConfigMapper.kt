package com.github.sukhinin.kflow.sink

import com.github.sukhinin.simpleconfig.Config
import com.github.sukhinin.simpleconfig.getInteger
import com.github.sukhinin.simpleconfig.scoped
import com.github.sukhinin.simpleconfig.toProperties

object KafkaFlowSinkConfigMapper {
    fun from(config: Config) =
        KafkaFlowSinkConfig(
            topic = config.get("topic"),
            producers = config.getInteger("producers"),
            props = config.scoped("props").toProperties()
        )
}
