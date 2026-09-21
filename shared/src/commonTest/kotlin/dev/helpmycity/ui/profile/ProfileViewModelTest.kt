package dev.helpmycity.ui.profile

import dev.helpmycity.data.export.IssueCsvExporter
import dev.helpmycity.data.local.InMemoryDepartmentLocalDataSource
import dev.helpmycity.data.local.InMemoryIssueLocalDataSource
import dev.helpmycity.data.local.InMemoryNeighborhoodLocalDataSource
import dev.helpmycity.data.repository.DefaultDepartmentRepository
import dev.helpmycity.data.repository.DefaultIssueRepository
import dev.helpmycity.data.repository.DefaultNeighborhoodRepository
import dev.helpmycity.data.session.FakeUserSession
import dev.helpmycity.domain.model.IssueDraft
import dev.helpmycity.domain.model.User
import dev.helpmycity.domain.model.UserRole
import dev.helpmycity.domain.util.IdGenerator
import dev.helpmycity.domain.util.TimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val admin = User("admin-1", "Admin", null, UserRole.ADMIN)
    private val manager = User("manager-1", "Manager", null, UserRole.MANAGER)
    private val resident = User("resident-1", "Ana", null, UserRole.RESIDENT)

    private val session = FakeUserSession()
    private val time = TimeProvider { 1_000L }
    private val ids = object : IdGenerator {
        private var next = 0
        override fun newId(): String = "id-${next++}"
    }
    private val neighborhoods = InMemoryNeighborhoodLocalDataSource()
    private val issues = DefaultIssueRepository(InMemoryIssueLocalDataSource(), neighborhoods, session, ids, time)

    private fun viewModel() = ProfileViewModel(
        session = session,
        neighborhoodRepository = DefaultNeighborhoodRepository(neighborhoods),
        departmentRepository = DefaultDepartmentRepository(InMemoryDepartmentLocalDataSource()),
        issueRepository = issues,
        csvExporter = IssueCsvExporter(),
        time = time,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun anAdminExportsEveryIssueIncludingOnesStillAwaitingReview() = runTest {
        session.signedInAs(resident)
        issues.submitIssue(IssueDraft(title = "Pending", description = "d", locationDescription = "Main St"))
        session.signedInAs(admin)

        val csv = viewModel().exportCsv()

        val rows = csv!!.lines().filter { it.isNotBlank() }
        assertEquals(2, rows.size)
        assertTrue(rows[1].contains("Pending"))
    }

    @Test
    fun nobodyButAnAdminCanExport() = runTest {
        for (user in listOf(manager, resident, null)) {
            session.signedInAs(user)
            assertNull(viewModel().exportCsv(), "exported as ${user?.role}")
        }
    }
}
