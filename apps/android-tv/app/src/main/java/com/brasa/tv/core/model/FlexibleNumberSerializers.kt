@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.brasa.tv.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Local and external metadata may represent an absent number as an empty
 * string, or a real number as a numeric string. These serializers prevent one
 * incomplete title from making the complete TV catalog unreadable.
 */
object FlexibleNullableDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleNullableDouble", PrimitiveKind.DOUBLE).nullable

    override fun deserialize(decoder: Decoder): Double? = decoder.flexibleNumber()?.toDouble()

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
}

object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)
    override fun deserialize(decoder: Decoder): Double = decoder.flexibleNumber()?.toDouble() ?: 0.0
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
}

object FlexibleNullableIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleNullableInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? = decoder.flexibleNumber()?.toInt()

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
}

object FlexibleIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int = decoder.flexibleNumber()?.toInt() ?: 0
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

object FlexibleNullableLongSerializer : KSerializer<Long?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleNullableLong", PrimitiveKind.LONG).nullable

    override fun deserialize(decoder: Decoder): Long? = decoder.flexibleNumber()?.toLong()

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) encoder.encodeNull() else encoder.encodeLong(value)
    }
}

object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)
    override fun deserialize(decoder: Decoder): Long = decoder.flexibleNumber()?.toLong() ?: 0L
    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}

private fun Decoder.flexibleNumber(): Number? {
    if (this !is JsonDecoder) return runCatching { decodeDouble() }.getOrNull()
    val element = decodeJsonElement()
    if (element is JsonNull || element !is JsonPrimitive) return null
    val normalized = element.content.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
}
