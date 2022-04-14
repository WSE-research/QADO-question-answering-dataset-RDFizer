FROM gradle:latest
COPY . .
CMD ./gradlew run
