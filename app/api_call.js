"use strict";

function transformDataset() {
    const filePath = document.getElementsByName("filePath").item(0).value;
    const format = document.getElementsByName("format").item(0).value;
    const homepage = document.getElementsByName("homepage").item(0).value;
    const label = document.getElementsByName("label").item(0).value;
    const output = document.getElementsByName("output").item(0).value;

    const button = document.getElementsByName("callApi").item(0);
    button.disabled = true;

    const body = {
        filePath: filePath,
        format: format,
        homepage: homepage,
        label: label
    }

    const status_text = document.getElementById("api_response");
    status_text.innerText = "Transforming data to RDF...";

    fetch("/json2rdf", {method: "POST", body: JSON.stringify(body), headers: {
        "Content-Type": "application/json",
            "Accept": output}}).then(
        result => {
            if (result.ok) {
                status_text.innerText = "Transformation successful!";
            }
            else {
                status_text.innerText = "Transformation failed!"
            }

            result.text().then(rdf => {
                document.getElementById("api_body").innerText = rdf;
            });
            button.disabled = false;
        });
}

document.getElementsByName("callApi").item(0).addEventListener("click", transformDataset)
