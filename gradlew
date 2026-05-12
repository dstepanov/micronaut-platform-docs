#!/bin/sh

APP_PATH=$0

while
    APP_HOME=${APP_PATH%"${APP_PATH##*/}"}
    [ -h "$APP_PATH" ]
do
    LINK=$(ls -ld "$APP_PATH")
    LINK=${LINK#*' -> '}
    case $LINK in
        /*) APP_PATH=$LINK ;;
        *) APP_PATH=$APP_HOME$LINK ;;
    esac
done

APP_HOME=$(cd "${APP_HOME:-./}" >/dev/null && pwd -P) || exit

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

set -- \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"

eval "set -- $DEFAULT_JVM_OPTS \"\$@\""
exec java "$@"
