package dev.helpmycity.ui

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.Neighborhood

/**
 * The areas a scope covers, named the way the city names them.
 *
 * Citywide is left out of the list on purpose. It is a different sentence, not a
 * longer one, and the screens say it themselves. An id that does not point at
 * anything anymore gets shown raw instead of dropped, so the admin who has to
 * clean up a stale assignment can actually see it.
 */
fun ManagerScope.areaNames(
    neighborhoods: List<Neighborhood>,
    departments: List<Department>,
): List<String> = buildList {
    addAll(districts.sorted())
    addAll(neighborhoodIds.map { id -> neighborhoods.firstOrNull { it.id == id }?.name ?: id }.sorted())
    addAll(departmentIds.map { id -> departments.firstOrNull { it.id == id }?.name ?: id }.sorted())
}
