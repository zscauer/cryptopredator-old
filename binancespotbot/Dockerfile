FROM openjdk:17-alpine

WORKDIR /binancespotbot
ADD build/libs/binancespotbot.jar .

ENTRYPOINT ["java", "-jar", "/binancespotbot/binancespotbot.jar"]