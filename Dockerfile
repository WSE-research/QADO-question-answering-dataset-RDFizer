FROM gradle:7.6 AS build
COPY app/ app/
WORKDIR /home/gradle/app
RUN gradle installDist

FROM eclipse-temurin:17-jre
COPY --from=build /home/gradle/app/build/install/app /rdfizer
WORKDIR /rdfizer/bin
COPY app/ontology ./ontology
COPY app/mappings ./mappings
COPY app/styles.css .
COPY app/api_call.js .
CMD ./app