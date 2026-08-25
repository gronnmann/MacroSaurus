package com.macrosaurus.acquisition

import com.macrosaurus.acquisition.domain.Barcode
import com.macrosaurus.shared.InvalidOperationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BarcodeTest {
    @Test
    fun `normalizes separators and accepts a valid EAN-13`() {
        assertThat(Barcode.normalizeAndValidate("3017 6204-22003")).isEqualTo("3017620422003")
    }

    @Test
    fun `rejects an invalid checksum`() {
        assertThatThrownBy { Barcode.normalizeAndValidate("3017620422004") }
            .isInstanceOf(InvalidOperationException::class.java)
            .hasMessageContaining("checksum")
    }
}
