#!/bin/sh

APP_HOME=$( cd "$( dirname "$0" )" && pwd )
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
JAVACMD="$JAVA_HOME/bin/java"

exec "$JAVACMD" \
    -Xmx64m -Xms64m \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
