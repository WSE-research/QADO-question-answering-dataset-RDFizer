FROM gradle:latest
COPY . .
RUN ./gradlew build
CMD ./gradlew run