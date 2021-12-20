package no.nav.familie.env

val DB_USERNAME: String = System.getenv("db_USERNAME")
val DB_PASSWORD: String = System.getenv("db_PASSWORD")
val DB_HOST: String = System.getenv("db_HOST")
val DB_DATABASE: String = System.getenv("db_DATABASE")
val DB_PORT: Int = System.getenv("db_PORT").toInt()
val CORS_ALLOWED_HOST: String = System.getenv("CORS_ALLOWED_HOST")
val BACKEND_PORT: Int = System.getenv("BACKEND_PORT").toInt()
val SANITY_PROJECT_ID: String = "tralalallaa" // TODO: Sett riktig sanity id