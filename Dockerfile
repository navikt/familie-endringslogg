FROM navikt/java:21
COPY ./build/libs/*all.jar "app.jar"
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
