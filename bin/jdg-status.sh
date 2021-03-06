#!/bin/sh
BASEDIR=$(dirname $0)
case "`uname`" in
  CYGWIN*) BASEDIR=`cygpath -w "${BASEDIR}"`
           ;;
esac

if [ "x${JDG_HOME}" == "x" ]; then
    echo "Error: Please set environment variable JDG_HOME."
else

CLASSPATH=${JDG_HOME}/bin/client/jboss-cli-client.jar

jrunscript -cp "$CLASSPATH" -f "${BASEDIR}/cachecontrol.js" "${BASEDIR}/cachecontrol.config" status

fi
