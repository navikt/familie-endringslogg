package no.nav.familie.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.header
import java.util.UUID

private const val RANDOM_USER_ID_COOKIE_NAME = "RUIDC"

object MDCConstants {
    const val CONSUMER_ID = "consumerId"
    const val USER_ID = "userId"
    const val CALL_ID = "callId"
}

fun Application.configureLogging() {
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
    }
}

private val NAV_CALL_ID_HEADER_NAMES =
    setOf(
        "Nav-Call-Id",
        "Nav-CallId",
        "Nav-Callid",
        "X-Correlation-Id"
    )

private fun resolveCallId(request: ApplicationRequest): String {
    return NAV_CALL_ID_HEADER_NAMES
        .asSequence()
        .mapNotNull { request.header(it) }
        .firstOrNull { it.isNotEmpty() }
        ?: UUID.randomUUID().toString()
}
