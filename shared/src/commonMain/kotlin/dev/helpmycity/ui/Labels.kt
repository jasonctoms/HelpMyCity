package dev.helpmycity.ui

import androidx.compose.runtime.Composable
import dev.helpmycity.domain.model.EditableField
import dev.helpmycity.domain.model.IssueCategory
import dev.helpmycity.domain.model.IssuePriority
import dev.helpmycity.domain.model.IssueStatus
import dev.helpmycity.domain.model.UserRole
import helpmycity.shared.generated.resources.Res
import helpmycity.shared.generated.resources.category_ada_access
import helpmycity.shared.generated.resources.category_drainage
import helpmycity.shared.generated.resources.category_graffiti
import helpmycity.shared.generated.resources.category_other
import helpmycity.shared.generated.resources.category_park_maintenance
import helpmycity.shared.generated.resources.category_road_surface
import helpmycity.shared.generated.resources.category_sidewalk
import helpmycity.shared.generated.resources.category_signage
import helpmycity.shared.generated.resources.category_street_lighting
import helpmycity.shared.generated.resources.category_traffic_safety
import helpmycity.shared.generated.resources.category_trash_dumping
import helpmycity.shared.generated.resources.category_water_utilities
import helpmycity.shared.generated.resources.field_category
import helpmycity.shared.generated.resources.field_department
import helpmycity.shared.generated.resources.field_description
import helpmycity.shared.generated.resources.field_location
import helpmycity.shared.generated.resources.field_neighborhood
import helpmycity.shared.generated.resources.field_notes_source
import helpmycity.shared.generated.resources.field_priority
import helpmycity.shared.generated.resources.field_requested_action
import helpmycity.shared.generated.resources.field_title
import helpmycity.shared.generated.resources.priority_high
import helpmycity.shared.generated.resources.priority_low
import helpmycity.shared.generated.resources.priority_medium
import helpmycity.shared.generated.resources.role_admin
import helpmycity.shared.generated.resources.role_manager
import helpmycity.shared.generated.resources.role_resident
import helpmycity.shared.generated.resources.status_complete
import helpmycity.shared.generated.resources.status_in_progress
import helpmycity.shared.generated.resources.status_in_review
import helpmycity.shared.generated.resources.status_open
import helpmycity.shared.generated.resources.status_rejected
import org.jetbrains.compose.resources.stringResource

/**
 * Every user-visible enum label goes through a string resource, so English and
 * Spanish stay in step and nothing leaks a `storageKey` onto the screen.
 */
@Composable
fun IssueStatus.label(): String = stringResource(
    when (this) {
        IssueStatus.IN_REVIEW -> Res.string.status_in_review
        IssueStatus.OPEN -> Res.string.status_open
        IssueStatus.IN_PROGRESS -> Res.string.status_in_progress
        IssueStatus.COMPLETE -> Res.string.status_complete
        IssueStatus.REJECTED -> Res.string.status_rejected
    }
)

@Composable
fun IssuePriority.label(): String = stringResource(
    when (this) {
        IssuePriority.HIGH -> Res.string.priority_high
        IssuePriority.MEDIUM -> Res.string.priority_medium
        IssuePriority.LOW -> Res.string.priority_low
    }
)

@Composable
fun IssueCategory.label(): String = stringResource(
    when (this) {
        IssueCategory.ROAD_SURFACE -> Res.string.category_road_surface
        IssueCategory.STREET_LIGHTING -> Res.string.category_street_lighting
        IssueCategory.DRAINAGE -> Res.string.category_drainage
        IssueCategory.ADA_ACCESS -> Res.string.category_ada_access
        IssueCategory.TRAFFIC_SAFETY -> Res.string.category_traffic_safety
        IssueCategory.SIDEWALK -> Res.string.category_sidewalk
        IssueCategory.PARK_MAINTENANCE -> Res.string.category_park_maintenance
        IssueCategory.TRASH_DUMPING -> Res.string.category_trash_dumping
        IssueCategory.GRAFFITI -> Res.string.category_graffiti
        IssueCategory.WATER_UTILITIES -> Res.string.category_water_utilities
        IssueCategory.SIGNAGE -> Res.string.category_signage
        IssueCategory.OTHER -> Res.string.category_other
    }
)

/**
 * Names the fields a manager changed, for the "Changed: ..." line under an
 * edited issue. Reuses the form's own labels so the two never drift apart.
 */
@Composable
fun EditableField.label(): String = stringResource(
    when (this) {
        EditableField.TITLE -> Res.string.field_title
        EditableField.DESCRIPTION -> Res.string.field_description
        EditableField.REQUESTED_ACTION -> Res.string.field_requested_action
        EditableField.CATEGORY -> Res.string.field_category
        EditableField.PRIORITY -> Res.string.field_priority
        EditableField.LOCATION -> Res.string.field_location
        EditableField.NEIGHBORHOOD -> Res.string.field_neighborhood
        EditableField.DEPARTMENT -> Res.string.field_department
        EditableField.NOTES_SOURCE -> Res.string.field_notes_source
    }
)

@Composable
fun UserRole.label(): String = stringResource(
    when (this) {
        UserRole.RESIDENT -> Res.string.role_resident
        UserRole.MANAGER -> Res.string.role_manager
        UserRole.ADMIN -> Res.string.role_admin
    }
)
