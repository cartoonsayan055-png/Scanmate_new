package com.synthbyte.scanmate

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.synthbyte.scanmate.data.AppDatabase
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.Page
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocDaoIntegrationTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchDocumentsMatchesTitleCategoryTagsAndOcrText() = runTest {
        val dao = database.docDao()
        dao.insertDocument(
            Document(
                title = "Chemistry Lab",
                ocrText = "Acid base neutralisation",
                category = "School",
                tags = "science,lab"
            )
        )
        dao.insertDocument(Document(title = "Shopping List", ocrText = "milk bread"))

        dao.searchDocuments("neutralisation").test {
            assertEquals(listOf("Chemistry Lab"), awaitItem().map { it.title })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun documentWithPagesReturnsPagesForInsertedDocument() = runTest {
        val dao = database.docDao()
        val documentId = dao.insertDocument(Document(title = "Batch Scan"))
        dao.insertPage(Page(documentId = documentId, imagePath = "one.jpg", pageOrder = 0))
        dao.insertPage(Page(documentId = documentId, imagePath = "two.jpg", pageOrder = 1))

        dao.getDocumentWithPages(documentId).test {
            val item = awaitItem()
            assertEquals("Batch Scan", item?.document?.title)
            assertEquals(listOf("one.jpg", "two.jpg"), item?.pages?.sortedBy { it.pageOrder }?.map { it.imagePath })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
