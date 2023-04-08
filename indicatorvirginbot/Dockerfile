FROM resurfaceio/alpine-jdk17:3.17.0a
#FROM springci/graalvm-ce:java17-0.12.x

ADD ./build/libs/indicatorvirginbot.jar .
#ENV HOST_NAME = "192.168.1.66"

#ENTRYPOINT ["java", "-agentlib:native-image-agent=config-output-dir=/tmp/logs/native-image", "-jar", \
ENTRYPOINT ["java", "-jar", \
"-Xms256m", "-Xmx300m", \
"-XX:NewSize=164m", "-XX:MaxNewSize=164m", \
"-XX:MaxMetaspaceSize=100m", \
"-XX:+HeapDumpOnOutOfMemoryError", \
"-XX:HeapDumpPath=/tmp/logs", \
"-Dcom.sun.management.jmxremote", \
"-Dcom.sun.management.jmxremote.local.only=false", \
"-Dcom.sun.management.jmxremote.port=9102", \
"-Dcom.sun.management.jmxremote.rmi.port=9102", \
"-Dcom.sun.management.jmxremote.ssl=false", \
"-Djava.rmi.server.hostname=192.168.1.66", \
"-Dcom.sun.management.jmxremote.authenticate=false", \
"indicatorvirginbot.jar"]
