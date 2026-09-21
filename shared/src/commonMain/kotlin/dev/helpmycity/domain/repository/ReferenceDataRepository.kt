package dev.helpmycity.domain.repository

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Neighborhood
import kotlinx.coroutines.flow.Flow

interface DepartmentRepository {
    fun observeDepartments(): Flow<List<Department>>
    suspend fun getDepartment(id: String): Department?
    suspend fun upsert(department: Department)
    suspend fun delete(id: String)
}

interface NeighborhoodRepository {
    fun observeNeighborhoods(): Flow<List<Neighborhood>>
    suspend fun getNeighborhood(id: String): Neighborhood?
    suspend fun upsert(neighborhood: Neighborhood)
    suspend fun delete(id: String)
}
