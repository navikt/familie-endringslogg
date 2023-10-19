
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import no.nav.familie.EndringJson
import no.nav.familie.SlideImageDl
import no.nav.familie.SlideImageJson
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

sealed class Result<out T, out E>
class Ok<out T>(val value: T) : Result<T, Nothing>()
class Err<out E>(val error: E) : Result<Nothing, E>()

class SanityClient(
    private val projId: String,
    apiVersion: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val baseUrl: String = "https://$projId.api.sanity.io/$apiVersion"
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    private fun imageObjToByteArray(obj: JsonObject, dataset: String): ByteArray {
        val refJson: String = obj["asset"]!!.jsonObject["_ref"].toString().replace("\"", "")
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

    fun query(queryString: String, dataset: String): Result<EndringJson, ClientRequestException> {
        return tryCacheFirst(queryCache, queryString, dataset) { q, d -> querySanity(q, d) }
    }

    private val queryCache: Cache<String, EndringJson> = Caffeine.newBuilder()
        .expireAfterWrite(30, TimeUnit.MINUTES)
        .maximumSize(1000)
        .build()

    @Throws(ClientRequestException::class)
    private fun querySanity(queryString: String, dataset: String): EndringJson {
        logger.info("Henter endringslogg for $queryString i $dataset")
        val response: EndringJson
        runBlocking {
            response = client.get("$baseUrl/data/query/$dataset?query=$queryString").body()
        }
        val responseWithImage = EndringJson(
            response.result.map {
                it.copy(
                    modal = it.modal?.copy(
                        slides = it.modal.slides.map { s ->
                            when (s.image) {
                                is SlideImageJson ->
                                    s.copy(
                                        image = SlideImageDl(
                                            imageObjToByteArray(
                                                s.image.slideImage.jsonObject,
                                                dataset
                                            )
                                        )
                                    )
                                else -> s
                            }
                        }
                    )
                )
            }
        )
        return responseWithImage
    }

    private fun <V> tryCacheFirst(
        cache: Cache<String, V>,
        query: String,
        dataset: String,
        valueSupplier: (queryString: String, datasetString: String) -> V
    ): Result<V, ClientRequestException> {
        val key = "$query.$dataset"
        return try {
            val value = cache.get(key) { valueSupplier(query, dataset) }
            Ok(value)
        } catch (e: ClientRequestException) {
            Err(e)
        }
    }
}
