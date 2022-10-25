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
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sb
from collections import defaultdict
import logging

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


def load_image_data(image_file) -> dict | None:
    """
    Read the image configuration for 'images' subdirectory

    :param image_file: file name in 'images' subdirectory
    :return: configuration as JSON object
    """
    image_path = f'images/{image_file}'

    if os.path.exists(image_path):
        with open(image_path) as f:
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

            # query relates to boxplot data
            if 'concat' in (header := sparql_result['head']['vars']):
                # set boxplot properties
                box_plot_values = ['min', '25% quantile', 'median', '75% quantile', 'max']

                header.remove('concat')
                header += box_plot_values

                dataframe_dump = defaultdict(list)

                # foreach dataset
                for response in sparql_result['results']['bindings']:
                    benchmark = response['benchmark']['value'].replace('-dataset', '').replace('urn:qa:benchmark#', '')

                    try:
                        lang = response['lang']['value']
                    except KeyError:
                        logging.info(f'No language provided for {sparql_path} {benchmark}')
                        lang = None

                    values = loads(f'[{response["concat"]["value"]}]')

                    # any property found to calculate quantiles
                    if values:
                        for value in values:
                            dataframe_dump['benchmark'].append(benchmark)
                            dataframe_dump['value'].append(value)

                            if lang:
                                dataframe_dump['lang'].append(lang)

                        # generate all quantiles needed for a boxplot
                        for box_plot_value, quantile in zip(box_plot_values, [0, .25, .5, .75, 1]):
                            response[box_plot_value] = {}
                            response[box_plot_value]['value'] = str(np.quantile(values, quantile))
                    # no data provided for the dataset
                    else:
                        # set all quantiles to 0
                        for box_plot_value in box_plot_values:
                            response[box_plot_value] = {'value': '0'}

                plt.figure(figsize=(40, 10))
                plt.title(sparql_path.replace('.sparql', '').replace('_boxplot', ''))
                sb.violinplot(data=pd.DataFrame(dataframe_dump), x='benchmark', y='value', hue='lang' if lang else None)
                plt.xticks(rotation=90)
                plt.tight_layout()
                plt.ylabel('Question length')
                plt.savefig(f'images/{sparql_path.replace(".sparql", ".png")}', dpi=200)
                plt.close()

            # query relates to SPARQL query statistics
            elif 'queries' in header:
                header.remove('queries')

                # generate headers for SPARQL query statistics
                for prefix in ['so', 'p', 'spo']:
                    box_plot_values = [f'{prefix}_min', f'{prefix}_25% quantile', f'{prefix}_median',
                                       f'{prefix}_75% quantile', f'{prefix}_max']

                    header += box_plot_values

                dataframe_dump = defaultdict(list)

                # foreach dataset
                for answer in sparql_result['results']['bindings']:
                    # get SPARQL queries for the dataset
                    queries = answer['queries']['value'].split('<||>')

                    benchmark = answer['benchmark']['value'].replace('-dataset', '').replace('urn:qa:benchmark#', '')

                    try:
                        lang = answer['lang']['value']
                    except KeyError:
                        lang = None
                        logging.info(f'No language provided for {sparql_path} {benchmark}')

                    subject_object_counts = []
                    predicate_counts = []
                    subject_predicate_object_counts = []

                    for query in queries:
                        lines = query.split('\n')

                        subject_object_set = set()
                        predicate_set = set()

                        # foreach line of a SPARQL query
                        for line in lines:
                            # current line contains usage of subjects/properties/objects
                            if line.strip().endswith(('.', ',', ';')):
                                elements = line.strip().split(' ')

                                # line defines new subject, property and object
                                if line.strip().endswith('.'):
                                    # add them if they aren't a variable
                                    if not elements[0].startswith('?'):
                                        subject_object_set.add(elements[0])
                                    if not elements[1].startswith('?'):
                                        predicate_set.add(elements[1])
                                    if not elements[2].startswith('?'):
                                        subject_object_set.add(elements[2])
                                # line defines new property and object
                                elif line.strip().endswith(';'):
                                    # add them if they aren't a variable
                                    if not elements[0].startswith('?'):
                                        predicate_set.add(elements[0])
                                    if not elements[1].startswith('?'):
                                        subject_object_set.add(elements[1])
                                # line defines new object
                                else:
                                    # add object if it isn't a variable
                                    if not elements[0].startswith('?'):
                                        subject_object_set.add(elements[0])

                        number_of_subjects_objects = len(subject_object_set)
                        number_of_predicates = len(predicate_set)
                        number_of_resources = len(subject_object_set) + len(predicate_set)

                        # count number of subjects/properties/objects
                        subject_object_counts.append(number_of_subjects_objects)
                        predicate_counts.append(number_of_predicates)
                        subject_predicate_object_counts.append(number_of_resources)

                        dataframe_dump['benchmark'].append(benchmark)
                        dataframe_dump['subjects/objects'].append(number_of_subjects_objects)
                        dataframe_dump['properties'].append(number_of_predicates)
                        dataframe_dump['resources'].append(number_of_resources)

                        if lang:
                            dataframe_dump['language'].append(lang)

                    # generate quantiles for subjects/objects, properties and all resources
                    for prefix, entity_sets in zip(['so', 'p', 'spo'], [subject_object_counts, predicate_counts,
                                                                        subject_predicate_object_counts]):
                        for key, quantile in zip([f'{prefix}_min', f'{prefix}_25% quantile', f'{prefix}_median',
                                                  f'{prefix}_75% quantile', f'{prefix}_max'], [0, .25, .5, .75, 1]):
                            answer[key] = {'value': str(np.quantile(entity_sets, quantile))}

                    dataframe = pd.DataFrame(dataframe_dump)

                    for column in ['subjects/objects', 'properties', 'resources']:
                        plt.figure(figsize=(40, 10))
                        plt.title(sparql_path.replace('.sparql', '').replace('_boxplot', ''))
                        sb.violinplot(data=dataframe, x='benchmark', y=column, hue='lang' if lang else None)
                        plt.xticks(rotation=90)
                        plt.tight_layout()
                        plt.savefig(f'images/{sparql_path.replace(".sparql", "")}-{column.replace("/", "-")}.png',
                                    dpi=200)
                        plt.close()

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
