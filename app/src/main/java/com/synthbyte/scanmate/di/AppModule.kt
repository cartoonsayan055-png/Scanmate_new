package com.synthbyte.scanmate.di

import android.content.Context
import com.synthbyte.scanmate.data.AppDatabase
import com.synthbyte.scanmate.data.DocDao
import com.synthbyte.scanmate.data.repository.DocumentRepositoryImpl
import com.synthbyte.scanmate.domain.repository.DocumentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.getDatabase(context)

    @Provides
    fun provideDocDao(database: AppDatabase): DocDao = database.docDao()

    @Provides
    @Singleton
    fun provideDocumentRepository(repository: DocumentRepositoryImpl): DocumentRepository = repository
}
