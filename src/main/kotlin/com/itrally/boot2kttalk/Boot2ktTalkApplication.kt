package com.itrally.boot2kttalk

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.context.support.beans
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.body
import org.springframework.web.reactive.function.server.bodyToServerSentEvents
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration

@SpringBootApplication
class Boot2ktTalkApplication

fun main(args: Array<String>) {
    SpringApplicationBuilder()
            .sources(Boot2ktTalkApplication::class.java)
            .initializers(AppBeans().getBeans())
            .run(*args)
}

class AppBeans {
    fun getBeans() = beans {
        bean { PersonHandler(ref()) }
        bean<SseHandler>()
        bean { AppRouter(ref(), ref()).getRouter() }
    }
}

@Document
data class Person(@Id val id: String?, val firstName: String, val lastName: String)

interface PersonRepository : ReactiveMongoRepository<Person, String> {
    fun findByFirstName(firstName: String): Flux<Person>
}

@Service
class PersonService(val personRepository: PersonRepository) {
    fun findAll() = personRepository.findAll()
    fun findByFirstName(firstName: String) = personRepository.findByFirstName(firstName)
    fun saveAll(persons: Flux<Person>) = personRepository.saveAll(persons).subscribe()
    fun deleteAll() = personRepository.deleteAll().subscribe()
}

class PersonHandler(val personService: PersonService) {
    fun findAll(request: ServerRequest) = ServerResponse.ok().body(personService.findAll())
    fun findByFirstName(request: ServerRequest): Mono<ServerResponse> {
        val firstName = request.pathVariable("fname")
        return ServerResponse.ok().body(personService.findByFirstName(firstName))
    }
    fun saveAll(request: ServerRequest): Mono<ServerResponse> {
        val persons = request.bodyToFlux(Person::class.java)
        personService.saveAll(persons)
        return ServerResponse.noContent().build()
    }
    fun deleteAll(request: ServerRequest): Mono<ServerResponse> {
        personService.deleteAll()
        return ServerResponse.noContent().build()
    }
}

class SseHandler {
    fun sse(request: ServerRequest) =
            ServerResponse.ok().bodyToServerSentEvents(
                    Flux.interval(Duration.ofMillis(100)).map { "Milis is: $it" }
            )
}

class AppRouter(val personHandler: PersonHandler, val sseHandler: SseHandler) {
    fun getRouter() = router {
        ("/api" and accept(MediaType.APPLICATION_JSON)).nest {
            "/persons".nest {
                GET("/", personHandler::findAll)
                GET("/{fname}", personHandler::findByFirstName)
                POST("/", personHandler::saveAll)
                DELETE("/", personHandler::deleteAll)
            }
        }
        GET("/sse", sseHandler::sse)
    }
}