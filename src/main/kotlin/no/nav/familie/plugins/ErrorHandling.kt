package no.nav.familie.plugins

import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.MDC

fun Application.errorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            try {
                setMdcValues(call)

                val responseStatus: HttpStatusCode =
                    when (cause) {
                        is ResponseException -> cause.response.status
                        // Catcher SerializationException/JsonDecodingException
                        is IllegalArgumentException -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.InternalServerError
                    }

                call.application.log.error(
                    "Feilet håndtering av ${call.request.httpMethod} - ${call.request.path()} status=$responseStatus",
                    cause,
                )
                call.respond(
                    responseStatus,
                    mapOf(
                        "status" to "FEILET",
                        "errorMelding" to cause.message,
                    ),
                )
            } finally {
                removeMdcValues()
            }
        }
    }
}

private fun removeMdcValues() {
    MDC.remove(MDCConstants.CALL_ID)
    MDC.remove(MDCConstants.CONSUMER_ID)
    MDC.remove(MDCConstants.USER_ID)
}

private fun setMdcValues(call: ApplicationCall) {
    MDC.put(MDCConstants.CALL_ID, call.callId)
    MDC.put(MDCConstants.CONSUMER_ID, call.request.header("Nav-Consumer-Id"))
    MDC.put(MDCConstants.USER_ID, call.request.cookies[RANDOM_USER_ID_COOKIE_NAME])
}
