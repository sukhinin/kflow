package com.github.sukhinin.kflow.sink

import com.github.sukhinin.kflow.Flow
import com.github.sukhinin.kflow.FlowSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

class KafkaFlowSink(private val config: KafkaFlowSinkConfig) : FlowSink, AutoCloseable {
    private val logger = LoggerFactory.getLogger(KafkaFlowSink::class.java)

    private val producerIndex = AtomicLong(0)
    private val producers: Array<KafkaProducer<ByteArray, Flow>> = Array(config.producers) {
        KafkaProducer(config.props, ByteArraySerializer(), FlowSerializer())
    }

    override fun write(flow: Flow) {
        val index = (producerIndex.getAndIncrement() % producers.size).toInt()
        producers[index].send(ProducerRecord(config.topic, flow))
    }

    override fun close() {
        logger.info("Closing Kafka producers")
        for (producer in producers) {
            producer.close(Duration.ofMillis(5000))
        }
    }
}
