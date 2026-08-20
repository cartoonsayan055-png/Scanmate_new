package com.synthbyte.scanmate.di

import com.synthbyte.scanmate.ui.viewmodels.AppErrorBus
import com.synthbyte.scanmate.ui.viewmodels.AppErrorReporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityRetainedComponent

@Module
@InstallIn(ActivityRetainedComponent::class)
abstract class AppErrorModule {
    @Binds
    abstract fun bindAppErrorReporter(appErrorBus: AppErrorBus): AppErrorReporter
}
