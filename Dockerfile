FROM openjdk:8-jdk-alpine

COPY target/*.jar worknet.jar

ENTRYPOINT ["java","-jar","/worknet.jar"]