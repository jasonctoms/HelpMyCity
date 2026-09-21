package dev.helpmycity.data.local

/**
 * The app's offline storage, as one platform seam.
 *
 * Bundling the data sources behind a single type keeps every binding
 * above this line in common code: `dataModule` declares each data source once,
 * and a platform binds only a `LocalStore`. Declaring the data sources
 * per-platform would put the same `single<T>` in more than one compilation,
 * which the Koin compiler plugin rejects.
 *
 * [dev.helpmycity.data.local.database.RoomLocalStore] backs every
 * target; [InMemoryLocalStore] backs tests.
 */
interface LocalStore {
    val issues: IssueLocalDataSource
    val departments: DepartmentLocalDataSource
    val neighborhoods: NeighborhoodLocalDataSource
    val users: UserLocalDataSource
}
