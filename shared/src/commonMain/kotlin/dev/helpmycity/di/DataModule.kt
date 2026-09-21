package dev.helpmycity.di

import dev.helpmycity.data.local.DepartmentLocalDataSource
import dev.helpmycity.data.local.IssueLocalDataSource
import dev.helpmycity.data.local.LocalStore
import dev.helpmycity.data.local.NeighborhoodLocalDataSource
import dev.helpmycity.data.local.UserLocalDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Unpacks the platform's [LocalStore] into the data sources the
 * repositories inject. Declared once, in common code, for every target -- the
 * platform only decides which [LocalStore] to bind.
 */
val dataModule: Module = module {
    single<IssueLocalDataSource> { get<LocalStore>().issues }
    single<DepartmentLocalDataSource> { get<LocalStore>().departments }
    single<NeighborhoodLocalDataSource> { get<LocalStore>().neighborhoods }
    single<UserLocalDataSource> { get<LocalStore>().users }
}
