#!/bin/bash
if [ "$1" != "" ]
then
	out_dir="$1"
else
	out_dir="/tmp/QADO-statistics"
fi

docker build --no-cache -t sparql-spreadsheet-creator:latest .
mkdir "$out_dir" > /dev/null 2>&1
echo "write statistics to $out_dir (change this through providing a directory as a parameter)"
docker run --rm -it -v $out_dir:/output/ sparql-spreadsheet-creator:latest
