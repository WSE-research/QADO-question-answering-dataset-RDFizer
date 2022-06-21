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


class JSONNotFoundException(url: String): Throwable() {
    override val message = "$url not found"
}


class MappingNotDoneException(override val message: String): Throwable()


@Serializable
data class Json2RDFTransformer(private var filePath: String, private var format: String,
                          private val label: String, private val homepage: String) {
    fun mapRDF(): String {
        format = format.uppercase().replace("..", "").replace("/", "")
        format = format.replace("\\", "")

        // create temporary
        val tempFile: File = kotlin.io.path.createTempFile().toFile()
        val tempRMLRules: File = kotlin.io.path.createTempFile().toFile()

        val outputText: String
        var jsonObject: String

        // fetch JSON file
        do {
            val client: HttpClient = HttpClient.newBuilder().build()
            val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(filePath)).build()
            val response: HttpResponse<*> = client.send(request, HttpResponse.BodyHandlers.ofString())
            val contentType = response.headers().allValues("content-type")[0]

            if(response.statusCode() >= 400) {
                throw JSONNotFoundException(filePath)
            }

            jsonObject = response.body().toString()

            if(response.statusCode() in 300..399) {
                filePath = response.headers().allValues("location")[0]

                if(filePath.startsWith("/")) {
                    filePath = "${request.uri().toURL().protocol}://${request.uri().host}$filePath"
                }

                continue
            }

            if(response.statusCode() == 200 && contentType != "application/json" &&
                !contentType.contains("text/plain") && !contentType.contains("application/binary")) {
                throw JSONNotFoundException(filePath)
            }
        } while (response.statusCode() != 200)

        try {
            // store JSON data
            tempFile.writeText(jsonObject)

            // select mapping file
            val mapPath = "mappings/${format}.ttl"
            val mappingFile = File(mapPath)

            // read mapping file and replace placeholder
            var newMapping = mappingFile.readText()
            newMapping = newMapping.replace("example.json", tempFile.absolutePath)
            newMapping = newMapping.replace("URL", filePath.replace("#", "%23"))
            newMapping = newMapping.replace("LABEL", label.replace(' ', '-'))
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
            result?.write(out, "nquads")

            outputText = output.toString()

            out.close()
        } catch (e: Exception) {
            e.printStackTrace()
            throw MappingNotDoneException(e.message?: "Unknown error occurred")
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
            post("/json2rdf") {
                val json2rdfTransformer = call.receive<Json2RDFTransformer>()
                try {
                    call.respondText(json2rdfTransformer.mapRDF(), ContentType("application",
                        "n-triples"))
                } catch (e: JSONNotFoundException) {
                    call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.BadRequest)
                } catch (e: MappingNotDoneException) {
                    call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
                }
            }
        }
    }.start(wait = true)
}
