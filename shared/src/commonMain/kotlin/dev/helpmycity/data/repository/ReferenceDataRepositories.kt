package dev.helpmycity.data.repository

import dev.helpmycity.data.local.DepartmentLocalDataSource
import dev.helpmycity.data.local.NeighborhoodLocalDataSource
import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.Neighborhood
import dev.helpmycity.domain.repository.DepartmentRepository
import dev.helpmycity.domain.repository.NeighborhoodRepository
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

@Single(binds = [DepartmentRepository::class])
class DefaultDepartmentRepository(
    private val local: DepartmentLocalDataSource,
) : DepartmentRepository {
    override fun observeDepartments(): Flow<List<Department>> = local.observeAll()
    override suspend fun getDepartment(id: String): Department? = local.getById(id)
    override suspend fun upsert(department: Department) = local.upsert(department)
    override suspend fun delete(id: String) = local.delete(id)
}

@Single(binds = [NeighborhoodRepository::class])
class DefaultNeighborhoodRepository(
    private val local: NeighborhoodLocalDataSource,
) : NeighborhoodRepository {
    override fun observeNeighborhoods(): Flow<List<Neighborhood>> = local.observeAll()
    override suspend fun getNeighborhood(id: String): Neighborhood? = local.getById(id)
    override suspend fun upsert(neighborhood: Neighborhood) = local.upsert(neighborhood)
    override suspend fun delete(id: String) = local.delete(id)
}
