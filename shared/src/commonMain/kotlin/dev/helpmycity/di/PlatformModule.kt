package dev.helpmycity.di

import org.koin.core.module.Module

/**
 * Binds the platform's [dev.helpmycity.data.local.LocalStore], and
 * nothing else. Each target contributes a `getDatabaseBuilder` and a
 * `SQLiteDriver`; the schema, DAOs, mappers and the store are common code.
 *
 * A DSL module rather than an annotated one because `expect`/`actual`
 * declarations cannot carry Koin annotations.
 */
expect fun platformModule(): Module
