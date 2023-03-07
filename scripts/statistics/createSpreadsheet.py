import os.path
from datetime import date
import xlsxwriter
import requests
from json import loads, load
import numpy as np
from config import *
from typing import Any


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


def get_sheet_config(sparql_path: str):
    """
    Generates the JSON payload for creating a spreadsheet table via the Google Sheets API

    :param sparql_path: file name of the SPARQL query file in 'queries' subdirectory
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

                # foreach dataset
                for response in sparql_result['results']['bindings']:
                    values = loads(f'[{response["concat"]["value"]}]')

                    # any property found to calculate quantiles
                    if values:
                        # generate all quantiles needed for a boxplot
                        for box_plot_value, quantile in zip(box_plot_values, [0, .25, .5, .75, 1]):
                            response[box_plot_value] = {}
                            response[box_plot_value]['value'] = str(np.quantile(values, quantile))
                    # no data provided for the dataset
                    else:
                        # set all quantiles to 0
                        for box_plot_value in box_plot_values:
                            response[box_plot_value] = {'value': '0'}

            # get variable names
            header = [entry for entry in sparql_result['head']['vars']]

            data: list[Any] = [[head] for head in header]

            # add results as new rows
            for response in sparql_result['results']['bindings']:
                # foreach column
                for i, head in enumerate(header):
                    # read value and replace URI data from benchmark names
                    value = response[head]['value'] if head in response else 'URI'
                    value = value.replace('urn:qado#', '').replace('-dataset', '')

                    data[i].append(value)
        # SPARQL query failed
        else:
            print(resp.text)

    return sparql_path.replace('.sparql', '').replace('_boxplot', ''), data


def main():
    """
    Creates a new Google Spreadsheet with Stardog data
    """
    # get all sparql queries
    queries = sorted(os.listdir('queries'))
    today = date.today()

    if 'output' not in os.listdir():
        os.mkdir('output')

    workbook = xlsxwriter.Workbook(f'output/QADO-statistics-{today}.xlsx')

    print('Creating sheets...')
    sheets = [get_sheet_config(sparql_query) for sparql_query in queries]

    for title, sheet in sheets:
        worksheet = workbook.add_worksheet(title[:31])
        worksheet.write(0, 0, title)

        for col, col_values in enumerate(sheet):
            for row, row_value in enumerate(col_values):
                worksheet.write(row + 1, col, row_value)

        worksheet.autofit()

    workbook.close()


if __name__ == '__main__':
    main()
