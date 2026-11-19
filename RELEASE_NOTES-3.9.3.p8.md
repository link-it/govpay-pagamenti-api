> ⚠️ **Progetto deprecato.** Il modello di pagamento 1 è dismesso da pagoPA e non è più supportato dal Nodo dei Pagamenti. Questo modulo è mantenuto esclusivamente per retrocompatibilità e non va adottato per nuove integrazioni, che devono usare il modello di pagamento corrente.

Aggiornamento di **GovPay Pagamenti API** alle librerie ufficiali di **GovPay 3.9.3.p8** (`api-commons`, `core`, `orm` e le altre).

## Correzioni

- **Ricevuta in PDF con logo SVG** (Issue #902): `GET /v3/ricevute/...` con `Accept: application/pdf` falliva se il logo del dominio era un SVG con un'immagine PNG o JPEG incorporata. Il war include ora `batik-codec`; se il logo non è stampabile, la ricevuta viene stampata con il logo di default e l'anomalia è registrata nel log.
- **Spec API-Pagamento v2** (Issue #903): dichiarati `nullable` i campi facoltativi di tipo data o numero delle pendenze spontanee di `POST /pagamenti` (`dataValidita`, `dataScadenza`, `annoRiferimento`, `proprieta.dataScandenzaAvviso`, `importo` di `proprieta.descrizioneImporto`). Il comportamento non cambia: i client generati dallo YAML non inviano più valori predefiniti (per esempio `0001-01-01` in C#) al posto dei campi non valorizzati.
- **Avviso bilingue** (Issue #909): corrette le etichette tedesche e slovene di `avvisi.properties`.
- **Schema Oracle** (Issue #901): il campo `xml` della tabella `fr` è opzionale anche in `oracle/gov_pay.sql`. Non serve una patch per le installazioni esistenti.

## Sicurezza

Aggiornate le dipendenze come in GovPay 3.9.3.p8: CXF 4.2.3 → 4.2.4 (con neethi 3.2.4 e xmlschema-core 2.3.3), Jackson 2.22.2 → 2.22.4, Guava 33.7.1-jre → 33.7.2-jre.

## Requisiti

- Java 21 e Tomcat 11.
- Database di GovPay 3.9.3.p8. Dalla 3.9.3.p3 non ci sono patch SQL da applicare.

## Installazione

**Installer**: `govpay-installer-3.9.3.p8.tgz`, allegato a questa release.

**Docker**: [`linkitaly/govpay-pagamenti-api`](https://hub.docker.com/r/linkitaly/govpay-pagamenti-api), tag `3.9.3.p8`, `3.9.3.p8_postgres`, `3.9.3.p8_mariadb`, `3.9.3.p8_mysql`, `3.9.3.p8_oracle`.

**Compilazione dai sorgenti**: `./script/install-govpay-jars.sh` prima di `mvn clean install`.
