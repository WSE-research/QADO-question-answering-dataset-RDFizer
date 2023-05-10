import os.path
from datetime import date
from json import loads
import numpy as np
from collections import defaultdict
from config import triplestore_endpoint, user, password, request_method
import pandas as pd
from SPARQLWrapper import SPARQLWrapper, JSON


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
        sparql = SPARQLWrapper(triplestore_endpoint)
        sparql.setCredentials(user, password)
        sparql.setMethod(request_method)
        sparql.setReturnFormat(JSON)
        sparql.setQuery(query)

        sparql_result = sparql.queryAndConvert()

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

        data = defaultdict(list)

        # add results as new rows
        for response in sparql_result['results']['bindings']:
            # foreach column
            for head in header:
                # read value and replace URI data from benchmark names
                value = response[head]['value'] if head in response else 'URI'
                value = value.replace('urn:qado#', '').replace('-dataset', '')

                data[head].append(value)

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

    print('Creating sheets...')
    sheets = [get_sheet_config(sparql_query) for sparql_query in queries]

    for title, sheet in sheets:
        statistic = pd.DataFrame(sheet)
        statistic.to_csv(f'output/QADO-statistics-{title}-{today}.csv', index=False)


if __name__ == '__main__':
    main()
