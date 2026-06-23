package com.punchlist.pocket.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.punchlist.pocket.data.local.Job
import com.punchlist.pocket.data.local.Photo
import com.punchlist.pocket.data.local.PunchItem
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralised file IO helper for PunchList Pocket. Owns all private-storage
 * directory creation, image persistence, and PDF generation.
 *
 * Every file produced by this helper lives inside the app's private filesDir,
 * so the app is fully offline-capable and never requires storage permissions on
 * API 30+.
 */
object FileHelper {

    private const val DIR_IMAGES = "job_images"
    private const val DIR_MARKUP = "markup_images"
    private const val DIR_PDF = "pdf_reports"

    private const val PAGE_WIDTH = 595 // A4 @ 72dpi in points (user units)
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36

    // --------------------------------------------------------------------
    // Directory setup
    // --------------------------------------------------------------------

    /** Ensures all private storage subdirectories exist; safe to call repeatedly. */
    fun ensureDirs(context: Context) {
        listOf(DIR_IMAGES, DIR_MARKUP, DIR_PDF).forEach { dirName ->
            File(context.filesDir, dirName).mkdirs()
        }
    }

    fun imageDir(context: Context): File =
        File(context.filesDir, DIR_IMAGES).apply { mkdirs() }

    fun markupDir(context: Context): File =
        File(context.filesDir, DIR_MARKUP).apply { mkdirs() }

    fun pdfDir(context: Context): File =
        File(context.filesDir, DIR_PDF).apply { mkdirs() }

    // --------------------------------------------------------------------
    // Image persistence
    // --------------------------------------------------------------------

    /**
     * Saves the supplied bitmap as a JPEG inside the markup directory. Returns
     * the absolute path on disk.
     */
    fun saveMarkupBitmap(context: Context, bitmap: Bitmap): String {
        ensureDirs(context)
        val file = File(markupDir(context), "markup_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file.absolutePath
    }

    /**
     * Writes raw bytes (e.g. captured from the camera) into the images
     * directory under a unique filename.
     */
    fun saveImageBytes(context: Context, bytes: ByteArray, prefix: String = "img"): String {
        ensureDirs(context)
        val file = File(imageDir(context), "${prefix}_${System.currentTimeMillis()}.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /**
     * Saves a bitmap that has been provided by an external capture flow (such
     * as the CameraX / ACTION_IMAGE_CAPTURE intent result) into the images dir.
     */
    fun saveImageBitmap(context: Context, bitmap: Bitmap, prefix: String = "img"): String {
        ensureDirs(context)
        val file = File(imageDir(context), "${prefix}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }
        return file.absolutePath
    }

    /**
     * Publishes [bitmap] to the device's public [MediaStore] (DCIM/PunchList on
     * Q+, or the legacy Pictures dir on older versions) so that:
     *   1. it shows up in the user's photo gallery, and
     *   2. the in-app [GalleryPickerSheet] (which queries MediaStore) surfaces
     *      it the next time the user browses — i.e. a photo just taken with the
     *      in-app camera is immediately selectable from the gallery grid.
     *
     * This complements [saveImageBitmap], which writes the app-private copy the
     * item row references. Both are called together on capture so the item has
     * a stable private path AND the image is discoverable via MediaStore.
     *
     * Returns the absolute path of the published file, or null on failure.
     */
    fun publishToMediaStore(context: Context, bitmap: Bitmap, prefix: String = "cam"): String? {
        val name = "${prefix}_${System.currentTimeMillis()}.jpg"
        return try {
            val resolver = context.contentResolver
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                // RELATIVE_PATH is Q+. Putting it unconditionally would throw
                // on older devices, so guard it.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DCIM}/PunchList"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            // We don't have a real filesystem path on scoped storage; the app
            // references photos by its private copy, so the caller only needs
            // to know the publish succeeded. Return a non-null sentinel.
            uri.toString()
        } catch (e: Exception) {
            null
        }
    }

    /** Decodes a stored image file (by absolute path) into a [Bitmap]. */
    fun loadBitmap(path: String): Bitmap? {
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Deletes a file at [path] if it exists. Used when removing photos or
     * overwriting a marked-up image.
     */
    fun deleteFile(path: String) {
        if (path.isBlank()) return
        runCatching { File(path).delete() }
    }

    /**
     * Builds a content [Uri] for sharing a private file with other apps via
     * FileProvider. Returns null if the file does not exist.
     */
    fun shareUri(context: Context, path: String): Uri? {
        val file = File(path)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    // --------------------------------------------------------------------
    // PDF generation
    // --------------------------------------------------------------------

    // Brand colors used across the report.
    private val pdfBrandBlue = Color.rgb(0x1F, 0x6F, 0xEB)
    private val pdfBrandBlueDark = Color.rgb(0x0D, 0x4D, 0xA3)
    private val pdfStatusOpen = Color.rgb(0xE0, 0x24, 0x24)
    private val pdfStatusInProgress = Color.rgb(0xFF, 0xB0, 0x20)
    private val pdfStatusResolved = Color.rgb(0x1A, 0x7F, 0x37)
    private val pdfInk = Color.rgb(0x1B, 0x1F, 0x24)
    private val pdfMuted = Color.rgb(0x6E, 0x77, 0x81)
    private val pdfDivider = Color.rgb(0xD0, 0xD7, 0xDE)
    /** Soft off-white used for cover sub-labels (90% opacity white). */
    private val pdfCoverLabelColor = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)

    /**
     * Mutable cursor for a multi-page flow. Holds the live [page] (so it can be
     * finished) alongside its canvas + current y position. Page ownership: the
     * page in [page] is finished exactly once — by [newBodyPageCanvas] when the
     * next page opens, or by [generateReport] for the final page.
     */
    private class PdfFlow(
        var page: PdfDocument.Page,
        var y: Int,
        val top: Int,
        val bottom: Int,
        var pageNumber: Int
    ) {
        val canvas: Canvas get() = page.canvas
    }

    /**
     * Renders a professional, client-ready punch-list report for [job]:
     *
     *  - Branded cover page: header band, job name, client, site address,
     *    generated date, and a status summary (Open / In Progress / Resolved
     *    counts).
     *  - Body grouped by status sections — Open, then In Progress, then
     *    Resolved — each item numbered within the report and showing priority,
     *    trade, location, description, and a tiled photo grid with a
     *    "Marked up" label where applicable.
     *  - Branded footer on every page (app name • "Offline by design").
     *
     * The result is written into the private PDF directory and the absolute
     * path is returned.
     *
     * @param photosByItem photos keyed by item id, in display order.
     */
    fun generateReport(
        context: Context,
        job: Job,
        items: List<PunchItem>,
        photosByItem: Map<Long, List<Photo>>
    ): String {
        ensureDirs(context)
        val doc = PdfDocument()

        // Paints ----------------------------------------------------------------
        val coverTitlePaint = Paint().apply {
            color = Color.WHITE; textSize = 30f; isAntiAlias = true; isFakeBoldText = true
        }
        val coverSubPaint = Paint().apply {
            color = Color.WHITE; textSize = 15f; isAntiAlias = true
        }
        val coverLabelPaint = Paint().apply {
            color = pdfCoverLabelColor
            textSize = 12f; isAntiAlias = true; isFakeBoldText = true
        }
        val coverMetaPaint = Paint().apply {
            color = Color.WHITE; textSize = 14f; isAntiAlias = true
        }
        val sectionPaint = Paint().apply {
            color = pdfBrandBlue; textSize = 15f; isAntiAlias = true; isFakeBoldText = true
        }
        val itemTitlePaint = Paint().apply {
            color = pdfInk; textSize = 14f; isAntiAlias = true; isFakeBoldText = true
        }
        val metaPaint = Paint().apply {
            color = pdfMuted; textSize = 11f; isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = pdfInk; textSize = 12f; isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = pdfDivider; strokeWidth = 1f; isAntiAlias = true
        }
        val footerPaint = Paint().apply {
            color = pdfMuted; textSize = 10f; isAntiAlias = true
        }
        val photoLabelPaint = Paint().apply {
            color = Color.WHITE; textSize = 10f; isAntiAlias = true; isFakeBoldText = true
        }
        val summaryLabelPaint = Paint().apply {
            color = pdfMuted; textSize = 11f; isAntiAlias = true; isFakeBoldText = true
        }
        val summaryValuePaint = Paint().apply {
            color = pdfInk; textSize = 22f; isAntiAlias = true; isFakeBoldText = true
        }

        val dateText = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())
        val jobName = job.name.ifBlank { "Untitled Job" }

        // Status buckets -------------------------------------------------------
        val open = items.filter { it.status == PunchItem.STATUS_OPEN }
        val inProgress = items.filter { it.status == PunchItem.STATUS_IN_PROGRESS }
        val resolved = items.filter { it.status == PunchItem.STATUS_RESOLVED }

        // ---------- Cover page ----------
        val cover = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        drawCoverPage(cover.canvas, jobName, job, dateText, items.size,
            open.size, inProgress.size, resolved.size,
            coverTitlePaint, coverSubPaint, coverLabelPaint, coverMetaPaint,
            summaryLabelPaint, summaryValuePaint, footerPaint, dividerPaint)
        doc.finishPage(cover)

        // ---------- Body (status sections) ----------
        if (items.isNotEmpty()) {
            val flowTop = MARGIN + 8
            val flowBottom = PAGE_HEIGHT - MARGIN - 16
            val firstPageNumber = 2
            val firstPage = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, firstPageNumber).create())
            var flow = PdfFlow(firstPage, flowTop, flowTop, flowBottom, firstPageNumber)

            // Page header strip on body pages.
            drawBodyHeader(flow.canvas, jobName, firstPageNumber, metaPaint, dividerPaint)

            fun startSection(title: String, color: Int, list: List<PunchItem>) {
                if (list.isEmpty()) return
                if (flow.y + 36 > flow.bottom) flow = newBodyPage(doc, flow, jobName, metaPaint, dividerPaint)
                flow.y += 8
                // Section header: small colored bar + title + count.
                val sectionBar = Paint().apply { this.color = color; isAntiAlias = true }
                flow.canvas.drawRect(
                    MARGIN.toFloat(), (flow.y - 11).toFloat(),
                    (MARGIN + 4).toFloat(), (flow.y + 3).toFloat(), sectionBar
                )
                flow.canvas.drawText(
                    "$title  (${list.size})",
                    (MARGIN + 12).toFloat(), flow.y.toFloat(), sectionPaint
                )
                flow.y += 16
                flow.canvas.drawLine(
                    MARGIN.toFloat(), flow.y.toFloat(),
                    (PAGE_WIDTH - MARGIN).toFloat(), flow.y.toFloat(), dividerPaint
                )
                flow.y += 18

                list.forEachIndexed { index, item ->
                    drawItem(flow, doc, jobName, metaPaint, dividerPaint, itemTitlePaint,
                        bodyPaint, photoLabelPaint, item, index + 1, photosByItem[item.id].orEmpty(),
                        context, flowTop, flowBottom)
                    // Spacer between items.
                    if (flow.y + 10 < flow.bottom) flow.y += 10
                }
            }

            startSection("OPEN", pdfStatusOpen, open)
            startSection("IN PROGRESS", pdfStatusInProgress, inProgress)
            startSection("RESOLVED", pdfStatusResolved, resolved)

            // Footer on the final body page, then finish it. flow.page is the
            // page we're currently on (which may be a later page than firstPage
            // if pagination occurred; intermediate pages were already finished
            // by newBodyPage/newBodyPageCanvas).
            drawFooter(flow.canvas, flow.pageNumber, jobName, footerPaint)
            doc.finishPage(flow.page)
        }

        val outFileName = "report_${job.id}_${System.currentTimeMillis()}.pdf"
        val outFile = File(pdfDir(context), outFileName)
        FileOutputStream(outFile).use { out: OutputStream ->
            doc.writeTo(out)
        }
        doc.close()
        return outFile.absolutePath
    }

    /** Draws the branded cover page: header band, meta block, status summary. */
    private fun drawCoverPage(
        canvas: Canvas,
        jobName: String,
        job: Job,
        dateText: String,
        total: Int,
        open: Int,
        inProgress: Int,
        resolved: Int,
        titlePaint: Paint, subPaint: Paint, labelPaint: Paint, metaPaint: Paint,
        summaryLabelPaint: Paint, summaryValuePaint: Paint,
        footerPaint: Paint, dividerPaint: Paint
    ) {
        // Top brand band.
        val bandPaint = Paint().apply { color = pdfBrandBlue; isAntiAlias = true }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 150f, bandPaint)
        // Darker accent strip under the band for a subtle two-tone look.
        val accentPaint = Paint().apply { color = pdfBrandBlueDark; isAntiAlias = true }
        canvas.drawRect(0f, 146f, PAGE_WIDTH.toFloat(), 150f, accentPaint)

        canvas.drawText("PUNCHLIST POCKET", MARGIN.toFloat(), 52f, labelPaint)
        canvas.drawText("Punch List Report", MARGIN.toFloat(), 82f, subPaint)
        canvas.drawText(jobName, MARGIN.toFloat(), 122f, titlePaint)

        // Meta block.
        var y = 210
        val metaLabel = { text: String ->
            canvas.drawText(text, MARGIN.toFloat(), y.toFloat(), labelPaint); y += 20
        }
        val metaValue = { text: String ->
            canvas.drawText(text, MARGIN.toFloat(), y.toFloat(), metaPaint); y += 28
        }
        if (job.client.isNotBlank()) { metaLabel("CLIENT"); metaValue(job.client) }
        if (job.address.isNotBlank()) {
            metaLabel("SITE ADDRESS")
            y = drawWrappedText(canvas, job.address, metaPaint, MARGIN, y, PAGE_WIDTH - MARGIN, 20) + 8
        }
        metaLabel("DATE GENERATED")
        canvas.drawText(dateText, MARGIN.toFloat(), y.toFloat(), metaPaint)
        y += 30

        // Status summary table: 4 equal cells (Total, Open, In Progress, Resolved).
        canvas.drawLine(
            MARGIN.toFloat(), (y + 4).toFloat(),
            (PAGE_WIDTH - MARGIN).toFloat(), (y + 4).toFloat(), dividerPaint
        )
        y += 14
        val cellW = (PAGE_WIDTH - 2 * MARGIN) / 4
        val summary = listOf(
            "Total" to total to pdfInk,
            "Open" to open to pdfStatusOpen,
            "In Progress" to inProgress to pdfStatusInProgress,
            "Resolved" to resolved to pdfStatusResolved
        )
        // Filled value paint per cell so the number carries the status color.
        summary.forEachIndexed { i, entry ->
            val (pair, color) = entry
            val (label, count) = pair
            val cx = MARGIN + i * cellW
            canvas.drawText(
                label,
                (cx + 12).toFloat(),
                y.toFloat(),
                summaryLabelPaint
            )
            val valuePaint = Paint(summaryValuePaint).apply { this.color = color }
            canvas.drawText(
                count.toString(),
                (cx + 12).toFloat(),
                (y + 28).toFloat(),
                valuePaint
            )
        }
        y += 48
        canvas.drawLine(
            MARGIN.toFloat(), y.toFloat(),
            (PAGE_WIDTH - MARGIN).toFloat(), y.toFloat(), dividerPaint
        )

        drawFooter(canvas, 1, jobName, footerPaint)
    }

    /** Header strip on body pages: job name left, page number right. */
    private fun drawBodyHeader(canvas: Canvas, jobName: String, page: Int, metaPaint: Paint, dividerPaint: Paint) {
        canvas.drawText(jobName, MARGIN.toFloat(), (MARGIN - 6).toFloat(), metaPaint)
        val pageText = "Page $page"
        val w = metaPaint.measureText(pageText)
        canvas.drawText(pageText, (PAGE_WIDTH - MARGIN - w), (MARGIN - 6).toFloat(), metaPaint)
        canvas.drawLine(
            MARGIN.toFloat(), MARGIN.toFloat(),
            (PAGE_WIDTH - MARGIN).toFloat(), MARGIN.toFloat(), dividerPaint
        )
    }

    /** Footer line: app brand left, "Offline by design" right. */
    private fun drawFooter(canvas: Canvas, page: Int, jobName: String, footerPaint: Paint) {
        val y = PAGE_HEIGHT - MARGIN + 14
        canvas.drawText("PunchList Pocket  •  $jobName", MARGIN.toFloat(), y.toFloat(), footerPaint)
        val tag = "Offline by design"
        val w = footerPaint.measureText(tag)
        canvas.drawText(tag, (PAGE_WIDTH - MARGIN - w), y.toFloat(), footerPaint)
    }

    /** Starts a fresh body page, finishing the previous one, and returns a new flow. */
    private fun newBodyPage(
        doc: PdfDocument, prev: PdfFlow, jobName: String, metaPaint: Paint, dividerPaint: Paint
    ): PdfFlow {
        // Footer for the page we're closing, then finish it so it flushes in order.
        drawFooter(prev.canvas, prev.pageNumber, jobName, metaPaint)
        doc.finishPage(prev.page)
        val pageNumber = prev.pageNumber + 1
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        val flow = PdfFlow(page, prev.top, prev.top, prev.bottom, pageNumber)
        drawBodyHeader(flow.canvas, jobName, pageNumber, metaPaint, dividerPaint)
        return flow
    }

    /**
     * Draws a single numbered punch item, paginating when it doesn't fit. Each
     * item shows: "#N Title" header, priority/trade/location meta line, wrapped
     * description, and a 2-per-row photo grid with a "Marked up" tag on photos
     * that have been annotated.
     */
    private fun drawItem(
        flow: PdfFlow,
        doc: PdfDocument,
        jobName: String,
        metaPaint: Paint,
        dividerPaint: Paint,
        itemTitlePaint: Paint,
        bodyPaint: Paint,
        photoLabelPaint: Paint,
        item: PunchItem,
        number: Int,
        photos: List<Photo>,
        context: Context,
        top: Int,
        bottom: Int
    ) {
        // Item header: "#N Title". Page-break if the header won't fit.
        if (flow.y + 20 > flow.bottom) {
            newBodyPageCanvas(doc, flow, jobName, metaPaint, dividerPaint, top, bottom)
        }
        flow.canvas.drawText(
            "#$number  ${item.title.ifBlank { "Untitled Item" }}",
            MARGIN.toFloat(), flow.y.toFloat(), itemTitlePaint
        )
        flow.y += 16

        // Meta line: priority, trade, location (only non-blank).
        val metaParts = buildList {
            add("Priority: ${pretty(item.priority)}")
            if (item.trade.isNotBlank()) add("Trade: ${item.trade}")
            if (item.location.isNotBlank()) add("Location: ${item.location}")
        }
        flow.canvas.drawText(metaParts.joinToString("    •    "), MARGIN.toFloat(), flow.y.toFloat(), metaPaint)
        flow.y += 14

        // Description, wrapped.
        if (item.description.isNotBlank()) {
            if (flow.y + 16 > flow.bottom) {
                newBodyPageCanvas(doc, flow, jobName, metaPaint, dividerPaint, top, bottom)
            }
            flow.y = drawWrappedText(flow.canvas, item.description, bodyPaint, MARGIN, flow.y, PAGE_WIDTH - MARGIN)
            flow.y += 6
        }

        // Photos as a 2-per-row grid with "Marked up" tags.
        if (photos.isNotEmpty()) {
            drawPhotoGrid(flow, doc, jobName, metaPaint, dividerPaint, photoLabelPaint, photos, context, top, bottom)
        }

        // Divider under the item.
        if (flow.y + 6 > flow.bottom) {
            newBodyPageCanvas(doc, flow, jobName, metaPaint, dividerPaint, top, bottom)
        }
        flow.canvas.drawLine(
            MARGIN.toFloat(), flow.y.toFloat(),
            (PAGE_WIDTH - MARGIN).toFloat(), flow.y.toFloat(), dividerPaint
        )
        flow.y += 8
    }

    /**
     * Draws the photos for one item as a 2-per-row grid, paginating row by row.
     * Each cell is square, captioned "Marked up" when the photo was annotated.
     */
    private fun drawPhotoGrid(
        flow: PdfFlow,
        doc: PdfDocument,
        jobName: String,
        metaPaint: Paint,
        dividerPaint: Paint,
        photoLabelPaint: Paint,
        photos: List<Photo>,
        context: Context,
        top: Int,
        bottom: Int
    ) {
        val perRow = 2
        val gap = 12
        val cellSize = (PAGE_WIDTH - 2 * MARGIN - gap) / perRow
        val labelH = 16
        val rowH = cellSize + labelH + gap

        // Decode + scale once per photo.
        val bitmaps = photos.mapNotNull { p ->
            loadBitmap(p.filePath)?.let { Bitmap.createScaledBitmap(it, cellSize, cellSize, true) to p.markedUp }
        }
        if (bitmaps.isEmpty()) {
            flow.canvas.drawText("No photos attached.", MARGIN.toFloat(), flow.y.toFloat(), metaPaint)
            flow.y += 16
            return
        }

        bitmaps.chunked(perRow).forEach { row ->
            if (flow.y + rowH > flow.bottom) {
                newBodyPageCanvas(doc, flow, jobName, metaPaint, dividerPaint, top, bottom)
            }
            val rowTop = flow.y
            row.forEachIndexed { col, (bmp, markedUp) ->
                val left = MARGIN + col * (cellSize + gap)
                // White background + thin border frame.
                val framePaint = Paint().apply { color = pdfDivider; isAntiAlias = true; strokeWidth = 1f; style = Paint.Style.STROKE }
                flow.canvas.drawRect(
                    left.toFloat(), rowTop.toFloat(),
                    (left + cellSize).toFloat(), (rowTop + cellSize).toFloat(), framePaint
                )
                flow.canvas.drawBitmap(bmp, left.toFloat(), rowTop.toFloat(), null)
                // "Marked up" tag on annotated photos.
                if (markedUp) {
                    val tag = "Marked up"
                    val tagW = photoLabelPaint.measureText(tag) + 14
                    val tagPaint = Paint().apply { color = pdfBrandBlue; isAntiAlias = true }
                    flow.canvas.drawRect(
                        (left + 6).toFloat(), (rowTop + 6).toFloat(),
                        (left + 6 + tagW).toFloat(), (rowTop + 22).toFloat(), tagPaint
                    )
                    flow.canvas.drawText(tag, (left + 13).toFloat(), (rowTop + 18).toFloat(), photoLabelPaint)
                }
            }
            flow.y = rowTop + cellSize + labelH
        }
        flow.y += gap
    }

    /**
     * Paginates: finishes the current page, opens the next, re-points [flow] at
     * it, and returns the new canvas. This way every page is finished exactly
     * once (the final page is finished by [generateReport] after the loop).
     */
    private fun newBodyPageCanvas(
        doc: PdfDocument,
        flow: PdfFlow,
        jobName: String,
        metaPaint: Paint,
        dividerPaint: Paint,
        top: Int,
        bottom: Int
    ): Canvas {
        drawFooter(flow.canvas, flow.pageNumber, jobName, metaPaint)
        doc.finishPage(flow.page)
        val pageNumber = flow.pageNumber + 1
        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        flow.page = page
        flow.pageNumber = pageNumber
        flow.y = top
        drawBodyHeader(page.canvas, jobName, pageNumber, metaPaint, dividerPaint)
        return page.canvas
    }

    /** Returns the available width after wrapping [text] within [left, right]. */
    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        left: Int,
        startY: Int,
        right: Int,
        lineHeight: Int = 15
    ): Int {
        val maxWidth = (right - left).toFloat()
        val words = text.split(" ")
        var current = StringBuilder()
        var y = startY
        for (word in words) {
            val test = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(test) > maxWidth && current.isNotEmpty()) {
                canvas.drawText(current.toString(), left.toFloat(), y.toFloat(), paint)
                y += lineHeight
                current = StringBuilder(word)
            } else {
                current = StringBuilder(test)
            }
        }
        if (current.isNotEmpty()) {
            canvas.drawText(current.toString(), left.toFloat(), y.toFloat(), paint)
            y += lineHeight
        }
        return y
    }

    private fun pretty(value: String): String =
        value.split("_").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
}

