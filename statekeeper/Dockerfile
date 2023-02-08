FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/statekeeper.jar statekeeper/

ENTRYPOINT ["java", "-jar", "-Xms256m", "-Xmx256m", "-XX:MaxMetaspaceSize=256m", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/tmp/logs", "statekeeper/statekeeper.jar"]