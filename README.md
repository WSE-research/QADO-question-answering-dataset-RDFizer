# question-answering-dataset-RDFizer
This tool provides an RMI service to convert structured data into RDF.

## Setup service
To run the service locally run `./gradlew run`. Otherwise, you can set up a Docker container
running the service by executing `docker build -t rdfizer:latest` and 
`docker run -p "$EXTERNAL_PORT:20000" rdfizer:latest`. The registry of the RMI server is
listing on port 20000 by default.

## Accessing service
A reference implementation of a client accessing the service is provided in `Client.kt`.
By default the following templates are supported:
* QALD
