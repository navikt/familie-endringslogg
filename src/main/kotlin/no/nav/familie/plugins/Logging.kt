package no.nav.familie.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.callloging.processingTimeMillis
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import java.util.UUID

const val RANDOM_USER_ID_COOKIE_NAME = "RUIDC"

object MDCConstants {
    const val CONSUMER_ID = "consumerId"
    const val USER_ID = "userId"
    const val CALL_ID = "callId"
}

fun Application.configureLogging() {
    install(CallId) {
        retrieve { resolveCallId(it.request) }
        replyToHeader("Nav-Call-Id")
    }

    install(CallLogging) {
        disableDefaultColors()
        mdc(MDCConstants.CONSUMER_ID) { call ->
            call.request.header("Nav-Consumer-Id")
        }
        mdc(MDCConstants.USER_ID) {
            it.request.cookies[RANDOM_USER_ID_COOKIE_NAME]
        }
        mdc(MDCConstants.CALL_ID) { call ->
            resolveCallId(call.request)
        }

        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val path = call.request.path()
            val duration = call.processingTimeMillis()
            "$httpMethod - $path - (${status?.value}). Dette tok $duration"
        }
    }
}

private val NAV_CALL_ID_HEADER_NAMES =
    setOf(
        "Nav-Call-Id",
        "Nav-CallId",
        "Nav-Callid",
        "X-Correlation-Id",
    )

private fun resolveCallId(request: ApplicationRequest): String {
    return NAV_CALL_ID_HEADER_NAMES
        .asSequence()
        .mapNotNull { request.header(it) }
        .firstOrNull { it.isNotEmpty() }
        ?: UUID.randomUUID().toString()
}
