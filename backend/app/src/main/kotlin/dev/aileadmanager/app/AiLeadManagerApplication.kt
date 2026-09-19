package dev.aileadmanager.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["dev.aileadmanager"])
class AiLeadManagerApplication

fun main(args: Array<String>) {
	runApplication<AiLeadManagerApplication>(*args)
}
