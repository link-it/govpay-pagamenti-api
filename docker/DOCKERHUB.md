<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay Pagamenti API

[![Docker Hub](https://img.shields.io/docker/v/linkitaly/govpay-pagamenti-api?label=Docker%20Hub&sort=semver)](https://hub.docker.com/r/linkitaly/govpay-pagamenti-api)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay/master/LICENSE)

## Descrizione

Wrapper di [GovPay](https://github.com/link-it/govpay) che espone il solo sottoinsieme delle API di Pagamento necessario ai portali degli Enti Creditori per avviare i pagamenti tramite il Checkout pagoPA e acquisire le relative ricevute. L'immagine contiene il war `govpay-pagamenti-api.war` su Tomcat 11 e Java 21, e utilizza il core e il database di GovPay 3.9.3.p3.

Operazioni esposte, sotto il context `/govpay-pagamenti-api`:

| Operazione | Descrizione |
|---|---|
| `POST /rs/{autenticazione}/v2/pagamenti` | Avvia il pagamento di una o piu' pendenze tramite il Checkout pagoPA (risposta `201` con la URL di redirect, oppure `302`) |
| `GET /rs/{autenticazione}/v3/ricevute/{idDominio}/{iuv}/{idRicevuta}` | Restituisce la ricevuta di pagamento in `application/json` o `application/pdf`, secondo l'header `Accept` |

Le specifiche OpenAPI sono pubblicate in `/govpay-pagamenti-api/v2/govpay-api-pagamento-v2.yaml` e `/govpay-pagamenti-api/v3/govpay-api-pagamento.yaml`, consultabili con la Swagger UI in `/govpay-pagamenti-api/index.html`.

## Tag disponibili

| Tag | Database |
|---|---|
| `<versione>`, `latest` | HSQL interno al container (per prove, nessun database esterno) |
| `<versione>_postgres` | PostgreSQL |
| `<versione>_mariadb` | MariaDB |
| `<versione>_mysql` | MySQL |
| `<versione>_oracle` | Oracle |

Ogni immagine e' preparata per un solo tipo di database: il tipo non si cambia con le variabili d'ambiente, si sceglie il tag.

Le immagini di sviluppo, prodotte a ogni modifica sui branch principali, sono pubblicate nel repository separato [`linkitaly/govpay-pagamenti-api-dev`](https://hub.docker.com/r/linkitaly/govpay-pagamenti-api-dev) con tag `<versione>_postgres`.

Elenco completo: https://hub.docker.com/r/linkitaly/govpay-pagamenti-api/tags

## Quick Start

### Prova con database HSQL interno

```bash
docker run -d --name govpay-pagamenti-api \
  -p 8080:8080 \
  -e GOVPAY_POP_DB_SKIP=false \
  linkitaly/govpay-pagamenti-api:latest
```

`GOVPAY_POP_DB_SKIP=false` crea lo schema al primo avvio. Il database HSQL resta all'interno del container.

### PostgreSQL con Docker Compose

Il driver JDBC non e' incluso nell'immagine: va messo in una directory montata nel container e indicata con `GOVPAY_DS_JDBC_LIBS`.

```yaml
services:
  database:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: govpay
      POSTGRES_USER: govpay
      POSTGRES_PASSWORD: govpay
    volumes:
      - govpay-db:/var/lib/postgresql/data

  govpay-pagamenti-api:
    image: linkitaly/govpay-pagamenti-api:<versione>_postgres
    depends_on:
      - database
    ports:
      - "8080:8080"
      - "8443:8443"
    environment:
      GOVPAY_DB_SERVER: database:5432
      GOVPAY_DB_NAME: govpay
      GOVPAY_DB_USER: govpay
      GOVPAY_DB_PASSWORD: govpay
      GOVPAY_DS_JDBC_LIBS: /opt/jdbc
      GOVPAY_POP_DB_SKIP: "false"
    volumes:
      # directory locale che contiene postgresql-<versione>.jar
      - ./jdbc:/opt/jdbc:ro
      - ./log:/var/log/govpay
    healthcheck:
      test: ["CMD", "test", "-f", "/tmp/govpay_ready"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 120s

volumes:
  govpay-db:
```

Il database puo' essere quello di un'installazione di GovPay 3.9.3.p3: in questo caso lasciare `GOVPAY_POP_DB_SKIP` al valore di default (`true`), cosi' lo schema esistente non viene toccato.

Una volta avviato, il servizio risponde per esempio su:

```
http://localhost:8080/govpay-pagamenti-api/rs/basic/v3/ricevute/{idDominio}/{iuv}/{idRicevuta}
```

Il path usato con l'ear di GovPay, `/govpay/frontend/api/pagamento/...`, viene inoltrato allo stesso war, cosi' i client configurati per quel path continuano a funzionare.

## Configurazione

### Database

| Variabile | Descrizione | Default |
|---|---|---|
| `GOVPAY_DB_SERVER` | Host e porta del database (`host:porta`) | obbligatoria, tranne HSQL |
| `GOVPAY_DB_NAME` | Nome del database (per Oracle: service name o SID) | obbligatoria, tranne HSQL |
| `GOVPAY_DB_USER` | Utente del database | obbligatoria, tranne HSQL |
| `GOVPAY_DB_PASSWORD` | Password del database | - |
| `GOVPAY_DS_JDBC_LIBS` | Directory che contiene il driver JDBC | obbligatoria, tranne HSQL |
| `GOVPAY_DS_CONN_PARAM` | Parametri aggiuntivi dell'URL JDBC | - |
| `GOVPAY_ORACLE_JDBC_URL_TYPE` | Solo Oracle: `servicename` oppure `sid` | obbligatoria per Oracle |
| `GOVPAY_POP_DB_SKIP` | `false` per creare lo schema al primo avvio, se il database e' vuoto | `true` |
| `GOVPAY_MAX_POOL`, `GOVPAY_MIN_POOL` | Dimensione massima e minima del pool di connessioni | `10`, `2` |
| `GOVPAY_DS_BLOCKING_TIMEOUT` | Attesa massima (ms) di una connessione dal pool | `30000` |
| `GOVPAY_LIVE_DB_CHECK_MAX_RETRY`, `GOVPAY_READY_DB_CHECK_MAX_RETRY` | Tentativi di attesa della disponibilita' del database all'avvio | `30`, `5` |

### Connettori e HTTPS

| Porta | Connettore |
|---|---|
| `8080` | HTTP |
| `8443` | HTTPS |
| `8445` | HTTPS con autenticazione client obbligatoria (modalita' `ssl`) |
| `8009` | AJP |

| Variabile | Descrizione |
|---|---|
| `GOVPAY_AS_KEYSTORE`, `GOVPAY_AS_KEYSTORE_PASSWORD`, `GOVPAY_AS_KEYSTORE_TIPO`, `GOVPAY_AS_KEYSTORE_KEY_PASSWORD` | Keystore dei connettori HTTPS. Se non indicato viene generato un keystore di prova (`CN=test.govpay.it`) |
| `GOVPAY_AS_TRUSTSTORE`, `GOVPAY_AS_TRUSTSTORE_PASSWORD`, `GOVPAY_AS_TRUSTSTORE_TIPO` | Truststore dei certificati client accettati sulla porta 8445. Se non indicato ne viene generato uno di prova |
| `GOVPAY_AS_HTTP_LISTENER` | `false` per disabilitare il connettore HTTP |
| `GOVPAY_AS_AJP_LISTENER` | `false` per disabilitare il connettore AJP |
| `GOVPAY_AS_HTTP_WORKER_MAX_THREADS`, `GOVPAY_AS_HTTPS_WORKER_MAX_THREADS`, `GOVPAY_AS_HTTPS_MTLS_WORKER_MAX_THREADS` | Thread massimi per connettore |
| `GOVPAY_AS_MAX_POST_SIZE` | Dimensione massima del body delle richieste (byte) |

### JVM e avvio

| Variabile | Descrizione | Default |
|---|---|---|
| `MAX_JVM_PERC` | Percentuale della memoria del container usabile dalla JVM | `80.0` |
| `JAVA_OPTS` | Opzioni JVM aggiuntive | - |
| `GOVPAY_JVM_AGENT_JAR` | Java agent da caricare all'avvio | - |
| `GOVPAY_STARTUP_CHECK_SKIP` | `true` per non attendere l'avvio del war | `false` |
| `GOVPAY_STARTUP_CHECK_MAX_RETRY` | Tentativi di verifica dell'avvio, ogni 5 secondi dopo un'attesa iniziale di 20 | `60` |

L'avvio e' considerato completato quando risponde `/govpay-pagamenti-api/v2/govpay-api-pagamento-v2.yaml`: a quel punto viene creato il file `/tmp/govpay_ready`, utilizzabile come readiness probe. Se il war non risponde entro il numero di tentativi, il container viene arrestato.

### Configurazione applicativa

Le properties di GovPay si sovrascrivono, in ordine di precedenza:

1. come system property JVM, in un file `/etc/govpay_as_jvm.properties` montato nel container (o con `-D` in `JAVA_OPTS`);
2. nel file esterno `/etc/govpay/govpay.properties`;
3. altrimenti vale la configurazione interna al war.

I pagamenti sono avviati sempre tramite il Checkout pagoPA, senza bisogno di abilitarlo. Le properties che lo riguardano:

```properties
# URL base del Checkout pagoPA (default: ambiente di produzione)
it.govpay.checkout.baseUrl=https://api.platform.pagopa.it/checkout/ec/v1
# true per rispondere con 302 verso il Checkout invece che con 201 e la URL nel body
it.govpay.checkout.response.sendRedirect.enabled=false
```

La property `it.govpay.checkout.enabled` non viene considerata.

La configurazione di log4j2 si sovrascrive con `/etc/govpay/GovPay-API-Pagamento-log4j2.xml` oppure `/etc/govpay/log4j2.xml`.

### Autenticazione

Sono attive per default le modalita' `basic` (utenze registrate in GovPay) e `ssl` (certificato client), raggiungibili con i segmenti `/rs/basic/...` e `/rs/ssl/...`.

La configurazione di Spring Security e' esterna al war: `/etc/govpay/api-pagamento-applicationContext-security.xml`. Le altre modalita' (`spid`, `header`, `sslheader`, `session`, `public`, `apikey`, `oauth2`, LDAP) sono presenti nel file come blocchi commentati: per attivarle si monta una versione modificata del file.

Attenzione: se si monta un volume su tutta la directory `/etc/govpay`, il volume deve contenere anche questo file, altrimenti il war non si avvia. Si puo' partire da quello dell'immagine:

```bash
docker run --rm --entrypoint cat linkitaly/govpay-pagamenti-api:<versione> \
  /etc/govpay/api-pagamento-applicationContext-security.xml > api-pagamento-applicationContext-security.xml
```

### Volumi

| Path | Contenuto |
|---|---|
| `/var/log/govpay` | Log di GovPay e di Tomcat |
| `/etc/govpay` | Configurazione esterna: `govpay.properties`, configurazione log4j2, Spring Security |
| `/docker-entrypoint-govpay.d/` | Personalizzazioni eseguite all'avvio: script `*.sh` e direttive `*.cli` di configurazione di Tomcat |

## Inizializzazione del database

Con `GOVPAY_POP_DB_SKIP=false`, all'avvio lo schema viene creato se la tabella `utenze` non esiste o non contiene righe; se contiene almeno un'utenza il database non viene modificato.

Gli script SQL sono inclusi nell'immagine, per il database del tag:

- `/opt/<database>/gov_pay.sql`: creazione dello schema;
- `/opt/<database>/patch/`: patch di aggiornamento da una versione alla successiva, da applicare manualmente in caso di aggiornamento.

```bash
docker exec govpay-pagamenti-api ls /opt/postgresql/patch
```

## Troubleshooting

```bash
# log di avvio del container
docker logs govpay-pagamenti-api

# dettaglio dell'entrypoint
docker exec govpay-pagamenti-api cat /tmp/entrypoint_debug.log

# log applicativi e di Tomcat
docker exec govpay-pagamenti-api ls /var/log/govpay
```

Cause frequenti di mancato avvio:

- driver JDBC assente: `GOVPAY_DS_JDBC_LIBS` non valorizzata o directory senza file `.jar`;
- database non raggiungibile entro i tentativi previsti (`GOVPAY_LIVE_DB_CHECK_MAX_RETRY`);
- volume su `/etc/govpay` privo di `api-pagamento-applicationContext-security.xml`.

## Link utili

- **Repository GitHub di GovPay**: https://github.com/link-it/govpay
- **Documentazione di GovPay**: https://govpay.readthedocs.io/
- **Segnalazioni**: https://github.com/link-it/govpay/issues
- **Documentazione pagoPA**: https://docs.pagopa.it/

## Licenza

Questo progetto e' rilasciato sotto licenza GPL-3.0. Vedere il file [LICENSE](https://github.com/link-it/govpay/blob/master/LICENSE) per i dettagli.

Copyright (c) 2014-2026 [Link.it srl](http://www.link.it)
