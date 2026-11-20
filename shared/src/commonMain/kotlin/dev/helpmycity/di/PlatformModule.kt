package dev.helpmycity.di

import org.koin.core.module.Module

/**
 * Binds the platform's [dev.helpmycity.data.local.LocalStore], and nothing else.
 * Each target contributes a `getDatabaseBuilder` and a `SQLiteDriver`, and the
 * schema, DAOs, mappers, and store are all common code.
 *
 * This is a DSL module instead of an annotated one, because `expect`/`actual`
 * declarations can't have Koin annotations.
 */
expect fun platformModule(): Module
