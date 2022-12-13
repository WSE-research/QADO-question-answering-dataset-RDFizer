FROM python:3.8-slim-buster
WORKDIR /app
COPY . .
RUN pip3 install -r requirements.txt --no-dependencies
CMD ["python3", "createSpreadsheet.py"]
