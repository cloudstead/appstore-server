#!/bin/bash

BASE=$(cd $(dirname $0) && pwd)
cd ${BASE}

outfile=${BASE}/../cloudstead-apps/apps/cloudos-appstore/files/appstore-server.sql

${BASE}/../cloudos/cloudos-lib/gen-sql.sh cloudos_appstore_test ${outfile}
