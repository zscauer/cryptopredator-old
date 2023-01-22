FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/binancedatakeeper.jar binancedatakeeper/

ENTRYPOINT ["java", "-jar", "binancedatakeeper/binancedatakeeper.jar"]