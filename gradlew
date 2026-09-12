#!/bin/sh
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
if [ -z "$DIRNAME" ]; then
  DIRNAME=.
fi
APP_HOME=`cd "$DIRNAME" && pwd`
DEFAULT_JVM_OPTS=""
JAVACMD="java"
which java >/dev/null 2>&1 || {
  echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH." >&2
  exit 1
}
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
exec "$JAVACMD" $DEFAULT_JVM_OPTS -jar "$CLASSPATH" "$@"
