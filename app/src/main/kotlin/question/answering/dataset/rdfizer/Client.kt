package question.answering.dataset.rdfizer
import java.rmi.registry.LocateRegistry

fun main() {
    // URL of the JSON file
    val url = "https://raw.githubusercontent.com/ag-sc/QALD/master/7/data/qald-7-train-multilingual.json"

    // output file and directory
    val outputDirectory = "output/qald/7"
    val outputFile = "qald-7-train-multilingual.ttl"

    // get registry for RMI
    val registry = LocateRegistry.getRegistry(20000)

    // lookup service
    val service = registry.lookup("rdfizer") as RDFRMIServiceInterface

    // convert JSON to RDF and print result
    service.mapRDF(url, outputDirectory, outputFile, "QALD", "QALD 7 train data", "http://example.com")
    println(service.getRDFFile(outputDirectory, outputFile))
}