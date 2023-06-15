package com.example.demo

import ca.uhn.fhir.rest.server.RestfulServer
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.boot.web.servlet.ServletComponentScan

@SpringBootApplication(
	exclude = [org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration::class]
)
@ServletComponentScan(basePackageClasses = [RestfulServer::class])
@EntityScan("ca.uhn.fhir.*")
class DemoApplication

fun main(args: Array<String>) {
	runApplication<DemoApplication>(*args)
}
