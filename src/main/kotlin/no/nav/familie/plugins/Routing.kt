package no.nav.familie.plugins

import Err
import Ok
import SanityClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.util.encodeBase64
import io.ktor.util.pipeline.PipelineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import no.nav.familie.BildeData
import no.nav.familie.BrukerData
import no.nav.familie.DocumentId
import no.nav.familie.SeenForcedStatus
import no.nav.familie.SeenStatus
import no.nav.familie.SessionDuration
import no.nav.familie.env.erIDev
import no.nav.familie.getAllEntriesInSeen
import no.nav.familie.getAllEntriesInUserSessions
import no.nav.familie.getSeenEntriesForAppId
import no.nav.familie.getSeenEntriesForDocId
import no.nav.familie.getSeenEntriesForUser
import no.nav.familie.getSeenForcedEntriesForUser
import no.nav.familie.getUniqueVisitorsPerDayForAppId
import no.nav.familie.insertSeenEntries
import no.nav.familie.insertSeenForcedEntries
import no.nav.familie.insertSessionDuration
import no.nav.familie.setLinkClicked
import no.nav.familie.setModalOpen
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.util.UUID

val logger: Logger = LoggerFactory.getLogger("no.nav.familie.routing")

fun Application.configureRouting(client: SanityClient) {
    routing {
        post("/endringslogg") { // TODO: Deprecated
            fetchEndringslogger(client, withImages = true)
        }
        post("/endringslogg/text") {
            fetchEndringslogger(client, withImages = false)
        }
        post("/endringslogg/image") {
            fetchImage(client)
        }
        post("/analytics/sett-endringer") {
            val seen = call.receive<SeenStatus>()
            insertSeenEntries(seen.userId, seen.appId, seen.documentIds.map(UUID::fromString))
            call.respond(HttpStatusCode.OK)
        }

        post("/analytics/seen-forced-modal") {
            val seen = call.receive<SeenForcedStatus>()
            insertSeenForcedEntries(seen.userId, seen.documentIds.map(UUID::fromString))
            call.respond(HttpStatusCode.OK)
        }

        post("/analytics/session-duration") {
            val duration = call.receive<SessionDuration>()
            insertSessionDuration(duration)
            call.respond(HttpStatusCode.OK)
        }
        patch("/analytics/modal-open") {
            val id = call.receive<DocumentId>()
            setModalOpen(id.documentId)
            call.respond(HttpStatusCode.OK)
        }
        patch("/analytics/link-click") {
            val id = call.receive<DocumentId>()
            setLinkClicked(id.documentId)
            call.respond(HttpStatusCode.OK)
        }

        get("/data/seen-all") {
            call.respond(getAllEntriesInSeen())
        }
        get("/data/seen-app") {
            call.request.queryParameters["appId"]?.let {
                call.respond(getSeenEntriesForAppId(it))
            } ?: call.respond(HttpStatusCode.BadRequest)
        }

        get("/data/seen") {
            call.request.queryParameters["docId"]?.let {
                call.respond(getSeenEntriesForDocId(it))
            } ?: call.respond(HttpStatusCode.BadRequest)
        }

        get("data/user-session-all") {
            call.request.queryParameters["appId"]?.let {
                call.respond(getAllEntriesInUserSessions(it))
            } ?: call.respond(HttpStatusCode.BadRequest)
        }

        get("data/unique-user-sessions-per-day") {
            call.request.queryParameters["appId"]?.let { appId ->
                call.request.queryParameters["moreThanMs"]?.let { moreThan ->
                    call.request.queryParameters["lessThanMs"]?.let { lessThan ->
                        call.respond(getUniqueVisitorsPerDayForAppId(appId, moreThan.toInt(), lessThan.toInt()))
                    } ?: call.respond(HttpStatusCode.BadRequest)
                } ?: call.respond(HttpStatusCode.BadRequest)
            } ?: call.respond(HttpStatusCode.BadRequest)
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.fetchImage(client: SanityClient) {
    val (slideImageRef, dataSet) = call.receive<BildeData>()

    when (val result = client.fetchImage(slideImageRef, dataSet)) {
        is Ok -> call.respond(mapOf("slideImage" to Json.encodeToJsonElement(result.value.slideImage.encodeBase64())))

        is Err -> {
            val statuskode = result.error.response.status.value
            val feilmelding = result.error.message
            logger.info("Feil ved uthenting av bilde: $statuskode - $feilmelding")
            call.response.status(HttpStatusCode(statuskode, "Feilmelding: $feilmelding"))
        }

        else -> {
            logger.error("Noe uventet skjedde ved uthenting av bilde")
            call.response.status(HttpStatusCode(500, "Uventet feil ved uthenting av bilde"))
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.fetchEndringslogger(
    client: SanityClient,
    withImages: Boolean,
) {
    val (userId, appId, dataset, maxEntries) = call.receive<BrukerData>()
    val seenEntryIds = getSeenEntriesForUser(userId).map(UUID::toString).toSet()
    val seenForcedEntryIds = getSeenForcedEntriesForUser(userId).map(UUID::toString).toSet()

    val alleMeldingerQuery = "*[_type=='$appId']|order(_createdAt desc)[0...$maxEntries]"
    val publiserteMedlingerQuery = "*[_type=='$appId']|[publisert]|order(_createdAt desc)[0...$maxEntries]"

    val query = if (erIDev()) alleMeldingerQuery else publiserteMedlingerQuery

    val queryStringEncoded = URLEncoder.encode(query, "utf-8")
    when (val endringslogger = client.query(queryStringEncoded, dataset, withImages)) {
        is Ok -> {
            if (endringslogger.value.result.isEmpty()) {
                call.response.status(HttpStatusCode(204, "Data for app $appId doesn't exist."))
            } else {
                call.respond(
                    endringslogger.value.result.map {
                        it.copy(
                            seen = it.id in seenEntryIds,
                            seenForced = it.id in seenForcedEntryIds,
                            forcedModal = it.modal?.forcedModal,
                        )
                    },
                )
            }
        }

        is Err -> {
            val statuskode = endringslogger.error.response.status.value
            val feilmelding = endringslogger.error.message
            logger.info("Feil ved uthenting av endringslogg: $statuskode - $feilmelding")
            call.response.status(HttpStatusCode(statuskode, "Feilmelding: $feilmelding"))
        }

        else -> {
            logger.error("Noe uventet skjedde ved uthenting av endringslogg")
            call.response.status(HttpStatusCode(500, "Uventet feil ved uthenting av endringslogg"))
        }
    }
}
