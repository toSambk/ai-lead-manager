package dev.aileadmanager.app.controller

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

internal fun plannedEndpoint(): ResponseEntity<Void> =
    ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
