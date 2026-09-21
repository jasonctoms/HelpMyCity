package dev.helpmycity.data.local.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [
        IssueEntity::class,
        IssuePhotoEntity::class,
        IssueStatusChangeEntity::class,
        IssueSupportEntity::class,
        DepartmentEntity::class,
        NeighborhoodEntity::class,
        UserEntity::class,
    ],
    version = HelpMyCityDatabase.VERSION,
    exportSchema = true,
)
@ConstructedBy(HelpMyCityDatabaseConstructor::class)
abstract class HelpMyCityDatabase : RoomDatabase() {
    abstract fun issueDao(): IssueDao
    abstract fun departmentDao(): DepartmentDao
    abstract fun neighborhoodDao(): NeighborhoodDao
    abstract fun userDao(): UserDao

    companion object {
        /** Bump on any schema change; `shared/schemas` holds the exported schema. */
        const val VERSION: Int = 6
        const val FILE_NAME: String = "helpmycity.db"
    }
}

/** Room generates the `actual` per target, which keeps `@Database` in commonMain. */
@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object HelpMyCityDatabaseConstructor : RoomDatabaseConstructor<HelpMyCityDatabase> {
    override fun initialize(): HelpMyCityDatabase
}
