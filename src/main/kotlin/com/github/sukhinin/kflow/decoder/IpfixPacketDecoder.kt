package com.github.sukhinin.kflow.decoder

import com.github.sukhinin.kflow.Flow
import com.github.sukhinin.kflow.utils.InetUtils
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.Tags
import io.netty.buffer.ByteBuf
import io.netty.channel.socket.DatagramPacket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * IPFIX packet decoder.
 *
 * @see <a href="https://tools.ietf.org/html/rfc7011">RFC 7011</a>,
 * <a href="https://tools.ietf.org/html/rfc7012">RFC 7012</a>,
 * <a href="https://www.iana.org/assignments/ipfix/ipfix.xhtml">IPFIX Entities</a>
 */
class IpfixPacketDecoder : PacketDecoder {
    private companion object {
        const val FIELD_OCTET_DELTA_COUNT = 1
        const val FIELD_PACKET_DELTA_COUNT = 2
        const val FIELD_PROTOCOL_IDENTIFIER = 4
        const val FIELD_TCP_CONTROL_BITS = 6
        const val FIELD_SOURCE_TRANSPORT_PORT = 7
        const val FIELD_SOURCE_IPV4_ADDRESS = 8
        const val FIELD_DESTINATION_TRANSPORT_PORT = 11
        const val FIELD_DESTINATION_IPV4_ADDRESS = 12

        const val ERROR_INVALID_PROTOCOL_VERSION = "invalid protocol version"
        const val ERROR_INVALID_SET_ID = "invalid set id"
        const val ERROR_INVALID_TEMPLATE_ID = "invalid template id"
        const val ERROR_UNKNOWN_TEMPLATE_ID = "unknown template id"
        const val ERROR_MALFORMED_PACKET = "malformed packet"

        const val PACKETS_METRIC_NAME = "decoder.ipfix.packets"
        const val TEMPLATES_METRIC_NAME = "decoder.ipfix.templates"
        const val ERRORS_METRIC_NAME = "decoder.ipfix.errors"
        const val FLOWS_METRIC_NAME = "decoder.ipfix.flows"
    }

    private val packetCounter = Metrics.counter(PACKETS_METRIC_NAME)
    private val flowsCounter = Metrics.counter(FLOWS_METRIC_NAME)
    private val templates = ConcurrentHashMap<TemplateKey, Template>()

    init {
        Metrics.gaugeMapSize(TEMPLATES_METRIC_NAME, Tags.empty(), templates)
    }

    override fun decode(pkt: DatagramPacket): Collection<Flow> {
        try {
            val flows = ArrayList<Flow>(128)
            packetCounter.increment()

            val samplerAddress = InetUtils.bton(pkt.sender().address.address)
            val packetBuffer = pkt.content()

            val versionNumber = packetBuffer.readUnsignedShort()
            if (versionNumber != 10) {
                throw DecoderException("IPFIX version mismatch: expected 10, got $versionNumber", ERROR_INVALID_PROTOCOL_VERSION)
            }

            val packetLength = packetBuffer.readUnsignedShort()
            val exportTime = packetBuffer.readUnsignedInt()
            val sequenceNumber = packetBuffer.readUnsignedInt()
            val observationDomainId = packetBuffer.readUnsignedInt()

            packetBuffer.readBytes(packetLength - 16).use { messageBuffer ->
                while (messageBuffer.readableBytes() > 0) {
                    val setId = messageBuffer.readUnsignedShort()
                    val length = messageBuffer.readUnsignedShort()
                    messageBuffer.readBytes(length - 4).use { setBuffer ->
                        when (setId) {
                            2 -> decodeTemplateSet(setBuffer, samplerAddress, observationDomainId, false)
                            3 -> decodeTemplateSet(setBuffer, samplerAddress, observationDomainId, true)
                            in 256..65535 -> decodeDataSet(setBuffer, samplerAddress, observationDomainId, setId, exportTime, flows)
                            else -> throw DecoderException("Invalid set ID: $setId", ERROR_INVALID_SET_ID)
                        }
                    }
                }
            }
            return flows
        } catch (e: DecoderException) {
            registerDecoderError(e.error)
        } catch (e: Exception) {
            registerDecoderError("exception ${e.javaClass.simpleName}")
        }
        return emptyList()
    }

    private fun decodeTemplateSet(buf: ByteBuf, samplerAddress: Int, observationDomainId: Long, scoped: Boolean) {
        while (buf.readableBytes() >= 4) {
            val templateId = buf.readUnsignedShort()
            if (templateId !in 256..65535) {
                throw DecoderException("Invalid template ID: $templateId", ERROR_INVALID_TEMPLATE_ID)
            }

            val count = buf.readUnsignedShort()
            val scopeFieldCount = if (scoped) buf.readUnsignedShort() else 0
            val fields = LinkedList<TemplateField>()

            repeat(count) {
                val id = buf.readUnsignedShort()
                val length = buf.readUnsignedShort()
                val enterpriseNumber = if (id and 0x8000 != 0) buf.readUnsignedInt() else 0
                val field = TemplateField(id, length, enterpriseNumber)
                fields.add(field)
            }

            val key = TemplateKey(samplerAddress, observationDomainId, templateId)
            val template = Template(fields, scopeFieldCount)
            templates[key] = template
        }
    }

    private fun decodeDataSet(buf: ByteBuf, samplerAddress: Int, observationDomainId: Long, templateId: Int, exportTime: Long, flows: MutableList<Flow>) {
        val key = TemplateKey(samplerAddress, observationDomainId, templateId)
        val template = templates[key]
            ?: throw DecoderException("Unknown template ID: $templateId", ERROR_UNKNOWN_TEMPLATE_ID)

        while (buf.readableBytes() >= template.length) {
            val flow = Flow()
            flow.timestamp = exportTime * 1000 // exportTime is in seconds, timestamp is in milliseconds
            flow.samplerAddress = samplerAddress
            for (field in template.fields) {
                when (field.id) {
                    FIELD_OCTET_DELTA_COUNT -> flow.bytes = buf.readCounter(field.length)
                    FIELD_PACKET_DELTA_COUNT -> flow.packets = buf.readCounter(field.length)
                    FIELD_PROTOCOL_IDENTIFIER -> flow.protocol = buf.readProtocol(field.length)
                    FIELD_TCP_CONTROL_BITS -> flow.flags = buf.readFlags(field.length)
                    FIELD_SOURCE_TRANSPORT_PORT -> flow.sourcePort = buf.readPort(field.length)
                    FIELD_SOURCE_IPV4_ADDRESS -> flow.sourceAddress = buf.readAddress(field.length)
                    FIELD_DESTINATION_TRANSPORT_PORT -> flow.destinationPort = buf.readPort(field.length)
                    FIELD_DESTINATION_IPV4_ADDRESS -> flow.destinationAddress = buf.readAddress(field.length)
                    else -> buf.skipBytes(field.length)
                }
            }
            flows.add(flow)
            flowsCounter.increment()
        }
    }

    private fun registerDecoderError(error: String) {
        Counter.builder(ERRORS_METRIC_NAME)
            .tag("error", error)
            .register(Metrics.globalRegistry)
            .increment()
    }

    private inline fun ByteBuf.use(block: (ByteBuf) -> Unit) {
        try {
            block(this)
        } finally {
            this.release()
        }
    }

    private fun ByteBuf.readAddress(length: Int): Int {
        return when (length) {
            4 -> readInt()
            else -> throw DecoderException("Cannot read address value of length $length", ERROR_MALFORMED_PACKET)
        }
    }

    private fun ByteBuf.readPort(length: Int): Int {
        return when (length) {
            2 -> readUnsignedShort()
            else -> throw DecoderException("Cannot read port value of length $length", ERROR_MALFORMED_PACKET)
        }
    }

    private fun ByteBuf.readProtocol(length: Int): Int {
        return when (length) {
            1 -> readByte().toInt() and 0xff
            else -> throw DecoderException("Cannot read protocol value of length $length", ERROR_MALFORMED_PACKET)
        }
    }

    private fun ByteBuf.readFlags(length: Int): Int {
        // IPFIX states flags field is unsigned16 but at least ipt_netflow exports flags as unsigned8
        return when(length) {
            1 -> readUnsignedByte().toInt()
            2 -> readUnsignedShort()
            else -> throw DecoderException("Cannot read flags value of length $length", ERROR_MALFORMED_PACKET)
        }
    }

    private fun ByteBuf.readCounter(length: Int): Long {
        return when (length) {
            1 -> readByte().toLong() and 0xff
            2 -> readUnsignedShort().toLong()
            4 -> readUnsignedInt()
            8 -> readLong()
            else -> throw DecoderException("Cannot read counter value of length $length", ERROR_MALFORMED_PACKET)
        }
    }

    private data class TemplateKey(val samplerAddress: Int, val observationDomainId: Long, val templateId: Int)

    private class Template(val fields: Iterable<TemplateField>, val scopeFieldCount: Int) {
        val length = fields.sumBy { it.length }
    }

    private class TemplateField(val id: Int, val length: Int, val enterpriseNumber: Long)
}
