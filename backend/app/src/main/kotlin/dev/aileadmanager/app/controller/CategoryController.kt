package dev.aileadmanager.app.controller

import dev.aileadmanager.app.dto.ServiceCategoryResponse
import dev.aileadmanager.core.ServiceCategoryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class CategoryController(private val categories: ServiceCategoryRepository) {
    @GetMapping("/api/categories")
    fun listCategories(): List<ServiceCategoryResponse> = categories.findActive().map { category ->
        ServiceCategoryResponse(
            id = requireNotNull(category.id) { "Stored category has no ID" },
            code = category.code,
            name = category.name,
        )
    }
}
