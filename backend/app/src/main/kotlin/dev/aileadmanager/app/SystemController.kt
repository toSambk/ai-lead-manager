package dev.aileadmanager.app

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/system")
class SystemController {
    @GetMapping
    fun status(): Map<String, String> = mapOf("status" to "ok")
}
