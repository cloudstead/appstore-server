#!/bin/bash

BASE=$(cd $(dirname $0) && pwd)
cd ${BASE}

outfile=${BASE}/../cloudstead-apps/apps/cloudos-appstore/files/cloudos-appstore.sql

SILENT="${1}"
if [ ! -z "${SILENT}" ] ; then
    ${BASE}/../cloudos/cloudos-lib/gen-sql.sh cloudos_appstore_test ${outfile} 1> /dev/null 2> /dev/null
else
    ${BASE}/../cloudos/cloudos-lib/gen-sql.sh cloudos_appstore_test ${outfile}
fi