package com.monitor.app.diag

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DiagModule {
    // DiagnosticLogger is @Singleton and @Inject constructor —
    // Hilt knows how to provide it. This module is a placeholder.
}
