package com.github.sukhinin.kflow

import io.kotlintest.shouldBe
import io.kotlintest.specs.ShouldSpec

internal class FlowSerializerTest : ShouldSpec({

    val serializer = FlowSerializer()

    should("serialize flow to json") {
        val flow = Flow(
            samplerAddress = 9 * 16777216 + 9 * 65536 + 9 * 256 + 9,
            sourceAddress = 1 * 16777216 + 2 * 65536 + 3 * 256 + 4,
            sourcePort = 10,
            destinationAddress = 5 * 16777216 + 6 * 65536 + 7 * 256 + 8,
            destinationPort = 20,
            protocol = 99,
            flags = 42,
            bytes = 123,
            packets = 456,
            timestamp = 1234567890000L
        )
        val expected = """{"dvc":"9.9.9.9","src":"1.2.3.4","srcp":10,"dst":"5.6.7.8","dstp":20,"proto":99,"flags":42,"bytes":123,"pkts":456,"time":1234567890000}"""

        serializer.serialize("", flow) shouldBe expected.toByteArray()
    }
})
