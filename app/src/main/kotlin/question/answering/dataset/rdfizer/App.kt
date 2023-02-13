package question.answering.dataset.rdfizer

import io.ktor.server.application.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.io.*

val rmlApplicatorHost: String? = System.getenv("RML_APPLICATOR_HOST")
val ontology = File("ontology/qa-benchmark-ontology.ttl").readText()


class JSONNotFoundException(url: String): Throwable() {
    override val message = "$url not found"
}

class MissingCustomRMLException: Throwable() {
    override val message = "RML rules required for mode \"custom\""
}


class MappingNotDoneException(override val message: String): Throwable()


class MissingJsonDataException(override val message: String): Throwable()

class RmlApplicatorData(val data: String, val rml: String)


data class Json2RDFTransformer(private var filePath: String, private var format: String,
                          private val label: String, private val homepage: String) {
    fun mapRDF(rmlContentType: String?): io.ktor.client.statement.HttpResponse {
        format = format.uppercase().replace("..", "").replace("/", "")
        format = format.replace("\\", "")

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
                }

                response = rmlClient.post("$rmlApplicatorHost/data2rdf") {
                    contentType(ContentType.Application.Json)
                    setBody(RmlApplicatorData(jsonObject, newMapping))
                    if (rmlContentType != null) {
                        val contentTypeParams = rmlContentType.split("/")
                        accept(ContentType(contentTypeParams[0], contentTypeParams[1]))
                    }
                }
            }

            return response
        } catch (e: Exception) {
            e.printStackTrace()
            throw MappingNotDoneException(e.message?: "Unknown error occurred")
        }
    }
}


fun main() {
    assert(rmlApplicatorHost != null) { "Environment variable \"RML_APPLICATOR_HOST\" not set!" }
    embeddedServer(Netty, port = 8080, module = Application::rdfizerApplication).start(wait = true)
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
                }
                else {
                    call.respondText(
                        ontology + response.bodyAsText(), response.contentType()
                    )
                }
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
