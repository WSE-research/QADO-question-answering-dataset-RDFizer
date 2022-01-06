# question-answering-dataset-RDFizer
This tool provides a webservice to convert structured data into RDF.

## Setup service
To run the service locally run `./gradlew run`. Otherwise, you can set up a Docker container
running the service by executing `docker build -t rdfizer:latest .` and 
`docker run -d -p "$EXTERNAL_PORT:8080" rdfizer:latest`.

## Accessing service
To transform a JSON file to RDF perform a `POST`-Request at `$HOST:$PORT/json2rdf` with a JSON
payload of the following structure:

```json
{
  "format": "Mapping file name",
  "label": "Name for the generated RDF triples",
  "filePath": "URL of the JSON file",
  "homepage": "URL of the data publisher"
}
```

By default, the following formats are supported:
* QALD (`qald`)
* LC-QuAD (`lc-quad`)
* RuBQ (`rubq`)
* CWQ (`cwq`)