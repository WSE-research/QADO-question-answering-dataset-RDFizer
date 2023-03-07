#!/bin/bash
docker build -t sparql-spreadsheet-creator:latest .
mkdir ~/QADO-statistics > /dev/null 2>&1
docker run --rm -it -v ~/QADO-statistics/:/output/ sparql-spreadsheet-creator:latest