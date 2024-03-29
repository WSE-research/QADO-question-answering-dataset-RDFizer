package question.answering.dataset.rdfizer

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.html.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking
import kotlinx.html.*
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

val rmlApplicatorHost: String? = System.getenv("RML_APPLICATOR_HOST")
val qadoDatasetPreprocessor: String? = System.getenv("DATASET_PREPROCESSOR_HOST")

val ontology = File("ontology/qa-benchmark-ontology.ttl").readText()

val preprocessFormats = listOf("compositional_wikidata")


class JSONNotFoundException(url: String) : Throwable() {
    override val message = "$url not found"
}


class MappingNotDoneException(override val message: String) : Throwable()


class MissingPreprocessingParameters(override val message: String) : Throwable()


class RmlApplicatorData(val data: String, val rml: String)

class PreprocessingData(val fetch_url: String, val language: String)


data class Json2RDFTransformer(
    private var filePath: String, private var format: String,
    private val label: String, private val homepage: String, private val language: String?
) {
    fun mapRDF(rmlContentType: String?): io.ktor.client.statement.HttpResponse {
        val acceptType: String = rmlContentType ?: "text/turtle"

        format = format.uppercase().replace("..", "").replace("/", "")
        format = format.replace("\\", "")
        val checkFormat = format.lowercase()

        var jsonObject: String

        if (preprocessFormats.contains(checkFormat)) {
            if (language == null) {
                throw MissingPreprocessingParameters("Parameter \"language\" required")
            }

            runBlocking {
                val rmlClient = HttpClient(CIO) {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        gson()
                    }
                }

                val processResponse = rmlClient.post("$qadoDatasetPreprocessor/process/$checkFormat") {
                    contentType(ContentType.Application.Json)
                    setBody(PreprocessingData(filePath, language))
                }

                jsonObject = processResponse.bodyAsText()

                if (!processResponse.status.isSuccess()) {
                    throw MappingNotDoneException("Preprocessing failed! $jsonObject")
                }
            }
        } else {
            // fetch JSON file
            do {
                val client: HttpClient = HttpClient.newBuilder().build()
                val request: HttpRequest = HttpRequest.newBuilder().uri(URI.create(filePath)).build()
                val response: HttpResponse<*> = client.send(request, HttpResponse.BodyHandlers.ofString())
                val contentType = response.headers().allValues("content-type")[0]

                if (response.statusCode() >= 400) {
                    throw JSONNotFoundException(filePath)
                }

                jsonObject = response.body().toString()

                if (response.statusCode() in 300..399) {
                    filePath = response.headers().allValues("location")[0]

                    if (filePath.startsWith("/")) {
                        filePath = "${request.uri().toURL().protocol}://${request.uri().host}$filePath"
                    }

                    continue
                }

                if (response.statusCode() == 200 && contentType != "application/json" &&
                    !contentType.contains("text/plain") && !contentType.contains("application/binary")
                ) {
                    throw JSONNotFoundException(filePath)
                }
            } while (response.statusCode() != 200)
        }

        try {
            var newMapping = run {
                // select mapping file
                val mapPath = "mappings/${format}.ttl"
                val mappingFile = File(mapPath)

                // read mapping file and replace placeholder
                mappingFile.readText()
            }

            newMapping = newMapping.replace("LABEL", label.replace(' ', '-'))
            newMapping = newMapping.replace("HOMEPAGE", homepage)

            newMapping = newMapping.replace("URL", filePath.replace("#", "%23"))
            val response: io.ktor.client.statement.HttpResponse

            runBlocking {
                val rmlClient = HttpClient(CIO) {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        gson()
                    }
                    install(HttpTimeout) {
                        requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
                    }
                }

                response = rmlClient.post("$rmlApplicatorHost/data2rdf") {
                    contentType(ContentType.Application.Json)
                    setBody(RmlApplicatorData(jsonObject, newMapping))
                    headers {
                        append(HttpHeaders.Accept, acceptType)
                    }
                }
            }

            return response
        } catch (e: Exception) {
            e.printStackTrace()
            throw MappingNotDoneException(e.message ?: "Unknown error occurred")
        }
    }
}


fun main() {
    assert(rmlApplicatorHost != null) { "Environment variable \"RML_APPLICATOR_HOST\" not set!" }
    assert(qadoDatasetPreprocessor != null) { "Environment variable \"DATASET_PREPROCESSOR_HOST\" not set!" }
    embeddedServer(Netty, port = 8080, module = Application::rdfizerApplication, configure = {
        responseWriteTimeoutSeconds = 0
    }).start(wait = true)
}

fun Application.rdfizerApplication() {
    install(ContentNegotiation) {
        gson()
    }

    routing {
        post("/json2rdf") {
            val json2rdfTransformer = call.receive<Json2RDFTransformer>()
            val accept = call.request.accept()
            try {
                val response = json2rdfTransformer.mapRDF(accept)

                if (!response.status.isSuccess()) {
                    call.respondText(response.bodyAsText(), response.contentType(), response.status)
                } else {
                    call.respondText(response.bodyAsText(), response.contentType())
                }
            } catch (e: JSONNotFoundException) {
                call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.BadRequest)
            } catch (e: MappingNotDoneException) {
                call.respondText(e.message, ContentType.Text.Plain, HttpStatusCode.InternalServerError)
            } catch (e: Exception) {
                call.respondText(
                    e.message ?: "An unknown error occurred",
                    ContentType.Text.Plain,
                    HttpStatusCode.InternalServerError
                )
            }
        }
        get("/json2rdf") {
            call.respondText(
                "The RDFizer service is running. Please view the documentation at " +
                        "<a href=\"https://github.com/anbo-de/question-answering-dataset-RDFizer#readme\">Github</a>.",
                ContentType.Text.Html
            )
        }
        get("/ontology") {
            call.respondText(ontology, ContentType("text", "turtle"))
        }
        get("/") {
            val callHost = call.request.host()
            var formats = mutableListOf<String>()

            File("mappings/").walk().forEach {
                if (it.name.contains(".ttl")) {
                    formats.add(it.name.replace(".ttl", "").lowercase())
                }
            }
            formats = formats.sorted().toMutableList()

            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title {
                        +"QADO dataset RDFizer"
                    }
                    link("/style", "stylesheet", "text/css")
                }
                body {
                    header {
                        h1 {
                            +"QADO dataset RDFizer"
                        }
                    }
                    main {
                        p {
                            +"This instance is running at $callHost."
                        }
                        p {
                            +"Dataset URL: "
                            textInput(name = "filePath")
                        }
                        p {
                            +"Format: "
                            select {
                                name = "format"
                                formats.forEach {
                                    option {
                                        +it
                                    }
                                }
                            }
                        }
                        p {
                            +"Project homepage: "
                            textInput(name = "homepage")
                        }
                        p {
                            +"Dataset label: "
                            textInput(name = "label")
                        }
                        p {
                            +"Output format: "
                            select {
                                name = "output"
                                option { +"text/turtle" }
                                option { +"text/xml" }
                                option { +"application/ld+json" }
                                option { +"application/n-triples" }
                            }
                        }
                        p {
                            button(name = "callApi") {
                                +"Transform dataset"
                            }
                        }
                        p {
                            id = "api_response"
                        }
                        textArea(rows = "20") {
                            id = "api_body"
                        }
                        script(type = "text/javascript", src = "/script") {}
                    }
                }
            }
        }
        get("/script") {
            call.respondText(File("api_call.js").readText(), ContentType.Text.JavaScript)
        }
        get("/style") {
            call.respondText(File("styles.css").readText(), ContentType.Text.CSS)
        }
    }
}
