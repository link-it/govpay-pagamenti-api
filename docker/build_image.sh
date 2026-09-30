#!/bin/bash
#
# Costruisce l'immagine Docker di GovPay Pagamenti API (govpay-pagamenti-api.war su Tomcat 11)
# partendo dall'installer prodotto da src/main/resources/setup/prepareSetup.sh.
#

function printHelp() {
echo "Usage $(basename $0) [ -t <repository>:<tagname> | <Installer Sorgente> | <Personalizzazioni> | <Avanzate> | -h ]"
echo 
echo "Options
-t <TAG>       : Imposta il nome del TAG ed il repository locale utilizzati per l'immagine prodotta 
                 NOTA: deve essere rispettata la sintassi <repository>:<tagname>
-h             : Mostra questa pagina di aiuto

Installer Sorgente:
-l <FILE>      : Installer binario da utilizzare (default: src/main/resources/setup/target/govpay-installer-<VERSIONE>.tgz)
-v <VERSIONE>  : Versione dell'installer (default: ricavata dal nome del file indicato con -l)

Personalizzazioni:
-d <TIPO>      : Prepara l'immagine per essere utilizzata su un particolare database  (valori: [ hsql, postgresql, mysql, mariadb, oracle] , default: hsql)
-e <PATH>      : Imposta il path interno utilizzato per i file di configurazione di govpay 
-f <PATH>      : Imposta il path interno utilizzato per i log di govpay

Avanzate:
-i <FILE>      : Usa il template ant.installer.properties indicato per la generazione degli archivi dall'installer
-r <DIRECTORY> : Inserisce il contenuto della directory indicata, tra i contenuti custom 
"
}

# Comando docker: sovrascrivibile con DOCKER_BIN per gli ambienti in cui serve
# un wrapper, per esempio DOCKER_BIN="sudo docker".
if [ -n "${DOCKER_BIN}" ]
then
  DOCKERBIN="${DOCKER_BIN}"
else
  DOCKERBIN="$(which docker)"
  if [ -z "${DOCKERBIN}" ]
  then
     echo "Impossibile trovare il comando \"docker\""
     exit 2
  fi
fi


TAG=
VER=
DB=
LOCALFILE=
TEMPLATE=
REGISTRY_PREFIX=${REGISTRY_PREFIX:-linkitaly}
IMAGE_NAME=govpay-pagamenti-api

while getopts "ht:v:d:l:i:r:e:f:" opt; do
  case $opt in
    t) TAG="$OPTARG"; NO_COLON=${TAG//:/}
      [ ${#TAG} -eq ${#NO_COLON} -o "${TAG:0:1}" == ':' -o "${TAG:(-1):1}" == ':' ] && { echo "Il tag fornito \"$TAG\" non utilizza la sintassi <repository>:<tagname>"; exit 2; } ;;
    v) VER="$OPTARG"  ;;
    d) DB="${OPTARG}"; case "$DB" in hsql);;postgresql);;mysql);;mariadb);;oracle);;*) echo "Database non supportato: $DB"; exit 2;; esac ;;
    l) LOCALFILE="$OPTARG"
        [ ! -f "${LOCALFILE}" ] && { echo "Il file indicato non esiste o non e' raggiungibile [${LOCALFILE}]."; exit 3; } 
       ;;
    i) TEMPLATE="${OPTARG}"
        [ ! -f "${TEMPLATE}" ] && { echo "Il file indicato non esiste o non e' raggiungibile [${TEMPLATE}]."; exit 3; } 
        ;;
    r) CUSTOM_RUNTIME="${OPTARG}"
        [ ! -d "${CUSTOM_RUNTIME}" ] && { echo "la directory indicata non esiste o non e' raggiungibile [${CUSTOM_RUNTIME}]."; exit 3; }
        [ -z "$(ls -A ${CUSTOM_RUNTIME})" ] && { echo "la directory [${CUSTOM_RUNTIME}] e' vuota.";  }
        ;;
    e) CUSTOM_GOVPAY_HOME="${OPTARG}" ;;
    f) CUSTOM_GOVPAY_LOG="${OPTARG}" ;;
    h) printHelp
       exit 0
       ;;
    \?)
      echo "Opzione non valida: -$opt"
      exit 1
      ;;
  esac
done

# I percorsi passati dall'utente sono relativi alla directory da cui e' stato invocato:
# vanno resi assoluti prima di spostarsi nella directory dello script.
function abspath() { echo "$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"; }
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Installer: se non indicato si usa quello prodotto da prepareSetup.sh
if [ -z "${LOCALFILE}" ]
then
  SETUP_TARGET="${SCRIPT_DIR}/../src/main/resources/setup/target"
  if [ -n "${VER}" ]
  then
    LOCALFILE="${SETUP_TARGET}/govpay-installer-${VER}.tgz"
  else
    LOCALFILE="$(ls -t "${SETUP_TARGET}"/govpay-installer-*.tgz 2> /dev/null | head -n 1)"
  fi
  [ -f "${LOCALFILE}" ] || { echo "Installer non trovato [${LOCALFILE:-${SETUP_TARGET}/govpay-installer-*.tgz}]: eseguire prima src/main/resources/setup/prepareSetup.sh oppure indicarlo con -l"; exit 3; }
fi
LOCALFILE="$(abspath "${LOCALFILE}")"

# Versione: Dockerfile.daFile estrae l'archivio per nome (govpay-installer-<VERSIONE>.tgz)
INSTALLER_NAME="$(basename "${LOCALFILE}")"
if [ -z "${VER}" ]
then
  VER="${INSTALLER_NAME#govpay-installer-}"
  VER="${VER%.tgz}"
fi
[ "${INSTALLER_NAME}" == "govpay-installer-${VER}.tgz" ] || { echo "Il nome dell'installer [${INSTALLER_NAME}] non corrisponde alla versione [${VER}]: atteso govpay-installer-${VER}.tgz"; exit 2; }

[ -n "${TEMPLATE}" ] && TEMPLATE="$(abspath "${TEMPLATE}")"
[ -n "${CUSTOM_RUNTIME}" ] && CUSTOM_RUNTIME="$(cd "${CUSTOM_RUNTIME}" && pwd)"
cd "${SCRIPT_DIR}" || { echo "Impossibile spostarsi nella directory dello script"; exit 2; }

rm -rf buildcontext
mkdir -p buildcontext/
cp -fr commons/tomcat11 buildcontext/commons
cp -f commons/* buildcontext/commons 2> /dev/null
cp -f "${LOCALFILE}" buildcontext/

DOCKERBUILD_OPTS=('--build-arg' "govpay_appserver=tomcat11")
DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_fullversion=${VER}")
[ -n "${TEMPLATE}" ] &&  cp -f "${TEMPLATE}" buildcontext/commons/ant.install.properties.template
[ -n "${CUSTOM_GOVPAY_HOME}" ] && DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_home=${CUSTOM_GOVPAY_HOME}")
[ -n "${CUSTOM_GOVPAY_LOG}" ] && DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_log=${CUSTOM_GOVPAY_LOG}")
if [ -n "${CUSTOM_RUNTIME}" ]
then
  cp -r ${CUSTOM_RUNTIME}/ buildcontext/runtime
  DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "runtime_custom_archives=runtime")
fi

if [ -n "${DB}" ]
then
  if [ "${DB}" == 'mariadb' ]
  then
    DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_database_vendor=mysql")
  else
    DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "govpay_database_vendor=${DB}")
  fi
fi

# Build immagine installer
INSTALLER_IMAGE="${REGISTRY_PREFIX}/${IMAGE_NAME}-installer_${DB:-hsql}:${VER}"
export DOCKER_BUILDKIT=false
${DOCKERBIN} build "${DOCKERBUILD_OPTS[@]}" \
  -t "${INSTALLER_IMAGE}" \
  -f govpay/Dockerfile.daFile buildcontext
RET=$?
[ ${RET} -eq  0 ] || exit ${RET}
 
if [ "${DB}" == 'mariadb' ]
then
  c=$(( ${#DOCKERBUILD_OPTS[@]} - 1 ))
  unset  DOCKERBUILD_OPTS[$c]
  DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} "govpay_database_vendor=mariadb")
fi

# Build immagine GovPay Pagamenti API
if [ -z "$TAG" ] 
then
  REPO=${REGISTRY_PREFIX}/${IMAGE_NAME}
  case "${DB:-hsql}" in
  hsql) TAG="${REPO}:${VER}" ;;
  postgresql) TAG="${REPO}:${VER}_postgres" ;;
  *) TAG="${REPO}:${VER}_${DB}" ;;
  esac
fi

DOCKERBUILD_OPTS=(${DOCKERBUILD_OPTS[@]} '--build-arg' "source_image=${INSTALLER_IMAGE}")

VCS_REF=$(git rev-parse --short HEAD 2>/dev/null || echo unknown)
${DOCKERBIN} build "${DOCKERBUILD_OPTS[@]}" \
--build-arg "vcs_ref=${VCS_REF}" \
-t "${TAG}" \
-f govpay/tomcat11/Dockerfile.govpay buildcontext
RET=$?
[ ${RET} -eq  0 ] || exit ${RET}



if [ "${DB:-hsql}" != 'hsql' ]
then
  mkdir -p compose/govpay_{conf,log}
  chmod 777 compose/govpay_{conf,log}

  SHORT=${TAG#*:}
  cat - << EOYAML > compose/docker-compose.yaml
version: '2'
services:
  govpay-pagamenti-api:
    container_name: govpay-pagamenti-api_${SHORT}
    image: ${TAG}
    depends_on:
        - database
    ports:
        - 8080:8080
        - 8443:8443
        - 8445:8445
    volumes:
        - ./govpay_log:${CUSTOM_GOVPAY_LOG:-/var/log/govpay}
EOYAML
  if [ "${DB:-hsql}" == 'postgresql' ]
  then
    cat - << EOYAML >> compose/docker-compose.yaml
          # Il driver deve essere compiato manualmente nella directory corrente
        - ./postgresql-42.4.0.jar:/tmp/postgresql-42.4.0.jar 
    environment:
        - GOVPAY_DB_SERVER=pg_govpay_${SHORT}
        - GOVPAY_DB_NAME=govpaydb
        - GOVPAY_DB_USER=govpay
        - GOVPAY_DB_PASSWORD=govpay
        - GOVPAY_POSTGRESQL_JDBC_PATH=/tmp/postgresql-42.4.0.jar 
        - GOVPAY_POP_DB_SKIP=false
  database:
    container_name: pg_govpay_${SHORT}
    image: postgres:13
    environment:
        - POSTGRES_DB=govpaydb
        - POSTGRES_USER=govpay
        - POSTGRES_PASSWORD=govpay
EOYAML
    echo 
    echo "ATTENZIONE: Copiare il driver jdbc postgresql 'postgresql-42.4.0.jar' dentro la directory './compose/'"
    echo
    echo "ATTENZIONE: Copiare il driver jdbc postgresql 'postgresql-42.4.0.jar' dentro la directory './compose/'" > compose/README.first
  elif [ "${DB:-hsql}" == 'mariadb' ]
  then
    cat - << EOYAML >> compose/docker-compose.yaml
        # Il driver deve essere compiato manualmente nella directory corrente
        - ./mariadb-java-client-3.0.6.jar:/tmp/mariadb-java-client-3.0.6.jar 
    environment:
        - GOVPAY_DB_SERVER=my_govpay_${SHORT}
        - GOVPAY_DB_NAME=govpaydb
        - GOVPAY_DB_USER=govpay
        - GOVPAY_DB_PASSWORD=govpay
        - GOVPAY_MARIADB_JDBC_PATH=/tmp/mariadb-java-client-3.0.6.jar
        - GOVPAY_POP_DB_SKIP=false
  database:
    container_name: my_govpay_${SHORT}
    image: mariadb:10.6
    environment:
      - MARIADB_DATABASE=govpaydb
      - MARIADB_USER=govpay
      - MARIADB_PASSWORD=govpay
      - MARIADB_ROOT_PASSWORD=my-secret-pw
    ports:
       - 3306:3306
EOYAML
    echo 
    echo "ATTENZIONE: Copiare il driver jdbc Mariadb 'mariadb-java-client-3.0.6.jar' dentro la directory './compose/'"
    echo
    echo "ATTENZIONE: Copiare il driver jdbc Mariadb 'mariadb-java-client-3.0.6.jar' dentro la directory './compose/'" > compose/README.first
  elif [ "${DB:-hsql}" == 'mysql' ]
  then
    cat - << EOYAML >> compose/docker-compose.yaml
        # Il driver deve essere compiato manualmente nella directory corrente
        - ./mysql-connector-java-8.0.29.jar:/tmp/mysql-connector-java-8.0.29.jar 
    environment:
        - GOVPAY_DB_SERVER=my_govpay_${SHORT}
        - GOVPAY_DB_NAME=govpaydb
        - GOVPAY_DB_USER=govpay
        - GOVPAY_DB_PASSWORD=govpay
        - GOVPAY_MYSQL_JDBC_PATH=/tmp/mysql-connector-java-8.0.29.jar
        - GOVPAY_POP_DB_SKIP=false
  database:
    container_name: my_govpay_${SHORT}
    image: mysql:8.0
    environment:
      - MYSQL_DATABASE=govpaydb
      - MYSQL_USER=govpay
      - MYSQL_PASSWORD=govpay
      - MYSQL_ROOT_PASSWORD=my-secret-pw
    ports:
       - 3306:3306
EOYAML
    echo 
    echo "ATTENZIONE: Copiare il driver jdbc Mysql 'mysql-connector-java-8.0.29.jar' dentro la directory './compose/'"
    echo
    echo "ATTENZIONE: Copiare il driver jdbc Mysql 'mysql-connector-java-8.0.29.jar' dentro la directory './compose/'" > compose/README.first


  elif [ "${DB:-hsql}" == 'oracle' ]
  then
    mkdir -p compose/oracle_startup
    mkdir compose/ORADATA
    chmod 777 compose/ORADATA
    cat - << EOSQL > compose/oracle_startup/create_db_and_user.sql
alter session set container = GOVPAYPDB;
-- USER GOVPAY
CREATE USER "GOVPAY" IDENTIFIED BY "GOVPAY"  
DEFAULT TABLESPACE "USERS"
TEMPORARY TABLESPACE "TEMP";
ALTER USER "GOVPAY" QUOTA UNLIMITED ON "USERS";
GRANT "CONNECT" TO "GOVPAY" ;
GRANT "RESOURCE" TO "GOVPAY" ;
GRANT CREATE VIEW TO "GOVPAY" ;
EOSQL

    cat - << EOYAML >> compose/docker-compose.yaml
        # Il driver deve essere compiato manualmente nella directory corrente
        - ./ojdbc10.jar:/tmp/ojdbc10.jar 
    environment:
        - GOVPAY_DB_SERVER=or_govpay_${SHORT}
        - GOVPAY_DB_NAME=GOVPAYPDB
        - GOVPAY_DB_USER=GOVPAY
        - GOVPAY_DB_PASSWORD=GOVPAY
        - GOVPAY_ORACLE_JDBC_PATH=/tmp/ojdbc10.jar
        - GOVPAY_ORACLE_JDBC_URL_TYPE=servicename
        - GOVPAY_POP_DB_SKIP=false
        # il container oracle puo impiegare anche 20 minuti ad avviarsi
        - GOVPAY_LIVE_DB_CHECK_MAX_RETRY=120
        - GOVPAY_READY_DB_CHECK_MAX_RETRY=600
  database:
    container_name: or_govpay_${SHORT}
    image: container-registry.oracle.com/database/enterprise:19.3.0.0
    shm_size: 2g
    ulimits:
      nofile: 65536
    environment:
      - ORACLE_PDB=GOVPAYPDB
      - ORACLE_PWD=123456
    volumes:
       - ./ORADATA:/opt/oracle/oradata
       - ./oracle_startup:/opt/oracle/scripts/startup
    ports:
       - 1521:1521
EOYAML
    echo 
    echo "ATTENZIONE: Copiare il driver jdbc Oracle 'ojdbc10.jar' dentro la directory './compose/'"
    echo
    echo "ATTENZIONE: Copiare il driver jdbc Oracle 'ojdbc10.jar' dentro la directory './compose/'" > compose/README.first
  fi
fi
exit 0
