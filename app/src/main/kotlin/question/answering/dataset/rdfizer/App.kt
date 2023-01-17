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

class MissingCustomRMLException: Throwable() {
    override val message = "RML rules required for mode \"custom\""
}


class MappingNotDoneException(override val message: String): Throwable()

class MissingJsonDataException(override val message: String): Throwable()


@Serializable
data class Json2RDFTransformer(private var filePath: String? = null, private var format: String, private val json: String? = null,
                          private val label: String, private val homepage: String, private val rml: String? = null) {
    fun mapRDF(): String {
        val ontology = File("ontology/qa-benchmark-ontology.ttl").readText()

        format = format.uppercase().replace("..", "").replace("/", "")
        format = format.replace("\\", "")

        if(format == "CUSTOM" && rml == null) {
            throw MissingCustomRMLException()
        }

        // create temporary
        val tempFile: File = kotlin.io.path.createTempFile().toFile()
        val tempRMLRules: File = kotlin.io.path.createTempFile().toFile()

        var jsonObject: String

        // fetch JSON file
        if (filePath != null) {
            do {
                val client: HttpClient = HttpClient.newBuilder().build()
                val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(filePath!!)).build()
                val response: HttpResponse<*> = client.send(request, HttpResponse.BodyHandlers.ofString())
                val contentType = response.headers().allValues("content-type")[0]

                if(response.statusCode() >= 400) {
                    throw JSONNotFoundException(filePath!!)
                }

                jsonObject = response.body().toString()

                if(response.statusCode() in 300..399) {
                    filePath = response.headers().allValues("location")[0]

                    if(filePath!!.startsWith("/")) {
                        filePath = "${request.uri().toURL().protocol}://${request.uri().host}$filePath"
                    }

                    continue
                }

                if(response.statusCode() == 200 && contentType != "application/json" &&
                    !contentType.contains("text/plain") && !contentType.contains("application/binary")) {
                    throw JSONNotFoundException(filePath!!)
                }
            } while (response.statusCode() != 200)
        }
        else {
            if (json != null) {
                jsonObject = json
            }
            else {
                throw MissingJsonDataException("Please provide a \"filePath\" or a \"json\"!")
            }
        }

        try {
            // store JSON data
            tempFile.writeText(jsonObject)

            var newMapping = if(format != "CUSTOM") {
                // select mapping file
                val mapPath = "mappings/${format}.ttl"
                val mappingFile = File(mapPath)

                // read mapping file and replace placeholder
                mappingFile.readText()
            } else {
                rml!!
            }

            newMapping = newMapping.replace("example.json", tempFile.absolutePath)
            newMapping = newMapping.replace("LABEL", label.replace(' ', '-'))
            newMapping = newMapping.replace("HOMEPAGE", homepage)

            newMapping = if(filePath != null) {
                newMapping.replace("URL", filePath!!.replace("#", "%23"))
            } else {
                newMapping.replace("URL", "urn:local:file")
            }

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

            out.write(ontology)

            result?.write(out, "turtle")

            val outputText = output.toString()

            out.close()

            return outputText
        } catch (e: Exception) {
            e.printStackTrace()
            throw MappingNotDoneException(e.message?: "Unknown error occurred")
        } finally {
            // remove temporary files
            tempFile.delete()
            tempRMLRules.delete()
        }
    }
}


fun main() {
    embeddedServer(Netty, port = 8080, module = Application::rdfizerApplication).start(wait = true)
}

fun Application.rdfizerApplication() {
    install(ContentNegotiation) {
        json()
    }

    routing {
        post("/json2rdf") {
            val json2rdfTransformer = call.receive<Json2RDFTransformer>()
            try {
                call.respondText(
                    json2rdfTransformer.mapRDF(), ContentType(
                        "application",
                        "n-triples"
                    )
                )
            } catch (e: JSONNotFoundException) {
                call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.BadRequest)
            } catch (e: MappingNotDoneException) {
                call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
            } catch (e: MissingCustomRMLException) {
                call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.BadRequest)
            } catch (e: MissingJsonDataException) {
                call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.BadRequest)
            }
        }
        get("/json2rdf") {
            call.respondText(
                "The RDFizer service is running. Please view the documentation at " +
                        "<a href=\"https://github.com/anbo-de/question-answering-dataset-RDFizer#readme\">Github</a>.",
                ContentType.Text.Html
            )
        }
    }
}
