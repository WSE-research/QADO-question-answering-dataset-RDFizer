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
* LC-QuAD (`lc-quad` or `lc-quad-2`)
* RuBQ (`rubq`)
* CWQ (`cwq`)

The follow cURL command can be used to convert a JSON file of the QALD benchmark
into RDF.

`curl --location --request POST 'http://$HOST:$PORT/json2rdf' \
--header 'Content-Type: application/json' \
--data-raw '{
    "filePath": "https://github.com/ag-sc/QALD/raw/master/6/data/qald-6-train-multilingual-raw.json",
    "format": "qald",
    "label": "QALD 6 train multilingual raw",
    "homepage": "https://github.com/ag-sc/QALD"
}'`

## Adding additional formats
To add new mapping rules just add a new mapping file `NAME.ttl` to `app/mappings` while `NAME` has to be in
all caps. The mapping language is RML (https://rml.io/specs/rml/). To use the file within the webservice just
use the base file name as the `format` parameter.
