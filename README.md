# Familie-Endringslogg

En applikasjon som henter endringslogg-innhold fra Sanity og fungerer som en proxy for frontend-applikasjoner.
For å holde orden på hvilke endringer saksbehandlere har lest om allerede lagres dette i en database.

Går mot https://familie-endringslogg.sanity.studio/

## Tilgang til tjenesten
For at en frontend-applikasjon skal få tilgang må urlen den kjører på legges inn i CORS-filteret

## Lokalt
For å bygge
`./gradlew build`

For å kjøre lokalt:
- `docker-compose up`
- Legg inn miljøvariablene fra `Env.kt` og `docker-compose.yaml` i IntelliJ og kjør Application 

# Henvendelser

Prosjektet er laget med utgangspunkt i `poao-endringslogg`

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-familie.
