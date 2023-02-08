FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/dailyvolumesbot.jar dailyvolumesbot/

ENTRYPOINT ["java", "-jar", "-Xms256m", "-Xmx256m", "-XX:MaxMetaspaceSize=256m", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/tmp/logs", "dailyvolumesbot/dailyvolumesbot.jar"]