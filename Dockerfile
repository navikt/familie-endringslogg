FROM navikt/java:17
COPY ./build/libs/*all.jar "app.jar"
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
