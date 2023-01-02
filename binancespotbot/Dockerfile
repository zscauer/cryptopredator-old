FROM resurfaceio/alpine-jdk17:3.17.0a

#WORKDIR /binancespotbot
ADD ./build/libs/binancespotbot.jar /binancespotbot/

ENTRYPOINT ["java", "-jar", "/binancespotbot/binancespotbot.jar"]