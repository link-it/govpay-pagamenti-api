<p align="center">
<img src="https://www.link.it/wp-content/uploads/2025/01/logo-govpay.svg" alt="GovPay Logo" width="200"/>
</p>

# GovPay Pagamenti API

> ⚠️ **Progetto deprecato.** Il modello di pagamento 1 è dismesso da pagoPA e non è più supportato dal Nodo dei Pagamenti. Questo modulo è mantenuto esclusivamente per retrocompatibilità e non va adottato per nuove integrazioni, che devono usare il modello di pagamento corrente.

[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=link-it_govpay&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=link-it_govpay)
[![Docker](https://github.com/link-it/govpay/blob/master/docs/_images/docker.svg)](https://hub.docker.com/r/linkitaly/govpay)
[![Documentation Status](https://readthedocs.org/projects/govpay/badge/?version=master)](https://govpay.readthedocs.io/it/latest/?badge=master)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://raw.githubusercontent.com/link-it/govpay/master/LICENSE)

Wrapper di [GovPay](https://github.com/link-it/govpay) che espone il solo sottoinsieme delle API di Pagamento necessario ai portali degli Enti Creditori per avviare i pagamenti tramite il Checkout pagoPA e acquisire le relative ricevute.

Il progetto è distribuito come un unico war per Tomcat, `govpay-pagamenti-api.war`, e utilizza il database e le librerie ufficiali di GovPay 3.9.3.p3 (`api-commons`, `core`, `orm` e le altre), incluse come dipendenze jar. Il codice aggiunto rispetto alla 3.9.3.p3, cioè l'avvio del pagamento tramite Checkout, si trova nel war, nel package `it.govpay.pagamento.checkout`.

## Funzionalità

Il war espone due operazioni, raggiungibili sotto il context root `/govpay-pagamenti-api`.

### Avvio di un pagamento tramite Checkout pagoPA

`POST /rs/{autenticazione}/v2/pagamenti` (API Pagamento v2)

* Avvia una sessione di pagamento per una o più pendenze. Le pendenze si indicano con l'identificativo del gestionale (`idA2A` e `idPendenza`), con gli estremi dell'avviso (`idDominio` e `iuv`) oppure, per i pagamenti spontanei, fornendone direttamente i dati.
* Costruisce il carrello e lo inoltra al Checkout pagoPA. La risposta è `201` con la URL di redirect, oppure `302` con l'header `Location` se è abilitato il redirect diretto.
* Il pagamento avviene sempre tramite Checkout, senza bisogno di abilitarlo: servono la URL base in `it.govpay.checkout.baseUrl` (default: `https://api.platform.pagopa.it/checkout/ec/v1`) e una stazione in versione V2. Il redirect diretto si abilita con `it.govpay.checkout.response.sendRedirect.enabled`. La property `it.govpay.checkout.enabled` non viene considerata.
* Il parametro opzionale `idSessionePortale` è accettato solo per compatibilità con i client esistenti: viene riportato nelle tracce della richiesta, ma non viene salvato né restituito nella notifica di pagamento all'Ente. Per correlare l'esito alla sessione del portale si usano l'`idSession` restituito nella risposta e la URL di ritorno del Checkout.
* Utenze ammesse: applicazione, cittadino e anonimo, con diritto di scrittura sul servizio API Pagamenti.

### Acquisizione della ricevuta di pagamento

`GET /rs/{autenticazione}/v3/ricevute/{idDominio}/{iuv}/{idRicevuta}` (API Pagamento v3)

* Restituisce la ricevuta pagoPA (RT o receipt) di un pagamento, nel formato indicato dall'header `Accept`:
  * `application/json`: dati della ricevuta, della pendenza pagata e messaggi RPT/RT in XML (base64) e JSON;
  * `application/pdf`: stampa della ricevuta telematica.
  Qualunque altro valore di `Accept`, incluso `*/*` o l'header assente, restituisce `406`.
* Controlli di accesso:
  * applicazione: la pendenza deve essere stata caricata dall'applicazione chiamante;
  * cittadino: deve essere il debitore della pendenza;
  * anonimo: la pendenza deve essere presente nella sessione e la ricevuta deve essere entro l'intervallo configurato per l'utenza anonima.
  Per cittadino e anonimo i dati personali del versante e del pagatore vengono mascherati.
* Utenze ammesse: applicazione, cittadino e anonimo, con diritto di lettura sul servizio API Pagamenti.
* I campi `idPagamento` e `idSessionePsp` non vengono valorizzati e `modello` vale sempre `PSP`, come nelle API di GovPay 3.9.3.p3.

## Autenticazione

Il segmento `{autenticazione}` dell'URL identifica la modalità di autenticazione. Per default sono attive `basic` e `ssl`.

Le altre modalità si abilitano sul war con lo script `src/main/resources/scripts/abilitaAuth.sh`, indicandole nell'opzione `-pag`:

| Opzione `-pag` | Segmento URL | Autenticazione |
|---|---|---|
| `basic` | `basic` | HTTP Basic sulle utenze registrate in GovPay |
| `ldap` | `basic` | HTTP Basic su LDAP (al posto di `basic`) |
| `ssl` | `ssl` | certificato client |
| `hdrcert` | `sslheader` | certificato client inoltrato in un header |
| `header` | `header` | principal inoltrato in un header |
| `spid` | `spid` | cittadino autenticato con SPID |
| `session` | `session` | cittadino letto dalla sessione condivisa |
| `public` | `public` | utenza anonima |
| `apikey` | `apikey` | API key |
| `oauth2` | `oauth2` | token JWT OAuth2 |

```
sh src/main/resources/scripts/abilitaAuth.sh -pag basic,ssl,spid,public
```

Con la modalità `public` (utenza anonima) sono accessibili solo `POST /rs/public/v2/pagamenti` e `GET /rs/public/v3/ricevute/**`.

## Specifiche OpenAPI

* API Pagamento v2: `/govpay-pagamenti-api/v2/govpay-api-pagamento-v2.yaml`
* API Pagamento v3: `/govpay-pagamenti-api/v3/govpay-api-pagamento.yaml`
* Swagger UI: `/govpay-pagamenti-api/index.html`

## Compilazione e installazione

Requisiti: Java 21, Maven e Tomcat 11.

* Librerie di GovPay: non sono pubblicate su Maven Central e vanno installate una volta nel repository Maven locale, compilandole dai sorgenti ufficiali del tag GitHub corrispondente alla versione del progetto:

  ```
  ./script/install-govpay-jars.sh
  ```

  Lo script scarica i sorgenti da `https://github.com/link-it/govpay`; con `--source` si può indicare un archivio o una directory già disponibili. La pipeline esegue lo stesso passo, con la cache delle librerie.
* Compilazione: `mvn clean install`. Il war viene prodotto in `wars/api-pagamento/target/govpay-pagamenti-api.war` e le properties di configurazione (`govpay.properties`, `log4j2.xml` e altre) sono incluse in `WEB-INF/classes`.
* Installer:
  1. compilare con `mvn -Denv=installer_template clean install`;
  2. eseguire `sh prepareSetup.sh` dalla directory `src/main/resources/setup`;
  3. lanciare l'installer da `target/govpay-installer-VERSIONE` (`sh install.sh` o `install.cmd`).
  
  L'installer produce in `dist/` il war configurato, i datasource e gli script SQL.

## Docker

La directory `docker/` contiene gli script per costruire l'immagine Docker del war su Tomcat 11, a partire dall'installer:

```
./docker/build_image.sh -d postgresql
```

Dettagli, opzioni e variabili d'ambiente in [docker/README.md](docker/README.md).

## Documentazione

- Documentazione di GovPay: [Read the docs](https://govpay.readthedocs.io/it/master/) ([download](https://readthedocs.org/projects/govpay/downloads/htmlzip/master/))

## Contatti

- Segnalazioni: [GitHub Issues](https://github.com/link-it/GovPay/issues)

## Licenza

GovPay - Porta di Accesso al Nodo dei Pagamenti SPC
http://www.gov4j.it/govpay

Copyright (c) 2014-2026 Link.it srl (http://www.link.it).

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
