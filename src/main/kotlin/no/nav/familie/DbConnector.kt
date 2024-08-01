package no.nav.familie

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.familie.env.DB_DATABASE
import no.nav.familie.env.DB_HOST
import no.nav.familie.env.DB_PASSWORD
import no.nav.familie.env.DB_PORT
import no.nav.familie.env.DB_USERNAME
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

fun sha256(userId: String): String {
    val bytes = userId.toByteArray()
    val md = MessageDigest.getInstance("SHA-256")
    val digest = md.digest(bytes)
    return digest.fold("", { str, it -> str + "%02x".format(it) })
}

object Seen : Table("seen") {
    val userId = varchar("user_id", 255)
    val documentId = uuid("document_id")
    val appId = varchar("app_id", 255)
    val openedLink = bool("opened_link").default(false)
    val openedModal = bool("opened_modal").default(false)
    val timeStamp = timestamp("time_stamp")
    override val primaryKey = PrimaryKey(userId, documentId)
}

object SeenForced : Table("seen_forced") {
    val userId = varchar("user_id", 255)
    val documentId = uuid("document_id")
    override val primaryKey = PrimaryKey(userId, documentId)
}

object UserSession : Table("user_session") {
    val userId = varchar("user_id", 255)
    val appId = varchar("app_id", 255)
    val duration = integer("duration")
    val unseenFields = integer("unseen_fields")
    val timeStamp = timestamp("time_stamp")
    override val primaryKey = PrimaryKey(userId, timeStamp)
}

// https://ktor.io/docs/connection-pooling-caching.html#connection-settings-config
private fun createHikariDataSource(url: String) =
    HikariDataSource(
        HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = url
            username = DB_USERNAME
            password = DB_PASSWORD
            maximumPoolSize = 5
            maxLifetime = 900_000
            // isAutoCommit and transactionIsolation are set to sync with the default settings used by Exposed
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        },
    )

fun connectToDatabase() {
    val connectUrl: String = "jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_DATABASE?reWriteBatchedInserts=true?sslmode=require"

    Database.connect(createHikariDataSource(connectUrl))
}

fun getSeenEntriesForUser(userId: String): List<UUID> =
    transaction {
        Seen.selectAll().where { Seen.userId eq sha256(userId) }.map { it[Seen.documentId] }
    }

fun getSeenForcedEntriesForUser(userId: String): List<UUID> =
    transaction {
        SeenForced.selectAll().where { SeenForced.userId eq sha256(userId) }.map { it[SeenForced.documentId] }
    }

fun insertSeenEntries(
    userId: String,
    appId: String,
    documentIds: List<UUID>,
) = transaction {
    Seen.batchInsert(documentIds, ignore = true) { docId ->
        this[Seen.userId] = sha256(userId)
        this[Seen.appId] = appId
        this[Seen.documentId] = docId
        this[Seen.timeStamp] = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    }
}

fun insertSeenForcedEntries(
    userId: String,
    documentIds: List<UUID>,
) = transaction {
    SeenForced.batchInsert(documentIds, ignore = true) { docId ->
        this[SeenForced.userId] = sha256(userId)
        this[SeenForced.documentId] = docId
    }
}

fun setModalOpen(docId: String) =
    transaction {
        Seen.update({ Seen.documentId eq UUID.fromString(docId) }) {
            it[Seen.openedModal] = true
        }
    }

fun setLinkClicked(docId: String) =
    transaction {
        Seen.update({ Seen.documentId eq UUID.fromString(docId) }) {
            it[Seen.openedLink] = true
        }
    }

fun insertSessionDuration(session: SessionDuration) =
    transaction {
        UserSession.insert {
            it[UserSession.userId] = sha256(session.userId)
            it[UserSession.appId] = session.appId
            it[UserSession.duration] = session.duration
            it[UserSession.unseenFields] = session.unseenFields
            it[UserSession.timeStamp] = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        }
    }
