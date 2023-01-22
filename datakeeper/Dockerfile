FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/datakeeper.jar datakeeper/

ENTRYPOINT ["java", "-jar", "datakeeper/datakeeper.jar"]