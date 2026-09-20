package dev.aileadmanager.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

@SpringBootApplication(scanBasePackages = ["dev.aileadmanager"])
@EnableJdbcRepositories(basePackages = ["dev.aileadmanager.persistence"])
class AiLeadManagerApplication

fun main(args: Array<String>) {
	runApplication<AiLeadManagerApplication>(*args)
}
