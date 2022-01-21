package no.nav.familie

import SanityClient
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.json.Json
import no.nav.familie.env.DB_DATABASE
import no.nav.familie.env.DB_HOST
import no.nav.familie.env.DB_PASSWORD
import no.nav.familie.env.DB_PORT
import no.nav.familie.env.DB_USERNAME
import no.nav.familie.env.SANITY_PROJECT_ID
import no.nav.familie.plugins.configureRouting
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("no.nav.familie.Application")

fun Application.main() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            encodeDefaults = true
        })
    }
    install(CORS) {
        logger.info("Setter opp cors")
        host("ensligmorellerfar.dev.intern.nav.no", listOf("https"))
        host("ensligmorellerfar.intern.nav.no", listOf("https"))
        host("barnetrygd.dev.intern.nav.no", listOf("https"))
        host("barnetrygd.intern.nav.no", listOf("https"))
        host("familie-endringslogg.sanity.studio", listOf("https"))
        host("localhost:8000", listOf("http"))

        method(HttpMethod.Options)
        method(HttpMethod.Get)
        method(HttpMethod.Post)
        method(HttpMethod.Patch)
        header(HttpHeaders.ContentType)
    }
}

fun main() {
    logger.info("Kjører flyway")
    val flyway: Flyway = Flyway.configure().dataSource(
        "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_DATABASE?reWriteBatchedInserts=true?sslmode=require",
        DB_USERNAME,
        DB_PASSWORD
    ).load()
    flyway.migrate()

    val client = SanityClient(SANITY_PROJECT_ID, "v1")

    connectToDatabase()

    embeddedServer(Netty, environment = applicationEngineEnvironment {
        module {
            main()
            configureRouting(client)
        }
        connector {
            port = 8080
            host = "0.0.0.0"
        }
    }) {
    }.start(wait = true)
}
