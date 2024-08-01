package no.nav.familie

import ModalSerializer
import SlideSerializer
import com.launchdarkly.eventsource.background.BackgroundEventSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import no.nav.familie.env.SANITY_PROJECT_ID

@Serializable
data class EndringJson(val result: List<Endring>)

@Serializable
data class Endring(
    val title: String,
    val description: BlockContent,
    val date: String? = null,
    val linkAttributes: LinkAttributes? = null,
    @SerialName("_id") val id: String,
    val seen: Boolean = false,
    val seenForced: Boolean = false,
    val forcedModal: Boolean? = false,
    val modal: Modal? = null,
    val projectId: String = SANITY_PROJECT_ID,
    val apiHost: String = "https://cdn.sanity.io",
)

@Serializable
data class LinkAttributes(val link: String? = null, val linkText: String? = null)

typealias BlockContent = JsonElement

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable(with = ModalSerializer::class)
data class Modal(val title: String, val forcedModal: Boolean, val slides: List<Slide>)

@Serializable sealed class SlideImage {
    abstract val type: String
}

@Serializable class SlideImageJson(val slideImage: JsonElement, override val type: String = "json") : SlideImage() {
    fun getReference() = this.slideImage.jsonObject["asset"]!!.jsonObject["_ref"].toString().replace("\"", "")
}

@Serializable class SlideImageDl(val slideImage: ByteArray, override val type: String = "dl") : SlideImage()

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable(with = SlideSerializer::class)
data class Slide(
    @SerialName("slideHeader") val header: String,
    @SerialName("slideDescription") val description: JsonArray? = null,
    @SerialName("slideImage") val image: SlideImage?,
    @SerialName("altText") val altText: String? = null,
)

@Serializable
data class BrukerData(val userId: String, val appId: String, val dataset: String, val maxEntries: Int)

@Serializable
data class BildeData(val slideImageRef: String, val dataset: String)

@Serializable
data class SeenStatus(
    val userId: String,
    val appId: String,
    val documentIds: List<String>,
)

@Serializable
data class SeenForcedStatus(
    val userId: String,
    val documentIds: List<String>,
)

@Serializable
data class SessionDuration(
    val userId: String,
    val appId: String,
    val duration: Int,
    val unseenFields: Int,
)

@Serializable
data class SeenWithTime(
    val userId: String,
    val documentId: String,
    val timeStamp: String,
)

@Serializable
data class SeenDataClass(
    val userId: String,
    val documentId: String,
    val openedLink: Boolean,
    val openedModal: Boolean,
    val timeStamp: String,
)

@Serializable
data class UserSessionClass(
    val userId: String,
    val appId: String,
    val duration: Int,
    val unseenFields: Int,
    val timeStamp: String,
)

@Serializable
data class UniqueSessionsPerDay(
    val date: String,
    val users: Long,
)

@Serializable
data class DocumentId(
    val documentId: String,
)

@Serializable
data class UniqueUsersPerDay(
    val appId: String,
    val moreThanMs: String,
    val lessThanMs: String,
)

data class SubscribedApp(
    val listenURL: String,
    val queryString: String,
    val dataset: String,
    val cacheKey: String,
    val eventSource: BackgroundEventSource,
    var connectionEstablished: Boolean = false,
)
