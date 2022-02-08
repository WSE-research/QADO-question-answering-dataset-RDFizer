FROM gradle:latest
COPY . .
RUN gradle build
CMD gradle run