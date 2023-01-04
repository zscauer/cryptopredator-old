FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/binancespotbot.jar binancespotbot/

ENTRYPOINT ["java", "-jar", "binancespotbot/binancespotbot.jar"]