FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/bigasscandlesbot.jar .
#ENV HOST_NAME = "192.168.1.66"

ENTRYPOINT ["java", "-jar", \
"-Xms300m", "-Xmx300m", \
"-XX:NewSize=200m", "-XX:MaxNewSize=200m", \
"-XX:MaxMetaspaceSize=100m", \
"-XX:+HeapDumpOnOutOfMemoryError", \
"-XX:HeapDumpPath=/tmp/logs", \
"-Dcom.sun.management.jmxremote", \
"-Dcom.sun.management.jmxremote.local.only=false", \
"-Dcom.sun.management.jmxremote.port=9104", \
"-Dcom.sun.management.jmxremote.rmi.port=9104", \
"-Dcom.sun.management.jmxremote.ssl=false", \
"-Djava.rmi.server.hostname=192.168.1.66", \
"-Dcom.sun.management.jmxremote.authenticate=false", \
"bigasscandlesbot.jar"]
