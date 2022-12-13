#!/bin/bash
docker build -t sparql-spreadsheet-creator:latest .
docker run --rm -it sparql-spreadsheet-creator:latest