package question.answering.dataset.rdfizer

import be.ugent.rml.Executor
import be.ugent.rml.Utils
import be.ugent.rml.functions.FunctionLoader
import be.ugent.rml.functions.lib.IDLabFunctions
import be.ugent.rml.records.RecordsFactory
import be.ugent.rml.store.QuadStore
import be.ugent.rml.store.QuadStoreFactory
import be.ugent.rml.store.RDF4JStore
import be.ugent.rml.term.NamedNode

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.*
import java.util.HashMap
import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.registry.LocateRegistry
import kotlin.jvm.Throws
import java.rmi.server.UnicastRemoteObject


interface RDFRMIServiceInterface: Remote {
    /**
     * Converts a remote JSON file to RDF triples
     *
     * @param file_path: HTTP link to the JSON source file
     * @param outputDirectory: Directory for the output file
     * @param outputFile: file name for the output
     * @param format: name of the corresponding mapping file
     * @param label: label for the Question Answering Dataset
     * @param homepage: link to the homepage of the project
     * @throws RemoteException
     *
     * @author Oliver Schmidtke
     */
    @Throws(RemoteException::class)
    fun mapRDF(file_path: String, outputDirectory: String, outputFile: String, format: String,
                      label: String, homepage: String)

    /**
     *
     */
    @Throws(RemoteException::class)
    fun getRDFFile(outputDirectory: String, outputFile: String): String
}


class RDFRMIService: RDFRMIServiceInterface {
    override fun getRDFFile(outputDirectory: String, outputFile: String): String {
        return try {
            File("$outputDirectory/$outputFile").readText()
        } catch (e: IOException) {
            "File not found!"
        }
    }

    override fun mapRDF(file_path: String, outputDirectory: String, outputFile: String, format: String,
        label: String, homepage: String) {
        // create temporary
        val tempFile: File = kotlin.io.path.createTempFile().toFile()
        val tempRMLRules: File = kotlin.io.path.createTempFile().toFile()

        // fetch JSON file
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(file_path)).build()
        val response: HttpResponse<*> = client.send(request, HttpResponse.BodyHandlers.ofString())
        val jsonObject = response.body().toString()

        try {
            val outputDirectoryObject = File(outputDirectory)
            outputDirectoryObject.mkdirs()

            // store JSON data
            tempFile.writeText(jsonObject)

            // select mapping file
            val mapPath = "mappings/${format.uppercase()}.ttl"
            val mappingFile = File(mapPath)

            // read mapping file and replace placeholder
            var newMapping = mappingFile.readText()
            newMapping = newMapping.replace("example.json", tempFile.absolutePath)
            newMapping = newMapping.replace("URL", file_path)
            newMapping = newMapping.replace("LABEL", label)
            newMapping = newMapping.replace("HOMEPAGE", homepage)

            tempRMLRules.writeText(newMapping)

            // setup RMLMapper
            val mappingStream = FileInputStream(tempRMLRules)
            val rmlStore: QuadStore = QuadStoreFactory.read(mappingStream)

            val factory = RecordsFactory(tempRMLRules.parent)

            val libraryMap = HashMap<String, Class<*>>()
            libraryMap["IDLabFunctions"] = IDLabFunctions::class.java

            val functionLoader = FunctionLoader(null, libraryMap)

            val outputStore: QuadStore = RDF4JStore()

            // apply mapping
            val executor = Executor(rmlStore, factory, functionLoader, outputStore, Utils.getBaseDirectiveTurtle(mappingStream))
            val result: QuadStore? = executor.executeV5(null)[NamedNode("rmlmapper://default.store")]

            // write triples to output file
            val output: OutputStream = FileOutputStream("${outputDirectory}/${outputFile}")
            val out = BufferedWriter(OutputStreamWriter(output))
            result?.write(out, "turtle")

            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
            error(e.message?: "")
        } finally {
            // remove temporary files
            tempFile.delete()
            tempRMLRules.delete()
        }
    }

}

fun main() {
    val registry = LocateRegistry.createRegistry(20000)
    val remote = RDFRMIService()

    registry.bind("rdfizer", UnicastRemoteObject.exportObject(remote, 0))

    /*mapRDF("https://raw.githubusercontent.com/ag-sc/QALD/master/7/data/qald-7-train-multilingual.json",
        "output/qald/7", "qald-7-train-multilingual.ttl", "QALD", "QALD 7 train data",
        "http://example.com")*/
}
