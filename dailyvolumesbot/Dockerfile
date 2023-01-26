FROM resurfaceio/alpine-jdk17:3.17.0a

ADD ./build/libs/dailyvolumesbot.jar dailyvolumesbot/

ENTRYPOINT ["java", "-jar", "dailyvolumesbot/dailyvolumesbot.jar"]