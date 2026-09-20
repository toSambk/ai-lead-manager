package dev.aileadmanager.core

data class ServiceCategory(
    val id: Long? = null,
    val code: String,
    val name: String,
    val active: Boolean = true,
)

interface ServiceCategoryRepository {
    fun save(category: ServiceCategory): ServiceCategory
    fun findById(id: Long): ServiceCategory?
    fun findByCode(code: String): ServiceCategory?
    fun findActive(): List<ServiceCategory>
}
