package com.github.sukhinin.kflow.sink

import java.util.*

data class KafkaFlowSinkConfig(val topic: String, val producers: Int, val props: Properties)

