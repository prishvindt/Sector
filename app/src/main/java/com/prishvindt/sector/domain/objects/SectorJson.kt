package com.prishvindt.sector.domain.objects

sealed interface SectorJsonValue {
    data object NullValue : SectorJsonValue
    data class BooleanValue(val value: Boolean) : SectorJsonValue
    data class NumberValue(val raw: String) : SectorJsonValue
    data class StringValue(val value: String) : SectorJsonValue
    data class ArrayValue(val values: List<SectorJsonValue>) : SectorJsonValue
    data class ObjectValue(val fields: Map<String, SectorJsonValue>) : SectorJsonValue
}

object SectorJson {
    val Null: SectorJsonValue = SectorJsonValue.NullValue

    fun string(value: String): SectorJsonValue = SectorJsonValue.StringValue(value)

    fun nullableString(value: String?): SectorJsonValue =
        value?.let(::string) ?: SectorJsonValue.NullValue

    fun bool(value: Boolean): SectorJsonValue = SectorJsonValue.BooleanValue(value)

    fun number(value: Int): SectorJsonValue = SectorJsonValue.NumberValue(value.toString())

    fun number(value: Long): SectorJsonValue = SectorJsonValue.NumberValue(value.toString())

    fun number(value: Double): SectorJsonValue {
        require(value.isFinite()) { "JSON number must be finite" }
        return SectorJsonValue.NumberValue(value.toString())
    }

    fun nullableNumber(value: Int?): SectorJsonValue =
        value?.let(::number) ?: SectorJsonValue.NullValue

    fun nullableNumber(value: Long?): SectorJsonValue =
        value?.let(::number) ?: SectorJsonValue.NullValue

    fun nullableNumber(value: Double?): SectorJsonValue =
        value?.let(::number) ?: SectorJsonValue.NullValue

    fun array(values: List<SectorJsonValue>): SectorJsonValue =
        SectorJsonValue.ArrayValue(values)

    fun obj(vararg fields: Pair<String, SectorJsonValue>): SectorJsonValue =
        SectorJsonValue.ObjectValue(linkedMapOf(*fields))

    fun stringify(value: SectorJsonValue): String = buildString {
        appendValue(value)
    }

    fun parse(text: String): Result<SectorJsonValue> =
        runCatching { Parser(text).parse() }

    private fun StringBuilder.appendValue(value: SectorJsonValue) {
        when (value) {
            SectorJsonValue.NullValue -> append("null")
            is SectorJsonValue.BooleanValue -> append(value.value)
            is SectorJsonValue.NumberValue -> append(value.raw)
            is SectorJsonValue.StringValue -> {
                append('"')
                append(value.value.jsonEscaped())
                append('"')
            }
            is SectorJsonValue.ArrayValue -> {
                append('[')
                value.values.forEachIndexed { index, item ->
                    if (index > 0) append(',')
                    appendValue(item)
                }
                append(']')
            }
            is SectorJsonValue.ObjectValue -> {
                append('{')
                value.fields.entries.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    append('"')
                    append(entry.key.jsonEscaped())
                    append("\":")
                    appendValue(entry.value)
                }
                append('}')
            }
        }
    }

    private fun String.jsonEscaped(): String = buildString {
        for (char in this@jsonEscaped) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\t' -> append("\\t")
                '\n' -> append("\\n")
                '\u000C' -> append("\\f")
                '\r' -> append("\\r")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }

    private class Parser(
        private val text: String
    ) {
        private var index = 0

        fun parse(): SectorJsonValue {
            val value = parseValue()
            skipWhitespace()
            if (index != text.length) {
                throw IllegalArgumentException("Unexpected JSON content at $index")
            }
            return value
        }

        private fun parseValue(): SectorJsonValue {
            skipWhitespace()
            if (index >= text.length) {
                throw IllegalArgumentException("Unexpected end of JSON")
            }
            return when (text[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> SectorJsonValue.StringValue(parseString())
                't' -> {
                    expectLiteral("true")
                    SectorJsonValue.BooleanValue(true)
                }
                'f' -> {
                    expectLiteral("false")
                    SectorJsonValue.BooleanValue(false)
                }
                'n' -> {
                    expectLiteral("null")
                    SectorJsonValue.NullValue
                }
                '-', in '0'..'9' -> parseNumber()
                else -> throw IllegalArgumentException("Unexpected JSON token at $index")
            }
        }

        private fun parseObject(): SectorJsonValue.ObjectValue {
            expect('{')
            skipWhitespace()
            val fields = linkedMapOf<String, SectorJsonValue>()
            if (peek('}')) {
                expect('}')
                return SectorJsonValue.ObjectValue(fields)
            }
            while (true) {
                skipWhitespace()
                val name = parseString()
                skipWhitespace()
                expect(':')
                fields[name] = parseValue()
                skipWhitespace()
                when {
                    peek(',') -> {
                        expect(',')
                    }
                    peek('}') -> {
                        expect('}')
                        return SectorJsonValue.ObjectValue(fields)
                    }
                    else -> throw IllegalArgumentException("Expected object separator at $index")
                }
            }
        }

        private fun parseArray(): SectorJsonValue.ArrayValue {
            expect('[')
            skipWhitespace()
            val values = mutableListOf<SectorJsonValue>()
            if (peek(']')) {
                expect(']')
                return SectorJsonValue.ArrayValue(values)
            }
            while (true) {
                values += parseValue()
                skipWhitespace()
                when {
                    peek(',') -> {
                        expect(',')
                    }
                    peek(']') -> {
                        expect(']')
                        return SectorJsonValue.ArrayValue(values)
                    }
                    else -> throw IllegalArgumentException("Expected array separator at $index")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < text.length) {
                val char = text[index++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> result.append(parseEscapedChar())
                    else -> {
                        if (char.code < 0x20) {
                            throw IllegalArgumentException("Control character in JSON string at $index")
                        }
                        result.append(char)
                    }
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun parseEscapedChar(): Char {
            if (index >= text.length) {
                throw IllegalArgumentException("Unterminated JSON escape")
            }
            return when (val char = text[index++]) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) {
                        throw IllegalArgumentException("Invalid unicode escape")
                    }
                    val value = text.substring(index, index + 4).toIntOrNull(16)
                        ?: throw IllegalArgumentException("Invalid unicode escape")
                    index += 4
                    value.toChar()
                }
                else -> throw IllegalArgumentException("Invalid JSON escape \\$char")
            }
        }

        private fun parseNumber(): SectorJsonValue.NumberValue {
            val start = index
            if (peek('-')) index++
            if (peek('0')) {
                index++
            } else {
                readDigits()
            }
            if (peek('.')) {
                index++
                readDigits()
            }
            if (peek('e') || peek('E')) {
                index++
                if (peek('+') || peek('-')) index++
                readDigits()
            }
            val raw = text.substring(start, index)
            raw.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid JSON number at $start")
            return SectorJsonValue.NumberValue(raw)
        }

        private fun readDigits() {
            val start = index
            while (index < text.length && text[index] in '0'..'9') {
                index++
            }
            if (start == index) {
                throw IllegalArgumentException("Expected digit at $index")
            }
        }

        private fun expectLiteral(value: String) {
            if (!text.startsWith(value, index)) {
                throw IllegalArgumentException("Expected $value at $index")
            }
            index += value.length
        }

        private fun expect(char: Char) {
            if (!peek(char)) {
                throw IllegalArgumentException("Expected $char at $index")
            }
            index++
        }

        private fun peek(char: Char): Boolean =
            index < text.length && text[index] == char

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) {
                index++
            }
        }
    }
}

fun SectorJsonValue.asObjectOrNull(): Map<String, SectorJsonValue>? =
    (this as? SectorJsonValue.ObjectValue)?.fields

fun SectorJsonValue.asArrayOrNull(): List<SectorJsonValue>? =
    (this as? SectorJsonValue.ArrayValue)?.values

fun SectorJsonValue.asStringOrNull(): String? =
    (this as? SectorJsonValue.StringValue)?.value

fun SectorJsonValue.asLongOrNull(): Long? =
    (this as? SectorJsonValue.NumberValue)?.raw?.toLongOrNull()

fun SectorJsonValue.asDoubleOrNull(): Double? =
    (this as? SectorJsonValue.NumberValue)?.raw?.toDoubleOrNull()

fun SectorJsonValue.asIntOrNull(): Int? =
    asLongOrNull()?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

fun SectorJsonValue.asBooleanOrNull(): Boolean? =
    (this as? SectorJsonValue.BooleanValue)?.value

fun Map<String, SectorJsonValue>.requiredString(name: String): String =
    this[name]?.asStringOrNull() ?: throw IllegalArgumentException("Missing string field $name")

fun Map<String, SectorJsonValue>.optionalString(name: String): String? =
    this[name]?.takeUnless { it == SectorJsonValue.NullValue }?.asStringOrNull()

fun Map<String, SectorJsonValue>.requiredLong(name: String): Long =
    this[name]?.asLongOrNull() ?: throw IllegalArgumentException("Missing number field $name")

fun Map<String, SectorJsonValue>.optionalLong(name: String): Long? =
    this[name]?.takeUnless { it == SectorJsonValue.NullValue }?.asLongOrNull()

fun Map<String, SectorJsonValue>.requiredDouble(name: String): Double =
    this[name]?.asDoubleOrNull() ?: throw IllegalArgumentException("Missing number field $name")

fun Map<String, SectorJsonValue>.optionalDouble(name: String): Double? =
    this[name]?.takeUnless { it == SectorJsonValue.NullValue }?.asDoubleOrNull()

fun Map<String, SectorJsonValue>.optionalInt(name: String): Int? =
    this[name]?.takeUnless { it == SectorJsonValue.NullValue }?.asIntOrNull()

fun Map<String, SectorJsonValue>.optionalBoolean(name: String): Boolean? =
    this[name]?.takeUnless { it == SectorJsonValue.NullValue }?.asBooleanOrNull()
