FROM gradle:latest as builder
COPY ./ ./
RUN gradle build

FROM openjdk:latest
COPY --from=builder /home/gradle/app/build/ /build/
CMD ./build/scripts/app
