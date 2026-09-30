#!/bin/bash

pushd () {
    command pushd "$@" > /dev/null
}

popd () {
    command popd "$@" > /dev/null
}

# DEFAULTS

PAGAMENTI=basic,ssl
APIDEFAULT=none
GOVPAY_SRC_DIR="./"
GOVPAY_WARS_DIR="wars/"
GOVPAY_VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -pag|--pagamenti)
    PAGAMENTI="$2"
    shift # past argument
    shift # past value
    ;;
    -src|--sourcedir)
    GOVPAY_SRC_DIR="$2"
    shift # past argument
    shift # past value
    ;;
    -v|--version)
    GOVPAY_VERSION="$2"
    shift # past argument
    shift # past value
    ;;
    -d|--default)
    APIDEFAULT="$2"
    shift # past argument
    shift # past value
    ;;
    *)    # unknown option
    echo "Opzione non riconosciuta $1"
    echo "usage:"
    echo "   -pag <args> : lista di autenticazioni da abilitare sulle api di pagamento (spid,header,basic,ssl,hdrcert,public,session,ldap,apikey,oauth2). Default: basic,ssl"
    echo "   -d <args> : autenticazione da abilitare sui contesti senza autenticazione per retro-compatibilita (basic,ssl,hdrcert). Default: none"
    exit 2;
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

PAGAMENTI_BASIC_GP=false
PAGAMENTI_BASIC_LDAP=false
[[ $PAGAMENTI == *"basic"* ]] && { PAGAMENTI_BASIC_GP=true; PAGAMENTI_BASIC_LDAP=false; }
[[ $PAGAMENTI == *"ldap"* ]] && { PAGAMENTI_BASIC_GP=false; PAGAMENTI_BASIC_LDAP=true; }
[[ $PAGAMENTI == *"ssl"* ]] && PAGAMENTI_SSL=true || PAGAMENTI_SSL=false
[[ $PAGAMENTI == *"hdrcert"* ]] && PAGAMENTI_SSL_HEADER=true || PAGAMENTI_SSL_HEADER=false
[[ $PAGAMENTI == *"header"* ]] && PAGAMENTI_HEADER=true || PAGAMENTI_HEADER=false
[[ $PAGAMENTI == *"spid"* ]] && PAGAMENTI_SPID=true || PAGAMENTI_SPID=false
[[ $PAGAMENTI == *"public"* ]] && PAGAMENTI_PUBLIC=true || PAGAMENTI_PUBLIC=false
[[ $PAGAMENTI == *"session"* ]] && PAGAMENTI_SESSION=true || PAGAMENTI_SESSION=false
[[ $PAGAMENTI == *"apikey"* ]] && PAGAMENTI_API_KEY=true || PAGAMENTI_API_KEY=false
[[ $PAGAMENTI == *"oauth2"* ]] && PAGAMENTI_OAUTH2=true || PAGAMENTI_OAUTH2=false

DEFAULT_BASIC=false
DEFAULT_SSL=false
[[ $APIDEFAULT == *"basic"* ]] && DEFAULT_BASIC=true || DEFAULT_BASIC=false
[[ $APIDEFAULT == *"ssl"* ]] && DEFAULT_SSL=true || DEFAULT_SSL=false

# il blocco DEFAULT_BASIC ridefinisce i bean del blocco BASIC_GOVPAY_PROVIDER (govpayPasswordEncoder, authenticationManager)
if $DEFAULT_BASIC && $PAGAMENTI_BASIC_GP
then
  echo "L'opzione '-d basic' non e' compatibile con l'autenticazione basic sulle api di pagamento: rimuovere 'basic' da -pag.";
  exit 3;
fi



GOVPAY_WAR_NAME="govpay-pagamenti-api.war"
GOVPAY_WORK_DIR="govpay_tmp"
GOVPAY_TMP_DIR="war_tmp"
APP_CONTEXT_BASE_DIR="WEB-INF"
CONTEXT_SECURITY_XML_SUFFIX="applicationContext-security.xml"
TARGET_DIR="/target/"

rm -rf $GOVPAY_WORK_DIR
mkdir $GOVPAY_WORK_DIR

# API-Pagamento
API_TARGET_DIR="api-pagamento"$TARGET_DIR

cp $GOVPAY_SRC_DIR$GOVPAY_WARS_DIR$API_TARGET_DIR$GOVPAY_WAR_NAME $GOVPAY_WORK_DIR
pushd $GOVPAY_WORK_DIR

API_PREFIX="api-pagamento-"
unzip -q $GOVPAY_WAR_NAME $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX

if ! $PAGAMENTI_BASIC_GP
then
  echo "API-Pagamenti disabilitazione autenticazione basic govpay...";
  sed -i -e "s#BASIC_GOVPAY_PROVIDER_START -->#BASIC_GOVPAY_PROVIDER_START#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#<!-- BASIC_GOVPAY_PROVIDER_END#BASIC_GOVPAY_PROVIDER_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamenti disabilitazione autenticazione basic govpay completata.";
fi

if $PAGAMENTI_BASIC_LDAP
then
  echo "API-Pagamenti abilitazione autenticazione basic ldap...";
  sed -i -e "s# BASIC_LDAP_PROVIDER_START# BASIC_LDAP_PROVIDER_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s# BASIC_LDAP_PROVIDER_END# <!-- BASIC_LDAP_PROVIDER_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamenti abilitazione basic ldap completata.";
fi

if ! $PAGAMENTI_SSL
then
  echo "API-Pagamenti disabilitazione autenticazione ssl...";
  sed -i -e "s#SSL_START -->#SSL_START#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#<!-- SSL_END#SSL_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamenti disabilitazione ssl completata.";
fi

if $PAGAMENTI_SSL_HEADER
then
  echo "API-Pagamenti abilitazione autenticazione hdrcert...";
  sed -i -e "s#SSL_HDR_START#SSL_HDR_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#SSL_HDR_END#<!-- SSL_HDR_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamenti abilitazione hdrcert completata.";
fi

if $PAGAMENTI_SPID
then
  echo "API-Pagamento abilitazione autenticazione SPID...";
  sed -i -e "s#SPID_START#SPID_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#SPID_END#<!-- SPID_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione autenticazione SPID completata.";
fi
if $PAGAMENTI_SESSION
then
  echo "API-Pagamento abilitazione autenticazione Session...";
  sed -i -e "s#SESSION_START#SESSION_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#SESSION_END#<!-- SESSION_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione autenticazione Session completata.";
fi
if $PAGAMENTI_HEADER
then
  echo "API-Pagamento abilitazione HTTP Header-auth ...";
  sed -i -e "s#HEADER_START#HEADER_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#HEADER_END#<!-- HEADER_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione HTTP Header-auth  completata.";
fi
if $PAGAMENTI_PUBLIC
then
  echo "API-Pagamento abilitazione pagamenti in forma anonima...";
  sed -i -e "s#PUBLIC_START#PUBLIC_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#PUBLIC_END#<!-- PUBLIC_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione pagamenti in forma anonima completata.";
fi
if $PAGAMENTI_API_KEY
then
  echo "API-Pagamento abilitazione ApiKey auth...";
  sed -i -e "s#API_KEY_START#API_KEY_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#API_KEY_END#<!-- API_KEY_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione ApiKey auth completata.";
fi
if $PAGAMENTI_OAUTH2
then
  echo "API-Pagamento abilitazione Oauth2 auth...";
  sed -i -e "s#OAUTH2_START#OAUTH2_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#OAUTH2_END#<!-- OAUTH2_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione Oauth2 auth completata.";
fi
if $DEFAULT_BASIC
then
  echo "API-Pagamento abilitazione default HTTP BASIC ...";
  sed -i -e "s#DEFAULT_BASIC_GOVPAY_PROVIDER_START#DEFAULT_BASIC_GOVPAY_PROVIDER_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#DEFAULT_BASIC_GOVPAY_PROVIDER_END#<!-- DEFAULT_BASIC_GOVPAY_PROVIDER_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione default HTTP BASIC completata.";
fi
if $DEFAULT_SSL
then
  echo "API-Pagamento abilitazione default SSL ...";
  sed -i -e "s#DEFAULT_SSL_START#DEFAULT_SSL_START -->#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  sed -i -e "s#DEFAULT_SSL_END#<!-- DEFAULT_SSL_END#g" $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
  echo "API-Pagamento abilitazione default SSL completata.";
fi

zip -qr $GOVPAY_WAR_NAME $APP_CONTEXT_BASE_DIR/$API_PREFIX$CONTEXT_SECURITY_XML_SUFFIX
rm -rf $APP_CONTEXT_BASE_DIR

# copio il war aggiornato nella posizione originale
popd
cp $GOVPAY_WORK_DIR/$GOVPAY_WAR_NAME $GOVPAY_SRC_DIR$GOVPAY_WARS_DIR$API_TARGET_DIR$GOVPAY_WAR_NAME

# rimozione directory di lavoro
rm -rf $GOVPAY_WORK_DIR

echo "Configurazione $GOVPAY_WAR_NAME completata";
