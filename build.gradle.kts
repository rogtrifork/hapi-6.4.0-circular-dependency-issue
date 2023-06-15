import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "2.7.5"
	id("io.spring.dependency-management") version "1.0.15.RELEASE"
	kotlin("jvm") version "1.6.21"
	kotlin("plugin.spring") version "1.6.21"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
	mavenCentral()
}

val hapiFhirVersion = "6.4.4"

dependencies {
	implementation(enforcedPlatform("ca.uhn.hapi.fhir:hapi-fhir-bom:$hapiFhirVersion"))
	implementation("ca.uhn.hapi.fhir:hapi-fhir-server")
	implementation("ca.uhn.hapi.fhir:hapi-fhir-structures-r4")
	implementation("ca.uhn.hapi.fhir:hapi-fhir-jpaserver-base")
//	implementation("ca.uhn.hapi.fhir:hapi-fhir-server-")

	implementation("org.springframework.boot:spring-boot-starter")
	implementation("org.springframework.boot:spring-boot-starter-web")

	implementation("com.h2database:h2")

	implementation("org.jetbrains.kotlin:kotlin-reflect")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
