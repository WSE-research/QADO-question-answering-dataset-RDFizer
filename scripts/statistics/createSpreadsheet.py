import os.path
from datetime import date

import requests
from json import loads, load
import numpy as np

from config import *

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

# API scope for Google Sheets
scopes = ['https://www.googleapis.com/auth/spreadsheets']


def load_chart(chart_file):
    """
    Loading the configuration file of a chart from filesystem

    :param chart_file: config file name in 'charts' subdirectory
    :raises FileNotFoundError: config file doesn't exist
    :return: configuration as JSON object
    """
    if not os.path.exists(f'charts/{chart_file}'):
        raise FileNotFoundError(f'Chart for {chart_file} not found')

    with open(f'charts/{chart_file}') as f:
        return load(f)


def build_stardog_api_url():
    """
    Create the stardog API endpoint from configuration file

    :return: HTTP(S) link to stardog endpoint
    """
    base_url = stardog_host.replace('://', f'://{stardog_username}:{stardog_password}@').rstrip('/')

    return f'{base_url}:{stardog_port}/{stardog_db}/query'


def get_sheet_config(sparql_path: str, number: int):
    """
    Generates the JSON payload for creating a spreadsheet table via the Google Sheets API

    :param sparql_path: file name of the SPARQL query file in 'queries' subdirectory
    :param number: sheet id
    :return: JSON payload for creating a table with the response of the SPARQL query
    """
    # read query
    with open(f'queries/{sparql_path}') as f:
        query = f.read()

        # run SPARQL query on Stardog endpoint
        resp = requests.post(build_stardog_api_url(), data=query, headers={
            'Content-Type': 'application/sparql-query',
            'Accept': 'application/sparql-results+json'
        })

        data = {}

        # SPARQL query execution successful
        if resp.ok:
            # load SPARQLResult
            sparql_result = loads(resp.text)

            if 'concat' in sparql_result['head']['vars']:
                box_plot_values = ['min', '25% quantile', 'median', '75% quantile', 'max']

                sparql_result['head']['vars'].remove('concat')
                sparql_result['head']['vars'] += box_plot_values

                for response in sparql_result['results']['bindings']:
                    values = loads(f'[{response["concat"]["value"]}]')

                    for box_plot_value, quantile in zip(box_plot_values, [0, .25, .5, .75, 1]):
                        response[box_plot_value] = {}
                        response[box_plot_value]['value'] = str(np.quantile(values, quantile))

            # get variable names
            header = [entry for entry in sparql_result['head']['vars']]

            # create header row
            data['startRow'] = 0
            data['startColumn'] = 0
            data['rowData'] = [
                {
                    'values': [{'userEnteredValue': {'stringValue': head}} for head in header]
                }
            ]

            # add results as new rows
            for response in sparql_result['results']['bindings']:
                next_row: dict = {'values': []}

                # foreach column
                for head in header:
                    # read value and replace URI data from benchmark names
                    value = response[head]['value'] if head in response else 'URI'
                    value = value.replace('urn:qa:benchmark#', '').replace('-dataset', '')

                    try:
                        value = float(value)
                        value_key = 'numberValue'
                    except ValueError:
                        value_key = 'stringValue'

                    next_row['values'].append({'userEnteredValue': {value_key: value}})

                # add new row to data payload
                data['rowData'].append(next_row)
        # SPARQL query failed
        else:
            print(resp.text)

    return {
        'properties': {
            'title': sparql_path.replace('.sparql', '').replace('_boxplot', ''),
            'sheetId': number
        },
        'data': [
            data
        ]
    }


def main():
    """
    Creates a new Google Spreadsheet with Stardog data
    """
    # get all sparql queries
    queries = sorted(os.listdir('queries'))

    creds = None

    today = date.today()

    # already logged in
    if os.path.exists('token.json'):
        # load credentials
        creds = Credentials.from_authorized_user_file('token.json', scopes)

    # not logged in or credentials invalid
    if not creds or not creds.valid:
        # credentials expired, refresh it
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        # request new token
        else:
            flow = InstalledAppFlow.from_client_secrets_file('credentials.json', scopes)
            creds = flow.run_local_server(port=0)

        # Save the credentials for the next run
        with open('token.json', 'w') as token:
            token.write(creds.to_json())

    try:
        # connect to API
        service = build('sheets', 'v4', credentials=creds)

        # creating new Spreadsheet with Stardog data
        print('Creating sheets...')
        spread_sheet = service.spreadsheets().create(body={
            'properties': {
                'title': f'QA Benchmark Statistics {today}',
                'locale': 'en'
            },
            'sheets': [get_sheet_config(sparql_query, count) for count, sparql_query in enumerate(queries)]
        }, fields='spreadsheetId').execute()

        spread_sheet_id = spread_sheet.get('spreadsheetId')

        # resize all columns based on content
        print('Reformatting sheets...')
        service.spreadsheets().batchUpdate(spreadsheetId=spread_sheet_id, body={
            'requests': [
                {
                    'autoResizeDimensions': {
                        'dimensions': {
                            'sheetId': i,
                            'dimension': 'COLUMNS',
                            'startIndex': 0,
                            'endIndex': 100
                        }
                    }
                } for i in range(len(queries))
            ]
        }).execute()

        # add all charts to spreadsheet
        print('Adding charts...')
        service.spreadsheets().batchUpdate(spreadsheetId=spread_sheet_id, body={
            'requests': [{'addChart': {'chart': load_chart(chart_file)}} for chart_file in os.listdir('charts')]
        }).execute()
    # any API call fails
    except HttpError as err:
        print(err)


if __name__ == '__main__':
    main()
