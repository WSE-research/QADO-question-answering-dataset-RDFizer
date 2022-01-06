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

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.serialization.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.Serializable

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.*
import java.util.HashMap


@Serializable
data class Json2RDFTransformer(private val filePath: String, private val format: String,
                          private val label: String, private val homepage: String) {
    fun mapRDF(): String {
        // create temporary
        val tempFile: File = kotlin.io.path.createTempFile().toFile()
        val tempRMLRules: File = kotlin.io.path.createTempFile().toFile()

        var outputText: String

        // fetch JSON file
        val client: HttpClient = HttpClient.newBuilder().build()
        val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(filePath)).build()
        val response: HttpResponse<*> = client.send(request, HttpResponse.BodyHandlers.ofString())
        val jsonObject = response.body().toString()

        try {
            // store JSON data
            tempFile.writeText(jsonObject)

            // select mapping file
            val mapPath = "mappings/${format.uppercase()}.ttl"
            val mappingFile = File(mapPath)

            // read mapping file and replace placeholder
            var newMapping = mappingFile.readText()
            newMapping = newMapping.replace("example.json", tempFile.absolutePath)
            newMapping = newMapping.replace("URL", filePath)
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
            val output: OutputStream = ByteArrayOutputStream()
            val out = BufferedWriter(OutputStreamWriter(output))
            result?.write(out, "turtle")

            outputText = output.toString()

            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
            outputText = e.message?: "Unknown error occurred"
        } finally {
            // remove temporary files
            tempFile.delete()
            tempRMLRules.delete()
        }

        return outputText
    }
}



fun main() {
    embeddedServer(Netty, port = 8080) {
        install(ContentNegotiation) {
            json()
        }

        routing {
            get("/") {
                call.respondText("Hello, world!")
            }

            post("/json2rdf") {
                val json2rdfTransformer = call.receive<Json2RDFTransformer>()

                call.respondText(json2rdfTransformer.mapRDF(), ContentType("text", "turtle"))
            }
        }
    }.start(wait = true)
}
