package com.synthbyte.scanmate

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.synthbyte.scanmate.data.AppDatabase
import com.synthbyte.scanmate.data.Document
import com.synthbyte.scanmate.data.DocumentWithPages
import com.synthbyte.scanmate.ui.viewmodels.AppErrorBus
import com.synthbyte.scanmate.ui.viewmodels.DocumentDetailViewModel
import com.synthbyte.scanmate.ui.viewmodels.ExportState
import com.synthbyte.scanmate.utils.PdfExportQuality
import com.synthbyte.scanmate.utils.PdfPageSize
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34])
class ExportStateTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exportDocxWithNullDocumentEmitsError() = runTest {
        val viewModel = viewModelFor(0L)

        viewModel.exportDocx(null)

        assertTrue(viewModel.exportState.value is ExportState.Error)
    }

    @Test
    fun exportPdfWithEmptyPagesEmitsError() = runTest {
        val documentId = database.docDao().insertDocument(Document(title = "Empty"))
        val document = Document(id = documentId, title = "Empty")
        val viewModel = viewModelFor(documentId)

        viewModel.exportPdf(
            dwp = DocumentWithPages(document = document, pages = emptyList()),
            quality = PdfExportQuality.BALANCED,
            filename = "empty",
            pageSize = PdfPageSize.A4
        )

        assertTrue(viewModel.exportState.value is ExportState.Error)
    }

    @Test
    fun clearExportStateResetsToIdle() = runTest {
        val viewModel = viewModelFor(0L)
        viewModel.exportDocx(null)
        assertTrue(viewModel.exportState.value is ExportState.Error)

        viewModel.clearExportState()

        assertTrue(viewModel.exportState.value is ExportState.Idle)
    }

    private fun viewModelFor(docId: Long): DocumentDetailViewModel = DocumentDetailViewModel(
        dao = database.docDao(),
        context = context,
        savedStateHandle = SavedStateHandle(mapOf("docId" to docId)),
        appViewModel = AppErrorBus()
    )
}
