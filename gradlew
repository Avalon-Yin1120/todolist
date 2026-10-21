#!/bin/sh
# Gradle wrapper 启动脚本（手写精简版）
APP_HOME=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_LAUNCHER=org.gradle.wrapper.GradleWrapperMain

if [ ! -f "$WRAPPER_JAR" ]; then
  echo "[ERROR] 缺少 gradle/wrapper/gradle-wrapper.jar" >&2
  exit 1
fi

if [ -z "$JAVA_HOME" ]; then
  JAVA_HOME="D:/Download/java"
fi
JAVA_EXE="$JAVA_HOME/bin/java"
[ -x "$JAVA_EXE" ] || JAVA_EXE=java

exec "$JAVA_EXE" -Dorg.gradle.appname=gradlew -classpath "$WRAPPER_JAR" "$WRAPPER_LAUNCHER" "$@"
