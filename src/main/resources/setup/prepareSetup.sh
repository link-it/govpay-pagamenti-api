#!/bin/bash

# L'installer produce il solo war govpay-api-pagamento per Tomcat
if [ -n "$1" ] && [ "$1" != "tomcat" ]; then
  echo "Parametro AS non valido [$1]: e' supportato solo 'tomcat'"
  exit 8
fi

VERSION=$(mvn -f ../../../../pom.xml -q -Dexec.executable=echo -Dexec.args='${project.version}' --non-recursive exec:exec)
echo "Creazione installer GovPay v.${VERSION}"
# Non e' piu' possibile eseguire la compilazione da qua poiche'
# il plugin di maven per la generazione dei bean da xsd non risolve
# correttamente i path relativi.

# mvn -f ../../../../pom.xml -Denv=installer_template clean package

# Directory
COPYING_FILE=../../../../COPYING
SQL=../../resources/db/sql/
DATASOURCE=../../resources/db/datasource/
DOC=../../resources/doc/pdf
GOVPAY_PAG=../../../../wars/api-pagamento/target/govpay-pagamenti-api.war
THIRD_PARTY_LICENSES=../../../../third-party-licenses


# Template
rm -rf core.template
cp -r core core.template
#find core.template/installer/setup/antinstall-config.xml -type f -exec perl -pi -e "s#PRODUCT_VERSION#${VERSION}#g" {} \;
perl -pi -e "s#PRODUCT_VERSION#${VERSION}#g" core.template/installer/setup/antinstall-config.xml

# Prepare SQL
echo "Prepare sql script ..."
mkdir -p core.template/installer/sql/
if [ ! -d "${SQL}" ]
then
	echo "Directory contenente gli script sql non esistente"
	exit 1
fi
cp -r ${SQL}/* core.template/installer/sql/
rm -f core.template/installer/sql/*.sql
rm -f core.template/installer/sql/*/delete.sql
rm -f core.template/installer/sql/*/drop.sql
echo "Prepare sql script [completed]"

# Prepare Datasource
echo "Prepare datasource ..."
mkdir -p core.template/installer/datasource
if [ ! -d "${DATASOURCE}" ]
then
	echo "Directory contenente i datasource non esistente"
	exit 2
fi
cp -r ${DATASOURCE} core.template/installer/
echo "Prepare datasource [completed]"

if [ ! -e "${COPYING_FILE}" ]
then
        echo "Copying file non esistente"
        exit 6
fi
cp ${COPYING_FILE} core.template/
cp ${COPYING_FILE} core.template/doc/

# readme
cp core.template/doc/README.txt core.template/

# librerie terze parti
cp -r ${THIRD_PARTY_LICENSES}/jar/* core.template/doc/
echo "Prepare doc [completed]"

# Prepare SOFTWARE
echo "Prepare archivi ..."
mkdir -p core.template/installer/archivi/

if [ ! -e "${GOVPAY_PAG}" ]; then
    echo "File WAR non trovato: ${GOVPAY_PAG}"
    exit 7
fi
cp "${GOVPAY_PAG}" core.template/installer/archivi/
echo "Copiato ${GOVPAY_PAG}"
echo "Prepare archivi [completed]"

echo "Creazione archivio compresso ..."
rm -rf target
mkdir target
mv core.template govpay-installer-${VERSION}
tar czf target/govpay-installer-${VERSION}.tgz govpay-installer-${VERSION}/
mv govpay-installer-${VERSION}/ target/
echo "Creazione archivio compresso [completed]"
