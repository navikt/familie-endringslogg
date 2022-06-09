package no.nav.familie.plugins

import Err
import Ok
import SanityClient
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.patch
import io.ktor.routing.post
import io.ktor.routing.routing
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
        post("/endringslogg") {
            logger.info("Henter ut endringslogg")
            val (userId, appId, dataset, maxEntries) = call.receive<BrukerData>()
            val seenEntryIds = getSeenEntriesForUser(userId).map(UUID::toString).toSet()
            val seenForcedEntryIds = getSeenForcedEntriesForUser(userId).map(UUID::toString).toSet()

            val alleMeldingerQuery = "*[_type=='$appId'][0...$maxEntries]"
            val publiserteMedlingerQuery = "*[_type=='$appId'][0...$maxEntries][publisert]"

            val query = if (erIDev()) alleMeldingerQuery else publiserteMedlingerQuery

            val queryStringEncoded = URLEncoder.encode(query, "utf-8")
            when (val endringslogger = client.query(queryStringEncoded, dataset)) {
                is Ok -> {
                    if (endringslogger.value.result.isEmpty()) {
                        call.response.status(HttpStatusCode(204, "Data for app $appId doesn't exist."))
                    } else {
                        call.respond(
                            endringslogger.value.result.map {
                                it.copy(
                                    seen = it.id in seenEntryIds,
                                    seenForced = it.id in seenForcedEntryIds,
                                    forcedModal = it.modal?.forcedModal
                                )
                            }
                        )
                    }
                }
                is Err -> {
                    logger.info("Got a client request exception with error code ${endringslogger.error.response.status.value} and message ${endringslogger.error.message}")
                    call.response.status(
                        HttpStatusCode(
                            endringslogger.error.response.status.value,
                            "Received error: ${endringslogger.error.message}"
                        )
                    )
                }
            }
        }
        post("/analytics/sett-endringer") {
            val seen = call.receive<SeenStatus>()
            insertSeenEntries(seen.userId, seen.appId, seen.documentIds.map(UUID::fromString))
            call.respond(HttpStatusCode.OK) // TODO: Return status for insert
        }

        post("/analytics/seen-forced-modal") {
            val seen = call.receive<SeenForcedStatus>()
            insertSeenForcedEntries(seen.userId, seen.documentIds.map(UUID::fromString))
            call.respond(HttpStatusCode.OK) // TODO: Return status for insert
        }

        post("/analytics/session-duration") {
            val duration = call.receive<SessionDuration>()
            insertSessionDuration(duration)
            call.respond(HttpStatusCode.OK) // TODO: Return status for insert
        }
        patch("/analytics/modal-open") {
            val id = call.receive<DocumentId>()
            setModalOpen(id.documentId)
            call.respond(HttpStatusCode.OK) // TODO: Return status for insert
        }
        patch("/analytics/link-click") {
            val id = call.receive<DocumentId>()
            setLinkClicked(id.documentId)
            call.respond(HttpStatusCode.OK) // TODO: Return status for insert
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
