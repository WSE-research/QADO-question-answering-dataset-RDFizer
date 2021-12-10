package question.answering.dataset.rdfizer
import java.rmi.registry.LocateRegistry

fun main() {
    // URL of the JSON file
    val url = "https://raw.githubusercontent.com/vladislavneon/RuBQ/master/RuBQ_1.0/RuBQ_1.0_test.json"

    // output file and directory
    val outputDirectory = "output/rubq/1"
    val outputFile = "rubq-1-test.ttl"

    // get registry for RMI
    val registry = LocateRegistry.getRegistry(20000)

    // lookup service
    val service = registry.lookup("rdfizer") as RDFRMIServiceInterface

    // convert JSON to RDF and print result
    service.mapRDF(url, outputDirectory, outputFile, "rubq", "RuBQ 1.0 test data", "http://example.com")
    println(service.getRDFFile(outputDirectory, outputFile))
}