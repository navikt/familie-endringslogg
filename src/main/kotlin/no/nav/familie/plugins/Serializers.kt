import io.ktor.util.InternalAPI
import io.ktor.util.encodeBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.nav.familie.Modal
import no.nav.familie.Slide
import no.nav.familie.SlideImage
import no.nav.familie.SlideImageDl
import no.nav.familie.SlideImageJson
import java.util.UUID

@ExperimentalSerializationApi
@Serializer(forClass = UUID::class)
object UUIDSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: UUID,
    ) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UUID {
        return UUID.fromString(decoder.decodeString())
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = Modal::class)
object ModalSerializer : KSerializer<Modal?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Modal") {
            element<String>("slideHeader")
            element<List<Slide>>("slides")
        }

    override fun serialize(
        encoder: Encoder,
        value: Modal?,
    ) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "header" to JsonPrimitive(value.title),
                    "slides" to JsonArray(value.slides.map(Json::encodeToJsonElement)),
                ),
            ),
        )
    }

    override fun deserialize(decoder: Decoder): Modal? {
        val modal = decoder.decodeSerializableValue(JsonObject.serializer())
        val numSlides = modal["numSlides"]?.jsonPrimitive?.int ?: return null
        if (numSlides < 1) return null
        val header = modal["modalHeader"]?.jsonPrimitive?.content ?: "Ny oppdatering"
        val forcedModal = modal["forcedModal"]?.jsonPrimitive?.boolean ?: false
        val slides =
            (1..numSlides).map { "modalSlide$it" }.mapNotNull { modal[it]?.jsonObject }.map {
                Json.decodeFromJsonElement(Slide.serializer(), it)
            }
        return Modal(header, forcedModal, slides)
    }
}

@ExperimentalSerializationApi
@Serializer(forClass = Slide::class)
object SlideSerializer : KSerializer<Slide?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Slide") {
            element<String>("slideHeader")
            element<JsonArray?>("slideDescription")
            element<SlideImage?>("slideImage")
            element<String?>("altText")
        }

    @OptIn(InternalAPI::class)
    override fun serialize(
        encoder: Encoder,
        value: Slide?,
    ) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            JsonObject(
                listOfNotNull(
                    "slideHeader" to JsonPrimitive(value.header),
                    if (value.description != null) "slideDescription" to value.description else null,
                    when (value.image) {
                        is SlideImageDl -> "slideImage" to Json.encodeToJsonElement(value.image.slideImage.encodeBase64())
                        is SlideImageJson -> "slideImageRef" to JsonPrimitive(value.image.getReference())
                        else -> null
                    },
                    if (value.altText != null) "altText" to JsonPrimitive(value.altText) else null,
                ).toMap(),
            ),
        )
    }

    override fun deserialize(decoder: Decoder): Slide? {
        val slide = decoder.decodeSerializableValue(JsonObject.serializer())
        val header: String = slide["slideHeader"]?.jsonPrimitive?.content ?: ""
        val description = slide["slideDescription"]?.jsonArray
        val image = if (slide["slideImage"] != null) SlideImageJson(slide["slideImage"]!!) else null
        val altText = slide["altText"]?.jsonPrimitive?.content

        return Slide(header, description, image, altText)
    }
}
