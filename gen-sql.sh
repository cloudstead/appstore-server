#!/bin/bash

BASE=$(cd $(dirname $0) && pwd)
cd ${BASE}

outfile=${BASE}/cloudos-appstore.sql

${BASE}/../cloudos-lib/gen-sql.sh cloudos_appstore_test ${outfile}
