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
- Legg inn disse miljøvariablene før du kjører Application:
- `db_USERNAME=postgres;db_PASSWORD=test;db_HOST=localhost;db_PORT=9876;db_DATABASE=familie-endringslogg;NAIS_CLUSTER_NAME=dev-gcp`

# Henvendelser

Prosjektet er laget med utgangspunkt i `poao-endringslogg`

# Ny saksbehandlingsløsning?
For at nye saksbehandlingsløsninger skal ta ibruk dette må det opprettes en endring i:
- https://github.com/navikt/familie-endringslogg-sanity/blob/main/schemas/schema.js - Legg til ny løsning med app-id
- https://github.com/navikt/familie-endringslogg/blob/main/src/main/kotlin/no/nav/familie/Application.kt - legge inn nye url-er i cors
- Legge til nye personer i sanity studio som får tilgang til å redigere innhold
- Frontend-komponent: https://www.npmjs.com/package/@navikt/familie-endringslogg

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #team-familie.
