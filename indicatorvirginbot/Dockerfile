FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/indicatorvirginbot.jar indicatorvirginbot/

ENTRYPOINT ["java", "-jar", "-Xms128m", "-Xmx256m", "-XX:MaxMetaspaceSize=256m", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/tmp/logs", "indicatorvirginbot/indicatorvirginbot.jar"]