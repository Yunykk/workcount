#!/bin/sh

##############################################################################
##
##  Gradle start up script for POSIX generated from a minimal wrapper stub.
##
##############################################################################

APP_HOME=$(cd "${0%/*}" && printf '%s\n' "$PWD")
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "Missing gradle/wrapper/gradle-wrapper.jar" >&2
  exit 1
fi

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
