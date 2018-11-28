-rw-r--r--   1 Chris  staff    794 22 Oct 11:35 .classpath
FROM gradle:jdk8 as builder

COPY . /home/gradle/src
USER root
RUN chown -R gradle:gradle /home/gradle/src
USER gradle

WORKDIR /home/gradle/src
RUN gradle assemble

FROM openjdk:8-jre-alpine

COPY --from=builder /home/gradle/src/build/libs/sscs-bulk-scan.jar /opt/app/

WORKDIR /opt/app

HEALTHCHECK NONE

EXPOSE 8080

ENTRYPOINT ["/usr/bin/java", "-jar", "/opt/app/sscs-bulk-scan.jar"]
