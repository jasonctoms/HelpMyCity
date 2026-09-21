package dev.helpmycity.ui

import dev.helpmycity.domain.model.Department
import dev.helpmycity.domain.model.ManagerScope
import dev.helpmycity.domain.model.Neighborhood

/**
 * The areas a scope covers, named the way the city names them.
 *
 * Citywide is deliberately not in the list: it is a different sentence rather
 * than a longer one, and the screens say it themselves. An id with nothing
 * behind it any more is shown raw rather than dropped, so a stale assignment is
 * visible to the admin who has to clear it.
 */
fun ManagerScope.areaNames(
    neighborhoods: List<Neighborhood>,
    departments: List<Department>,
): List<String> = buildList {
    addAll(districts.sorted())
    addAll(neighborhoodIds.map { id -> neighborhoods.firstOrNull { it.id == id }?.name ?: id }.sorted())
    addAll(departmentIds.map { id -> departments.firstOrNull { it.id == id }?.name ?: id }.sorted())
}
