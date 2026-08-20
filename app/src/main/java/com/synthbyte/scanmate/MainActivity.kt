package com.synthbyte.scanmate

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.fragment.app.FragmentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.synthbyte.scanmate.core.SafeLogger
import com.synthbyte.scanmate.data.SettingsRepository
import com.synthbyte.scanmate.data.ThemeMode
import com.synthbyte.scanmate.ui.components.GlobalErrorBoundary
import com.synthbyte.scanmate.ui.viewmodels.AppViewModel
import com.synthbyte.scanmate.ui.navigation.Routes
import com.synthbyte.scanmate.ui.screens.AiScreen
import com.synthbyte.scanmate.ui.screens.CameraScreen
import com.synthbyte.scanmate.ui.screens.DocumentDetailScreen
import com.synthbyte.scanmate.ui.screens.FileManagerScreen
import com.synthbyte.scanmate.ui.screens.HomeScreen
import com.synthbyte.scanmate.ui.screens.PageEditorScreen
import com.synthbyte.scanmate.ui.screens.PdfToolsScreen
import com.synthbyte.scanmate.ui.screens.OcrTranslateScreen
import com.synthbyte.scanmate.ui.screens.OnboardingScreen
import com.synthbyte.scanmate.ui.screens.QrScannerScreen
import com.synthbyte.scanmate.ui.screens.QrScreen
import com.synthbyte.scanmate.ui.screens.SettingsScreen
import com.synthbyte.scanmate.ui.screens.ToolsScreen
import com.synthbyte.scanmate.ui.screens.SignatureScreen
import com.synthbyte.scanmate.ui.screens.ZipScreen
import com.synthbyte.scanmate.ui.screens.VaultScreen
import com.synthbyte.scanmate.ui.theme.ScanMateTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private var shortcutRouteState: MutableState<String>? = null
    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val previousCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            writeCrashLog(throwable)
            SafeLogger.e("ScanMate", "Uncaught app crash", throwable)
            if (previousCrashHandler != null && previousCrashHandler !== Thread.getDefaultUncaughtExceptionHandler()) {
                previousCrashHandler.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
        enableEdgeToEdge()
        // Was a synchronous main-thread Binder IPC to the shortcut service before
        // setContent — blocked first-frame render on every cold start. Moved off
        // the main thread since shortcuts don't need to exist before the UI does.
        lifecycleScope.launch(Dispatchers.Default) { publishLauncherShortcuts() }
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val themeMode by settingsRepository.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            val onboardingCompleteState by settingsRepository.onboardingCompleteFlow.collectAsState(initial = null)
            val scope = rememberCoroutineScope()
            val requestedShortcutRoute = remember {
                mutableStateOf(normalizeShortcutRoute(intent?.getStringExtra(EXTRA_SHORTCUT_ROUTE)))
            }
            shortcutRouteState = requestedShortcutRoute

            ScanMateTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val onboardingComplete = onboardingCompleteState
                    if (onboardingComplete == null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val navController = rememberNavController()

                        LaunchedEffect(requestedShortcutRoute.value) {
                            val route = requestedShortcutRoute.value
                            if (route != Routes.HOME) {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                requestedShortcutRoute.value = Routes.HOME
                            }
                        }

                        val startDestination = if (onboardingComplete) Routes.HOME else Routes.ONBOARDING
                        GlobalErrorBoundary(appViewModel = appViewModel) {
                        NavHost(navController = navController, startDestination = startDestination) {
                        composable(Routes.ONBOARDING) {
                            OnboardingScreen(
                                onFinish = {
                                    scope.launch { settingsRepository.setOnboardingComplete(true) }
                                    navController.navigate(Routes.HOME) {
                                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }
                        composable(Routes.HOME) {
                            HomeScreen(
                                onNavigateToCamera = { navController.navigate(Routes.CAMERA_SCAN) },
                                onNavigateToDoc = { id -> navController.navigate(Routes.docDetail(id)) },
                                onNavigateToQr = { navController.navigate(Routes.QR_TOOLS) },
                                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                onNavigateToAi = { navController.navigate(Routes.AI_ASSISTANT) },
                                onNavigateToZip = { navController.navigate(Routes.ZIP_TOOLS) },
                                onNavigateToFiles = { navController.navigate(Routes.FILE_MANAGER) },
                                onNavigateToPdfTools = { navController.navigate(Routes.PDF_TOOLS) },
                                onNavigateToTranslate = { navController.navigate(Routes.OCR_TRANSLATE) },
                                onNavigateToVault = { navController.navigate(Routes.VAULT) },
                                onNavigateToTools = { navController.navigate(Routes.TOOLS) },
                                settingsRepository = settingsRepository
                            )
                        }
                        composable(Routes.CAMERA_SCAN) {
                            CameraScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onScanFinished = { id ->
                                    navController.popBackStack()
                                    navController.navigate(Routes.docDetail(id))
                                },
                                settingsRepository = settingsRepository
                            )
                        }
                        composable(
                            route = Routes.DOC_DETAIL,
                            arguments = listOf(navArgument("docId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val docIdArg = backStackEntry.arguments?.getLong("docId") ?: 0L
                            DocumentDetailScreen(
                                docId = docIdArg,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPageEditor = { documentId, pageId -> navController.navigate(Routes.pageEditor(documentId, pageId)) },
                                onNavigateToSignature = { documentId -> navController.navigate(Routes.signature(documentId)) }
                            )
                        }

                        composable(
                            route = Routes.PAGE_EDITOR,
                            arguments = listOf(
                                navArgument("docId") { type = NavType.LongType },
                                navArgument("pageId") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val docIdArg = backStackEntry.arguments?.getLong("docId") ?: 0L
                            val pageIdArg = backStackEntry.arguments?.getLong("pageId") ?: 0L
                            PageEditorScreen(
                                docId = docIdArg,
                                pageId = pageIdArg,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.TOOLS) {
                            ToolsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToPdfTools = { navController.navigate(Routes.PDF_TOOLS) },
                                onNavigateToQr = { navController.navigate(Routes.QR_TOOLS) },
                                onNavigateToQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                                onNavigateToTranslate = { navController.navigate(Routes.OCR_TRANSLATE) },
                                onNavigateToAi = { navController.navigate(Routes.AI_ASSISTANT) },
                                onNavigateToVault = { navController.navigate(Routes.VAULT) },
                                onNavigateToZip = { navController.navigate(Routes.ZIP_TOOLS) },
                                onNavigateToFiles = { navController.navigate(Routes.FILE_MANAGER) },
                                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                                onNavigateToCamera = { navController.navigate(Routes.CAMERA_SCAN) }
                            )
                        }
                        composable(Routes.PDF_TOOLS) {
                            PdfToolsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(
                            route = Routes.SIGNATURE,
                            arguments = listOf(navArgument("docId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val docIdArg = backStackEntry.arguments?.getLong("docId") ?: 0L
                            SignatureScreen(
                                docId = docIdArg,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.QR_TOOLS) {
                            QrScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onOpenCameraScanner = { navController.navigate(Routes.QR_SCANNER) }
                            )
                        }
                        composable(Routes.QR_SCANNER) {
                            QrScannerScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                settingsRepository = settingsRepository
                            )
                        }
                        composable(Routes.AI_ASSISTANT) {
                            AiScreen(
                                onNavigateBack = { navController.popBackStack() },
                                settingsRepository = settingsRepository
                            )
                        }
                        composable(Routes.OCR_TRANSLATE) {
                            OcrTranslateScreen(
                                onNavigateBack = { navController.popBackStack() },
                                settingsRepository = settingsRepository
                            )
                        }
                        composable(Routes.ZIP_TOOLS) {
                            ZipScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.FILE_MANAGER) {
                            FileManagerScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(Routes.VAULT) {
                            VaultScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                    }
                }
                }
            }
        }
    }

    private fun writeCrashLog(throwable: Throwable) {
        runCatching {
            val logFile = java.io.File(filesDir, "crash_log.txt")
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", java.util.Locale.US).format(java.util.Date())
            val entry = buildString {
                appendLine(); appendLine("---"); appendLine(timestamp)
                appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                appendLine("Thread: ${Thread.currentThread().name}")
                appendLine(throwable.stackTraceToString())
            }
            logFile.appendText(entry)
            if (logFile.length() > 256_000L) {
                runCatching { logFile.writeText(logFile.readText().takeLast(128_000)) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        shortcutRouteState?.value = normalizeShortcutRoute(intent.getStringExtra(EXTRA_SHORTCUT_ROUTE))
    }

    override fun onDestroy() {
        shortcutRouteState = null
        super.onDestroy()
    }

    private fun publishLauncherShortcuts() {
        val shortcuts = listOf(
            buildShortcut("scan", "Scan", "Open camera scanner", Routes.CAMERA_SCAN),
            buildShortcut("ai", "AI", "Open AI Workspace", Routes.AI_ASSISTANT),
            buildShortcut("qr", "QR", "Open QR tools", Routes.QR_TOOLS),
            buildShortcut("pdf", "PDF", "Open PDF tools", Routes.PDF_TOOLS)
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(this, shortcuts) }
    }

    private fun buildShortcut(id: String, label: String, longLabel: String, route: String): ShortcutInfoCompat {
        val shortcutIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_SHORTCUT_ROUTE, route)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        return ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
            .setIntent(shortcutIntent)
            .build()
    }

    private fun normalizeShortcutRoute(raw: String?): String {
        return when (raw) {
            Routes.CAMERA_SCAN, Routes.AI_ASSISTANT, Routes.QR_TOOLS, Routes.PDF_TOOLS, Routes.OCR_TRANSLATE -> raw
            else -> Routes.HOME
        }
    }

    companion object {
        const val EXTRA_SHORTCUT_ROUTE = "shortcut_route"
    }
}
