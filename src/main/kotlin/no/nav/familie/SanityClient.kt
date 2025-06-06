import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.HttpConnectStrategy
import com.launchdarkly.eventsource.MessageEvent
import com.launchdarkly.eventsource.StreamClosedByServerException
import com.launchdarkly.eventsource.background.BackgroundEventHandler
import com.launchdarkly.eventsource.background.BackgroundEventSource
import com.launchdarkly.eventsource.background.ConnectionErrorHandler
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import no.nav.familie.EndringJson
import no.nav.familie.SlideImageDl
import no.nav.familie.SlideImageJson
import no.nav.familie.SubscribedApp
import okhttp3.internal.http2.StreamResetException
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.set

sealed class Result<out T, out E>

class Ok<out T>(val value: T) : Result<T, Nothing>()

class Err<out E>(val error: E) : Result<Nothing, E>()

class SanityClient(
    private val projId: String,
    apiVersion: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val baseUrl: String = "https://$projId.api.sanity.io/$apiVersion"
    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        ignoreUnknownKeys = true
                    },
                )
            }
        }

    private var subscribedApps: HashMap<String, SubscribedApp> = hashMapOf()

    fun query(
        queryString: String,
        dataset: String,
        withImages: Boolean,
    ): Result<EndringJson, ClientRequestException> {
        return when (withImages) {
            true -> tryCacheFirst(queryCache, queryString, dataset) { q, d -> querySanity(q, d) }
            false -> tryCacheFirst(queryCacheText, queryString, dataset) { q, d -> querySanityText(q, d) }
        }
    }

    fun fetchImage(
        slideImageRef: String,
        dataset: String,
    ): Result<SlideImageDl, ClientRequestException> {
        return try {
            val value = SlideImageDl(slideImage = imageObjToByteArray(slideImageRef, dataset))
            Ok(value)
        } catch (e: ClientRequestException) {
            Err(e)
        }
    }

    private val queryCache: Cache<String, EndringJson> =
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000).build()

    private val queryCacheText: Cache<String, EndringJson> =
        Caffeine.newBuilder().expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1000).build()

    @Throws(ClientRequestException::class)
    private fun querySanityText(
        queryString: String,
        dataset: String,
    ): EndringJson {
        val response: EndringJson
        runBlocking {
            response = client.get("$baseUrl/data/query/$dataset?query=$queryString").body()
        }
        val listenUrl = "$baseUrl/data/listen/$dataset?query=$queryString&includeResult=false&visibility=query"
        // call was successful. must then check if the response is empty. If empty -> don't subscribe
        if (response.result.isNotEmpty() and !subscribedApps.contains(listenUrl)) {
            subscribeToSanityApp(listenUrl, queryString, dataset)
        }
        return response
    }

    @Throws(ClientRequestException::class)
    private fun querySanity(
        queryString: String,
        dataset: String,
    ): EndringJson {
        val response: EndringJson
        runBlocking {
            response = client.get("$baseUrl/data/query/$dataset?query=$queryString").body()
        }
        val responseWithImage =
            EndringJson(
                response.result.map {
                    it.copy(
                        modal =
                            it.modal?.copy(
                                slides =
                                    it.modal.slides.map { s ->
                                        when (s.image) {
                                            is SlideImageJson ->
                                                s.copy(
                                                    image =
                                                        SlideImageDl(
                                                            imageObjToByteArray(
                                                                s.image.getReference(),
                                                                dataset,
                                                            ),
                                                        ),
                                                )

                                            else -> s
                                        }
                                    },
                            ),
                    )
                },
            )
        val listenUrl = "$baseUrl/data/listen/$dataset?query=$queryString&includeResult=false&visibility=query"
        // call was successful. must then check if the response is empty. If empty -> don't subscribe
        if (response.result.isNotEmpty() and !subscribedApps.contains(listenUrl)) {
            subscribeToSanityApp(listenUrl, queryString, dataset)
        }
        return responseWithImage
    }

    private fun <V> tryCacheFirst(
        cache: Cache<String, V>,
        query: String,
        dataset: String,
        valueSupplier: (queryString: String, datasetString: String) -> V,
    ): Result<V, ClientRequestException> {
        val key = "$query.$dataset"
        return try {
            val value = cache.get(key) { valueSupplier(query, dataset) }
            Ok(value)
        } catch (e: ClientRequestException) {
            Err(e)
        }
    }

    private fun <V : Any> updateCache(
        cache: Cache<String, V>,
        query: String,
        dataset: String,
        valueSupplier: (queryString: String, datasetString: String) -> V,
    ) {
        val key = "$query.$dataset"
        val newValue = valueSupplier(query, dataset)
        cache.put(key, newValue)
    }

    private fun subscribeToSanityApp(
        listenUrl: String,
        queryString: String,
        dataset: String,
    ) {
        val eventHandler = MessageEventHandler(listenUrl)
        val eventSource: BackgroundEventSource =
            BackgroundEventSource.Builder(
                eventHandler,
                EventSource.Builder(HttpConnectStrategy.http(URI.create(listenUrl)).readTimeout(55, TimeUnit.MINUTES)),
            )
                .connectionErrorHandler(SanityConnectionErrorHandler())
                .build()

        eventSource.start()
        if (!subscribedApps.containsKey(listenUrl)) {
            subscribedApps[listenUrl] =
                SubscribedApp(listenUrl, queryString, dataset, "$queryString.$dataset", eventSource)
        }

        // Schedule task to ensure that connection has been established. If not, remove data from cache
        Executors.newSingleThreadScheduledExecutor().schedule({
            if (!subscribedApps[listenUrl]?.connectionEstablished!!) {
                logger.warn("Connection to $listenUrl not established.")
                resetSubscriptionAndCache(listenUrl)
            }
        }, 20, TimeUnit.SECONDS)
    }

    private fun resetSubscriptionAndCache(listenUrl: String) {
        logger.info("Nullstiller mot $listenUrl.")
        val cachekey = subscribedApps[listenUrl]?.cacheKey
        subscribedApps[listenUrl]?.connectionEstablished = false
        subscribedApps[listenUrl]?.eventSource?.close()
        subscribedApps.remove(listenUrl)
        if (cachekey != null) {
            queryCache.asMap().remove(cachekey)
            queryCacheText.asMap().remove(cachekey)
        }
    }

    // calculates milliseconds from now until next given weekday with hourly offset in UTC time
    private fun msToNextDay(
        dayOfWeek: DayOfWeek,
        hourOffset: Long,
    ): Long {
        val nextDay =
            LocalDate.now(Clock.systemUTC())
                .with(TemporalAdjusters.nextOrSame(dayOfWeek))
                .atStartOfDay()
                .plusHours(hourOffset)
        val duration = Duration.between(LocalDateTime.now(Clock.systemUTC()), nextDay).toMillis()
        return if (duration < 0) {
            duration + TimeUnit.DAYS.toMillis(7) // add one week if calculated duration is negative
        } else {
            duration
        }
    }

    // Class to handle events from EventHandler
    private inner class MessageEventHandler(val listenUrl: String) : BackgroundEventHandler {
        @Throws(Exception::class)
        override fun onOpen() {
            logger.info("Åpner stream mot Sanity")
        }

        @Throws(Exception::class)
        override fun onClosed() {
            resetSubscriptionAndCache(listenUrl)
            logger.info("Lukker stream mot Sanity")
        }

        // Handles events from Sanity listen API
        override fun onMessage(
            event: String,
            messageEvent: MessageEvent,
        ) {
            logger.info("Mottar melding")
            val origin = messageEvent.origin.toString()
            when (event) {
                "welcome" -> { // connection is established
                    // cancels subscription, and clears cache every Saturday morning 01.00 UTC time
                    if (!subscribedApps[origin]!!.connectionEstablished) {
                        Executors.newSingleThreadScheduledExecutor().schedule({
                            resetSubscriptionAndCache(origin)
                            logger.info("Unsubscribed from listening API: $origin")
                        }, msToNextDay(DayOfWeek.SATURDAY, 1), TimeUnit.MILLISECONDS)
                    }
                    subscribedApps[origin]?.connectionEstablished = true
                    logger.info("Subscribing to listening API: $origin")
                }

                "mutation" -> { // a change is discovered in Sanity -> update cache
                    logger.info("Mutation in $origin discovered, updating cache.")
                    updateCache(
                        queryCache,
                        subscribedApps[origin]!!.queryString,
                        subscribedApps[origin]!!.dataset,
                    ) { q, p -> querySanity(q, p) }
                    updateCache(
                        queryCacheText,
                        subscribedApps[origin]!!.queryString,
                        subscribedApps[origin]!!.dataset,
                    ) { q, p -> querySanityText(q, p) }
                }

                "disconnect" -> { // client should disconnect and stay disconnected. Likely due to a query error
                    logger.info("Listening API for $origin requested disconnection with error message: ${messageEvent.data}")
                    subscribedApps[origin]?.connectionEstablished = false
                    subscribedApps[origin]?.eventSource?.close()
                    queryCache.asMap().remove(subscribedApps[origin]?.cacheKey)
                    queryCacheText.asMap().remove(subscribedApps[origin]?.cacheKey)
                    subscribedApps.remove(origin)
                }
            }
        }

        override fun onError(t: Throwable) {
            when (t) {
                is StreamResetException -> logger.info("Stream mot Sanity ble resatt", t)
                is StreamClosedByServerException -> logger.info("Connection mot sanity ble avbrutt", t)
                else ->
                    when (t.cause) {
                        is StreamResetException -> logger.info("Stream mot Sanity ble resatt", t)
                        else -> logger.error("En feil oppstod", t)
                    }
            }
        }

        override fun onComment(comment: String) {
            logger.debug("Holder stream mot Sanity i gang")
        }
    }

    // Shuts down connection when connection attempt fails
    private inner class SanityConnectionErrorHandler : ConnectionErrorHandler {
        override fun onConnectionError(t: Throwable?): ConnectionErrorHandler.Action {
            logger.info("ConnectionError mot Sanity", t)
            return if (t is StreamResetException) { // to handle stream resets every 30 minutes
                ConnectionErrorHandler.Action.PROCEED
            } else {
                ConnectionErrorHandler.Action.SHUTDOWN
            }
        }
    }

    private fun imageObjToByteArray(
        refJson: String,
        dataset: String,
    ): ByteArray {
        val ref = refJson.replace("(-([A-Za-z]+))\$".toRegex(), ".\$2").drop(6)
        val url = "https://cdn.sanity.io/images/$projId/$dataset/$ref"
        val httpResp: HttpResponse
        val byteArr: ByteArray
        runBlocking {
            httpResp = client.get(url)
            byteArr = httpResp.body()
        }

        return byteArr
    }
}
