#FROM resurfaceio/alpine-jdk17:3.17.0a
FROM springci/graalvm-ce:java17-0.12.x

ADD ./build/libs/statekeeper.jar .

ENTRYPOINT ["java", "-agentlib:native-image-agent=config-output-dir=/tmp/logs/native-image", "-jar", \
"-Xms256m", "-Xmx256m", \
"-XX:NewSize=128m", "-XX:MaxNewSize=128m", \
"-XX:MaxMetaspaceSize=100m", \
"-XX:+HeapDumpOnOutOfMemoryError", \
"-XX:HeapDumpPath=/tmp/logs", \
"-Dcom.sun.management.jmxremote", \
"-Dcom.sun.management.jmxremote.local.only=false", \
"-Dcom.sun.management.jmxremote.port=9100", \
"-Dcom.sun.management.jmxremote.rmi.port=9100", \
"-Dcom.sun.management.jmxremote.ssl=false", \
"-Djava.rmi.server.hostname=192.168.1.66", \
"-Dcom.sun.management.jmxremote.authenticate=false", \
"statekeeper.jar"]