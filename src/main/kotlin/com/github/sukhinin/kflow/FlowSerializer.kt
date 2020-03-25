package com.github.sukhinin.kflow

import com.github.sukhinin.kflow.utils.InetUtils.ntoa
import org.apache.kafka.common.serialization.Serializer

class FlowSerializer : Serializer<Flow> {
    override fun serialize(topic: String, flow: Flow): ByteArray {
        val builder = StringBuilder(200)
        builder.append("{")

        builder.append("\"dvc\":\"")
        builder.append(ntoa(flow.samplerAddress))
        builder.append("\",")

        builder.append("\"src\":\"")
        builder.append(ntoa(flow.sourceAddress))
        builder.append("\",")

        builder.append("\"srcp\":")
        builder.append(flow.sourcePort)
        builder.append(",")

        builder.append("\"dst\":\"")
        builder.append(ntoa(flow.destinationAddress))
        builder.append("\",")

        builder.append("\"dstp\":")
        builder.append(flow.destinationPort)
        builder.append(",")

        builder.append("\"proto\":")
        builder.append(flow.protocol)
        builder.append(",")

        builder.append("\"flags\":")
        builder.append(flow.flags)
        builder.append(",")

        builder.append("\"bytes\":")
        builder.append(flow.bytes)
        builder.append(",")

        builder.append("\"pkts\":")
        builder.append(flow.packets)
        builder.append(",")

        builder.append("\"time\":")
        builder.append(flow.timestamp)

        builder.append("}")
        return builder.toString().toByteArray(Charsets.US_ASCII)
    }
}
