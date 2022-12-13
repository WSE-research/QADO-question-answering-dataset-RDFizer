FROM python:3.10-slim
WORKDIR /app
COPY . .
RUN pip3 install -r requirements.txt --no-dependencies
CMD ["python3", "createSpreadsheet.py"]
