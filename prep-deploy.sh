#!/bin/bash

DEPLOY=${1}
if [ -z ${DEPLOY} ] ; then
  echo "Usage $0 <deploy-dir>"
  exit 1
fi

# Nothing special to do for now