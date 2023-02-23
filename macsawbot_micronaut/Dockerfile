FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/macsawbot-all.jar .

ENTRYPOINT ["java", "-jar", \
"-Xms256m", "-Xmx256m", \
"-XX:MaxMetaspaceSize=256m", \
"-XX:+HeapDumpOnOutOfMemoryError", \
"-XX:HeapDumpPath=/tmp/logs", \
"-Dcom.sun.management.jmxremote", \
"-Dcom.sun.management.jmxremote.local.only=false", \
"-Dcom.sun.management.jmxremote.port=9103", \
"-Dcom.sun.management.jmxremote.rmi.port=9103", \
"-Dcom.sun.management.jmxremote.ssl=false", \
"-Djava.rmi.server.hostname=192.168.1.66", \
"-Dcom.sun.management.jmxremote.authenticate=false", \
"macsawbot-all.jar"]