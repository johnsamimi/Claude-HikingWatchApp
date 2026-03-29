#!/bin/sh
APP_HOME=$(cd "$(dirname "$0")" && pwd)
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
JAVACMD=${JAVA_HOME:+$JAVA_HOME/bin/}java
exec "$JAVACMD" $DEFAULT_JVM_OPTS "-Dorg.gradle.appname=$(basename $0)" \
    -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
