package com.punchlist.pocket.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.punchlist.pocket.ui.screens.home.HomeScreen
import com.punchlist.pocket.ui.screens.item.AddEditItemScreen
import com.punchlist.pocket.ui.screens.job.AddEditJobScreen
import com.punchlist.pocket.ui.screens.job.JobDetailScreen
import com.punchlist.pocket.ui.screens.markup.MarkupScreen
import com.punchlist.pocket.ui.screens.pdf.PdfPreviewScreen
import com.punchlist.pocket.ui.screens.template.AddEditTemplateScreen
import com.punchlist.pocket.ui.screens.template.TemplateDetailScreen
import com.punchlist.pocket.ui.screens.template.TemplatesScreen

object Routes {
    const val HOME = "home"

    const val JOB_DETAIL = "job/{jobId}"
    fun jobDetail(jobId: Long) = "job/$jobId"

    const val ADD_EDIT_JOB = "add_edit_job/{jobId}"
    fun addEditJob(jobId: Long = NEW_ID) = "add_edit_job/$jobId"
    const val NEW_ID = -1L

    const val ADD_EDIT_ITEM = "add_edit_item/{jobId}/{itemId}/{photoId}"
    fun addEditItem(jobId: Long, itemId: Long = NEW_ID, photoId: Long = NEW_ID) =
        "add_edit_item/$jobId/$itemId/$photoId"

    const val MARKUP = "markup/{photoId}"
    fun markup(photoId: Long) = "markup/$photoId"

    const val PDF_PREVIEW = "pdf_preview/{jobId}"
    fun pdfPreview(jobId: Long) = "pdf_preview/$jobId"

    /**
     * Templates browser. Optional [pickerJobId] puts it in "apply to job" mode;
     * a value of [NO_JOB] means standalone management mode. Passed as a Long
     * arg and converted to a nullable on the screen side.
     */
    const val TEMPLATES = "templates/{pickerJobId}"
    fun templates(pickerJobId: Long? = null) = "templates/${pickerJobId ?: NO_JOB}"
    const val NO_JOB: Long = -2L

    const val TEMPLATE_DETAIL = "template/{templateId}/{pickerJobId}"
    fun templateDetail(templateId: Long, pickerJobId: Long? = null) =
        "template/$templateId/${pickerJobId ?: NO_JOB}"

    const val ADD_EDIT_TEMPLATE = "add_edit_template/{templateId}"
    fun addEditTemplate(templateId: Long = NEW_ID) = "add_edit_template/$templateId"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    // The bottom navigation bar (Jobs / My Items / Reports) was removed — Home
    // is now the single root and all destinations live behind it. The NavHost
    // fills the screen; each destination's own Scaffold handles its insets via
    // enableEdgeToEdge.
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = Modifier.fillMaxSize()
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onJobClick = { job -> navController.navigate(Routes.jobDetail(job.id)) },
                onJobPdf = { job -> navController.navigate(Routes.pdfPreview(job.id)) },
                onAddJob = { navController.navigate(Routes.addEditJob()) },
                onOpenTemplates = { navController.navigate(Routes.templates()) }
            )
        }

        composable(
            route = Routes.JOB_DETAIL,
            arguments = listOf(navArgument("jobId") { type = NavType.LongType })
        ) { entry ->
            val jobId = entry.arguments?.getLong("jobId") ?: 0L
            JobDetailScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() },
                onEditJob = { navController.navigate(Routes.addEditJob(jobId)) },
                onAddItem = { navController.navigate(Routes.addEditItem(jobId)) },
                onEditItem = { item ->
                    navController.navigate(Routes.addEditItem(jobId, item.id))
                },
                onPdf = { navController.navigate(Routes.pdfPreview(jobId)) },
                onApplyTemplate = { navController.navigate(Routes.templates(jobId)) }
            )
        }

        composable(
            route = Routes.ADD_EDIT_JOB,
            arguments = listOf(navArgument("jobId") { type = NavType.LongType })
        ) { entry ->
            val jobId = entry.arguments?.getLong("jobId") ?: Routes.NEW_ID
            AddEditJobScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ADD_EDIT_ITEM,
            arguments = listOf(
                navArgument("jobId") { type = NavType.LongType },
                navArgument("itemId") { type = NavType.LongType },
                navArgument("photoId") { type = NavType.LongType }
            )
        ) { entry ->
            val jobId = entry.arguments?.getLong("jobId") ?: 0L
            val itemId = entry.arguments?.getLong("itemId") ?: Routes.NEW_ID
            AddEditItemScreen(
                jobId = jobId,
                itemId = itemId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onMarkup = { photoId -> navController.navigate(Routes.markup(photoId)) }
            )
        }

        composable(
            route = Routes.MARKUP,
            arguments = listOf(navArgument("photoId") { type = NavType.LongType })
        ) { entry ->
            val photoId = entry.arguments?.getLong("photoId") ?: 0L
            MarkupScreen(
                photoId = photoId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.PDF_PREVIEW,
            arguments = listOf(navArgument("jobId") { type = NavType.LongType })
        ) { entry ->
            val jobId = entry.arguments?.getLong("jobId") ?: 0L
            PdfPreviewScreen(
                jobId = jobId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.TEMPLATES,
            arguments = listOf(navArgument("pickerJobId") { type = NavType.LongType })
        ) { entry ->
            val raw = entry.arguments?.getLong("pickerJobId") ?: Routes.NO_JOB
            val pickerJobId: Long? = if (raw == Routes.NO_JOB) null else raw
            TemplatesScreen(
                pickerJobId = pickerJobId,
                onBack = { navController.popBackStack() },
                onOpenTemplate = { id ->
                    navController.navigate(Routes.templateDetail(id, pickerJobId))
                },
                onAddTemplate = { navController.navigate(Routes.addEditTemplate()) }
            )
        }

        composable(
            route = Routes.TEMPLATE_DETAIL,
            arguments = listOf(
                navArgument("templateId") { type = NavType.LongType },
                navArgument("pickerJobId") { type = NavType.LongType }
            )
        ) { entry ->
            val templateId = entry.arguments?.getLong("templateId") ?: 0L
            val raw = entry.arguments?.getLong("pickerJobId") ?: Routes.NO_JOB
            val pickerJobId: Long? = if (raw == Routes.NO_JOB) null else raw
            TemplateDetailScreen(
                templateId = templateId,
                jobId = pickerJobId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.addEditTemplate(templateId)) }
            )
        }

        composable(
            route = Routes.ADD_EDIT_TEMPLATE,
            arguments = listOf(navArgument("templateId") { type = NavType.LongType })
        ) { entry ->
            val templateId = entry.arguments?.getLong("templateId") ?: Routes.NEW_ID
            AddEditTemplateScreen(
                templateId = templateId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}

