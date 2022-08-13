FROM openjdk:17-alpine

WORKDIR /binancespotbot
ADD target/binancespotbot.jar .

ENTRYPOINT ["java", "-jar", "/binancespotbot/binancespotbot.jar"]