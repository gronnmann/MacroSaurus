package com.macrosaurus.acquisition.domain

import com.macrosaurus.shared.InvalidOperationException

object Barcode {
    fun normalizeAndValidate(raw: String): String {
        val code = raw.filter(Char::isDigit)
        if (code.length !in setOf(8, 12, 13, 14)) {
            throw InvalidOperationException("EAN/UPC must contain 8, 12, 13, or 14 digits")
        }
        val check =
            code
                .dropLast(1)
                .reversed()
                .mapIndexed { index, char -> char.digitToInt() * if (index % 2 == 0) 3 else 1 }
                .sum()
        val expected = (10 - check % 10) % 10
        if (expected != code.last().digitToInt()) throw InvalidOperationException("EAN/UPC checksum is invalid")
        return code
    }
}
