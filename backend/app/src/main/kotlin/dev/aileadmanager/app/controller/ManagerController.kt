package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.AssignableManagerResponse
import dev.aileadmanager.core.usecase.ListAssignableManagersUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/managers")
class ManagerController(private val listAssignableManagersUseCase: ListAssignableManagersUseCase) {
    @GetMapping
    fun listManagers(): List<AssignableManagerResponse> =
        listAssignableManagersUseCase.list().map(AssignableManagerResponse::from)
}
