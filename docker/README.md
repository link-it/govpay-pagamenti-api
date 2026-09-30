# Immagini Docker di GovPay Pagamenti API

Questa directory contiene il necessario per costruire l'immagine Docker di GovPay
Pagamenti API: `govpay-pagamenti-api.war` su Tomcat 11. Gli script derivano da quelli di
GovPay (`docker/` del repository `link-it/govpay`), ridotti al solo war e al solo Tomcat 11.

## Contenuto

| Percorso | Cosa e' |
|---|---|
| `build_image.sh` | Costruisce **una** immagine, per un database |
| `build-images.sh` | Costruisce, e opzionalmente pubblica, un insieme di immagini (una per database) |
| `govpay/Dockerfile.daFile` | Stage installer: esegue l'installer `.tgz` locale in modalita' `text-auto` |
| `govpay/tomcat11/Dockerfile.govpay` | Immagine finale su Tomcat 11 |
| `commons/ant.install.properties.template` | Risposte all'installer usate dallo stage installer |
| `commons/tomcat11/` | Entrypoint, inizializzazione del database, configurazione datasource e di Tomcat |
| `DOCKERHUB.md` | Descrizione pubblicata sulla pagina Docker Hub |

`buildcontext/` e `compose/` sono prodotti dagli script e sono ignorati da git.

## Prerequisiti

L'immagine si costruisce a partire dall'installer del progetto:

```bash
./script/install-govpay-jars.sh      # una volta: librerie ufficiali di GovPay
mvn -Denv=installer_template clean install
cd src/main/resources/setup && sh prepareSetup.sh && cd -
```

L'installer viene prodotto in `src/main/resources/setup/target/govpay-installer-<versione>.tgz`.

## Costruzione di una immagine

```bash
# immagine senza database esterno (HSQL interno)
./docker/build_image.sh

# immagine per PostgreSQL, con tag esplicito
./docker/build_image.sh -d postgresql -t linkitaly/govpay-pagamenti-api:3.9.3.p3_postgres

./docker/build_image.sh -h   # elenco completo delle opzioni
```

Se `-l` non e' indicato viene usato l'installer piu' recente in
`src/main/resources/setup/target`; se `-v` non e' indicato la versione e' ricavata dal nome
dell'installer. Lo script puo' essere invocato da qualsiasi directory.

Senza `-t` il tag e' `<REGISTRY_PREFIX>/govpay-pagamenti-api:<versione>` con suffisso per
database: nessuno per HSQL, `_postgres`, `_oracle`, `_mariadb`, `_mysql`
(`REGISTRY_PREFIX`, default `linkitaly`).

Per i database esterni viene generato anche un `compose/docker-compose.yaml` di esempio, che
richiede il driver JDBC copiato a mano nella directory `compose/` (vedi `compose/README.first`).

## Insiemi di immagini

`build-images.sh` produce due insiemi, in repository distinti:

| Insieme | Repository | Immagini |
|---|---|---|
| `dev` | `linkitaly/govpay-pagamenti-api-dev` | postgresql |
| `release` | `linkitaly/govpay-pagamenti-api` | postgresql, oracle, mariadb, mysql, senza db (con `--latest` anche `:latest`) |

```bash
# anteprima dei comandi
./docker/build-images.sh \
  --version 3.9.3.p3 \
  --installer src/main/resources/setup/target/govpay-installer-3.9.3.p3.tgz \
  --set release --dry-run
```

Con `--push` le immagini vengono pubblicate: il login al registry deve essere eseguito prima
dal chiamante. `DOCKER_BIN` permette di usare un wrapper, per esempio
`DOCKER_BIN="sudo docker"`.

## Uso nella pipeline

La pipeline GitHub Actions (`.github/workflows/maven.yml`) costruisce le immagini con
`build-images.sh`, partendo dall'installer prodotto dal job `build` (artefatto
`govpay-installer`, generato solo quando i job docker gireranno davvero):

| Evento | Job | Risultato |
|---|---|---|
| push su `main` o su un branch `*.x` | `docker_dev` | immagine postgres in `linkitaly/govpay-pagamenti-api-dev` |
| push di un tag | `docker_release` | cinque immagini in `linkitaly/govpay-pagamenti-api`, piu' `:latest` |
| push su un altro branch, pull request | nessuno | niente immagini |

`docker_release` usa una matrice con un database per esecutore: cinque immagini con i
rispettivi stage installer non stanno nel disco di un solo runner. Il tag `:latest` lo
aggiunge solo la voce `hsql`, perche' e' derivato dall'immagine senza database.

Impostazioni richieste sul repository GitHub:

| Nome | Tipo | Contenuto |
|---|---|---|
| `DOCKERHUB_USERNAME` | variabile | utente Docker Hub |
| `DOCKERHUB_TOKEN` | segreto | token di accesso con permesso di scrittura |
| `DOCKER_IMAGE_BASE`, `DOCKER_IMAGE_BASE_DEV` | variabili (opzionali) | repository di destinazione, se diversi dai default |

## Esecuzione

```bash
docker run -d -p 8080:8080 -e GOVPAY_POP_DB_SKIP=false linkitaly/govpay-pagamenti-api:3.9.3.p3
```

* Il war e' esposto sotto `/govpay-pagamenti-api`, per esempio
  `http://localhost:8080/govpay-pagamenti-api/rs/basic/v3/ricevute/{idDominio}/{iuv}/{idRicevuta}`.
  Il path usato con l'ear di GovPay, `/govpay/frontend/api/pagamento/...`, viene inoltrato allo
  stesso war da una regola di rewrite.
* Porte: 8080 (HTTP), 8443 (HTTPS), 8445 (HTTPS con autenticazione client), 8009 (AJP).
* L'avvio e' considerato completato quando risponde
  `/govpay-pagamenti-api/v2/govpay-api-pagamento-v2.yaml`; a quel punto viene creato
  `/tmp/govpay_ready`.
* La configurazione di spring-security e' esternalizzata in `/etc/govpay` (installer eseguito
  con `antinstaller_springsec_ext=true`), i log sono in `/var/log/govpay`.

Variabili d'ambiente principali:

| Variabile | Uso |
|---|---|
| `GOVPAY_DB_SERVER`, `GOVPAY_DB_NAME`, `GOVPAY_DB_USER`, `GOVPAY_DB_PASSWORD` | Connessione al database (obbligatorie per i database esterni) |
| `GOVPAY_DS_JDBC_LIBS` | Directory con il driver JDBC (obbligatoria per i database esterni) |
| `GOVPAY_DS_CONN_PARAM` | Parametri aggiuntivi dell'URL JDBC |
| `GOVPAY_POP_DB_SKIP` | `false` per creare lo schema al primo avvio se il database e' vuoto (default `true`) |
| `GOVPAY_MAX_POOL`, `GOVPAY_MIN_POOL` | Dimensioni del pool di connessioni |
| `GOVPAY_AS_KEYSTORE*`, `GOVPAY_AS_TRUSTSTORE*` | Keystore e truststore dei connettori HTTPS (se assenti ne vengono generati di default) |
| `GOVPAY_AS_AJP_LISTENER`, `GOVPAY_AS_HTTP_LISTENER` | Abilitazione dei connettori AJP e HTTP |
| `GOVPAY_STARTUP_CHECK_SKIP`, `GOVPAY_STARTUP_CHECK_MAX_RETRY` | Controllo di avvio |

Le personalizzazioni all'avvio (`*.sh` o script `*.cli` per Tomcat) si montano in
`/docker-entrypoint-govpay.d/`; le properties JVM aggiuntive in `/etc/govpay_as_jvm.properties`.

## Differenze rispetto agli script di GovPay

- Solo Tomcat 11: nessuna immagine WildFly.
- Solo installer locale: le release GitHub e la pipeline Jenkins pubblicano l'installer di
  GovPay completo, non di questo progetto. Rimosse le opzioni `-j`, `-g`, `-a` e `-w` di
  `build_image.sh`.
- Nessuna console: rimosso l'hook di esternalizzazione di `Config.js`.
- Entrypoint: una sola rewrite di compatibilita' (`/govpay/frontend/api/pagamento`), nessun
  context descriptor per il backoffice, controllo di avvio sul war `govpay-pagamenti-api`.
