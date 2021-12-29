package question.answering.dataset.rdfizer
import java.rmi.registry.LocateRegistry

fun main() {
    // URL of the JSON file
    val url = "https://raw.githubusercontent.com/ag-sc/QALD/master/9/data/qald-9-test-multilingual.json"

    // output file and directory
    val outputDirectory = "output/qald/9"
    val outputFile = "qald-9-multilingual-train.ttl"

    // get registry for RMI
    val registry = LocateRegistry.getRegistry(20000)

    // lookup service
    val service = registry.lookup("rdfizer") as RDFRMIServiceInterface

    // convert JSON to RDF and print result
    service.mapRDF(url, outputDirectory, outputFile, "qald", "QALD 9 multilingual", "http://example.com")
    println(service.getRDFFile(outputDirectory, outputFile))
}