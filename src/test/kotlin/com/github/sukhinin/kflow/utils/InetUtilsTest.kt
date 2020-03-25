package com.github.sukhinin.kflow.utils

import com.github.sukhinin.kflow.utils.InetUtils.bton
import com.github.sukhinin.kflow.utils.InetUtils.ntoa
import io.kotlintest.assertSoftly
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.FunSpec

@Suppress("INTEGER_OVERFLOW")
internal class InetUtilsTest : FunSpec({
    test("ntoa() should return IPv4 address string for an integer address representation") {
        assertSoftly {
            ntoa(1 * 16777216 + 2 * 65536 + 3 * 256 + 4) shouldBe "1.2.3.4"
            ntoa(255 * 16777216 + 254 * 65536 + 253 * 256 + 252) shouldBe "255.254.253.252"
        }
    }

    test("bton() should return integer address representation for byte array") {
        assertSoftly {
            bton(listOf<Byte>(1, 2, 3, 4).toByteArray()) shouldBe 1 * 16777216 + 2 * 65536 + 3 * 256 + 4
            bton(listOf<Byte>(-1, -2, -3, -4).toByteArray()) shouldBe 255 * 16777216 + 254 * 65536 + 253 * 256 + 252
        }
    }

    test("bton() should throw on invalid byte array length") {
        assertSoftly {
            shouldThrow<IllegalArgumentException> { bton(listOf<Byte>(1, 2, 3).toByteArray()) }
            shouldThrow<IllegalArgumentException> { bton(listOf<Byte>(1, 2, 3, 4, 5).toByteArray()) }
        }
    }
})
