FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/indicatorvirginbot.jar indicatorvirginbot/

ENTRYPOINT ["java", "-jar", "indicatorvirginbot/indicatorvirginbot.jar"]