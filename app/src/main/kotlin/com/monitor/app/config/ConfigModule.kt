package com.monitor.app.config

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {
    // ConfigManager and ConfigSources are constructor-injected singletons.
    // This module is a placeholder for future config bindings.
}
