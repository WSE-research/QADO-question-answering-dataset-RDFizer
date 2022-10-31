# SPARQL dataset statistics
This directory provides a tool to create a Google Spreadsheet with statistics from 
your Stardog Triplestore. To use it run `pip install -r requirements.txt` to install
all packages. **Python 3.8** or newer is required. Run the `createSpreadsheet.py`
script to generate your statistics. In your Google Drive a new subdirectory
`SPARQL Statistics CURRENT_DATE` is created with the Spreadsheet and optional violin
plots

## Configuration
### Google Cloud Console
To use the tool it is required to configure a Google Cloud Console project. You can use
the preconfigured credentials, but therefore it is currently necessary to ask for 
access at the project maintainers. Alternatively you can [create your own Google Cloud
Console project](https://developers.google.com/workspace/guides/get-started?hl=en) 
and replace the `credentials.json` with your setup. The tool needs 
read-and-write-permissions for the *Google Sheets API* and the *Google Drive API*.

### Script configuration
Edit the `config.py` to select your Stardog Triplestore and configure the display size
of the violin plots in your spreadsheet.

### Statistics
Create your SPARQL queries and store them into the `queries` directory. The tool
creates a new worksheet foreach SPARQL query file.

#### Boxplot data
If you want to generate a boxplot, you need to store your query with the file
ending `_boxplot.sparql`. Additionally, the data for the boxplot needs to be
selected via `(GROUP_CONCAT(?YOUR_VARIABLE ; SEPARATOR = ",") AS ?concat)`.
The tool also generates a violin plot and inserts it in the `Violin charts` worksheet.
This plot is stored locally inside the `images` directory and uploaded to Google 
Drive.

**Attention!** The violin plots are configured to grant readonly access via a share 
link by default.

### Charts
Adding charts is possible by creating an [EmbeddedChart JSON object](https://developers.google.com/sheets/api/reference/rest/v4/spreadsheets/charts#embeddedchart)
and storing it inside the `charts` folder. You can create multiple charts per 
worksheet.

## Default statistics
The tool generates per default the following statistics for RDFized QA benchmarks:

* Query length per benchmark
* Query modifiers per benchmark
* Question length per 
  * answer type and benchmark (boxplot)
  * benchmark (boxplot)
  * language and benchmark (boxplot)
* Questions per 
  * answer type
  * benchmark
  * language
  * language and benchmark
* Question type per benchmark
* Statistics of used resources inside the SPARQL queries per benchmark
