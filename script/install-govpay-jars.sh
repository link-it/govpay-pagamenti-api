#!/bin/bash
#
# Installa nel repository Maven locale le librerie di GovPay (moduli jars/ e pom
# radice "bom") compilate dai sorgenti ufficiali del tag GitHub link-it/govpay.
#
# Il war di questo progetto dipende da quelle librerie (api-commons, core, orm, ...)
# che non sono pubblicate su Maven Central: vanno quindi prodotte una volta, in locale
# e in CI, prima di compilare il progetto.
#
# Uso:
#   ./script/install-govpay-jars.sh [--version <versione>] [--source <file.tgz|directory>]
#
#   --version  versione di GovPay (default: la versione del pom radice di questo progetto)
#   --source   sorgenti gia' disponibili: archivio del tag o directory estratta
#              (default: download da https://github.com/link-it/govpay)
#
# Variabili d'ambiente:
#   MVN        comando maven da usare (default: mvn)
#   MVN_OPTS   opzioni aggiuntive passate a maven (per esempio -o oppure -B)
#
set -euo pipefail

BASEDIR="$(cd "$(dirname "$0")/.." && pwd)"
MVN="${MVN:-mvn}"
VERSION=
SOURCE=

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="${2:-}"; shift 2 ;;
    --source)  SOURCE="${2:-}"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *)         echo "Opzione sconosciuta: $1" >&2; exit 1 ;;
  esac
done

# La versione di default e' quella del progetto: le librerie devono coincidere con
# quelle dichiarate dalla dependencyManagement del pom radice (${project.version}).
if [[ -z "${VERSION}" ]]; then
  VERSION="$(sed -n '0,/<version>/s#.*<version>\(.*\)</version>.*#\1#p' "${BASEDIR}/pom.xml")"
fi
[[ -n "${VERSION}" ]] || { echo "Errore: impossibile determinare la versione di GovPay." >&2; exit 1; }

WORKDIR="$(mktemp -d)"
trap 'rm -rf "${WORKDIR}"' EXIT

if [[ -z "${SOURCE}" ]]; then
  echo "Download sorgenti GovPay ${VERSION} ..."
  curl -fsSL "https://codeload.github.com/link-it/govpay/tar.gz/refs/tags/${VERSION}" -o "${WORKDIR}/govpay.tgz"
  SOURCE="${WORKDIR}/govpay.tgz"
fi

if [[ -f "${SOURCE}" ]]; then
  tar xzf "${SOURCE}" -C "${WORKDIR}"
  SRCDIR="$(find "${WORKDIR}" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
elif [[ -d "${SOURCE}" ]]; then
  SRCDIR="$(cd "${SOURCE}" && pwd)"
else
  echo "Errore: sorgenti non trovati [${SOURCE}]." >&2
  exit 1
fi

[[ -f "${SRCDIR}/pom.xml" && -f "${SRCDIR}/jars/pom.xml" ]] || { echo "Errore: [${SRCDIR}] non contiene i sorgenti di GovPay (pom.xml e jars/pom.xml)." >&2; exit 1; }

SRC_VERSION="$(sed -n '0,/<version>/s#.*<version>\(.*\)</version>.*#\1#p' "${SRCDIR}/pom.xml")"
[[ "${SRC_VERSION}" == "${VERSION}" ]] || { echo "Errore: i sorgenti sono della versione ${SRC_VERSION}, attesa ${VERSION}." >&2; exit 1; }

# I sorgenti del tag non sono un repository git: il plugin git-commit-id va disattivato.
# Test, analisi OWASP e SpotBugs non servono per produrre le librerie.
OPTS=(-DskipTests -Dmaven.gitcommitid.skip=true -Dowasp=none -Dspotbugs.skip=true ${MVN_OPTS:-})

echo "Installazione pom radice it.govpay:bom:${VERSION} ..."
"${MVN}" -q -N -f "${SRCDIR}/pom.xml" install "${OPTS[@]}"

echo "Installazione librerie GovPay ${VERSION} (jars/) ..."
"${MVN}" -q -f "${SRCDIR}/jars/pom.xml" install "${OPTS[@]}"

echo "Librerie GovPay ${VERSION} installate nel repository Maven locale."
