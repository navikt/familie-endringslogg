package no.nav.familie.env

val DB_USERNAME: String = System.getenv("db_USERNAME")
val DB_PASSWORD: String = System.getenv("db_PASSWORD")
val DB_HOST: String = System.getenv("db_HOST")
val DB_DATABASE: String = System.getenv("db_DATABASE")
val DB_PORT: Int = System.getenv("db_PORT").toInt()
const val SANITY_PROJECT_ID: String = "avzz8jwq"

fun erIDev() = System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp"

fun erIProd() = System.getenv("NAIS_CLUSTER_NAME") == "prod-gcp"
