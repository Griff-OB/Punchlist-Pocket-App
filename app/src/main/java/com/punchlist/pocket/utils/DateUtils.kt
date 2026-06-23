package com.punchlist.pocket.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Shared date + status helpers used across Home, Job Detail, and Add/Edit
 * Item. Centralises formatting that was previously duplicated.
 */
object DateUtils {

    private val dayMonth: SimpleDateFormat =
        SimpleDateFormat("MMM d", Locale.getDefault())
    private val fullDate: SimpleDateFormat =
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    /** "Jun 22" style label, or null when there is no due date. */
    fun formatDueDate(epochMillis: Long?): String? =
        epochMillis?.let { dayMonth.format(Date(it)) }

    /** "Jun 22, 2026" style label, or null when there is no due date. */
    fun formatFullDate(epochMillis: Long?): String? =
        epochMillis?.let { fullDate.format(Date(it)) }

    /** "Updated <date>" string for job/ item rows. */
    fun formatUpdated(epochMillis: Long): String = fullDate.format(Date(epochMillis))

    /**
     * The start of today in the device's local timezone, as epoch millis.
     * Used as the boundary for overdue / due-today comparisons so that an
     * item due "today" is not flagged overdue until the day rolls over.
     */
    fun startOfToday(now: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Converts a Material3 DatePicker `selectedDateMillis` (the chosen day at
     * 00:00 **UTC**) into the matching local-midnight millis for that day.
     *
     * The picker intentionally works in UTC, so storing its raw value and then
     * formatting/comparing it in the device timezone shifts it by up to a day
     * (selecting the 20th would display as the 19th in timezones behind UTC).
     * Aligning to local midnight here keeps the displayed and stored day in
     * sync and makes every other comparison (overdue / due soon / formatting)
     * consistent.
     */
    fun datePickerMillisToLocalDay(selectedDateMillis: Long): Long {
        // Read the Y/M/D the picker meant in UTC, then rebuild the same
        // calendar date at local 00:00.
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selectedDateMillis
        }
        return Calendar.getInstance().apply {
            clear()
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH)
            )
        }.timeInMillis
    }

    /**
     * Inverse of [datePickerMillisToLocalDay]: turns a local-midnight millis
     * (the format we store) into the UTC-midnight millis the Material3
     * DatePicker expects for its `initialSelectedDateMillis` seed, so the
     * picker highlights the same day the stored value represents.
     */
    fun localDayMillisToUtcDay(localDayMillis: Long): Long {
        val localCal = Calendar.getInstance().apply { timeInMillis = localDayMillis }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH)
            )
        }.timeInMillis
    }

    /** Relative description of a job's last update. */
    fun relativeUpdated(updatedAt: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfToday(now)
        val updatedDay = startOfToday(updatedAt)
        val dayMs = 24L * 60 * 60 * 1000
        return when {
            updatedDay == today -> "Updated today"
            updatedDay == today - dayMs -> "Updated yesterday"
            now - updatedAt < 7 * dayMs -> "Updated this week"
            else -> "Updated ${fullDate.format(Date(updatedAt))}"
        }
    }

    /**
     * Pretty-prints a snake-case constant. Special-cases "RESOLVED" → "Completed"
     * so the status chip on the Add/Edit Item form and the Job Detail pills match
     * the "Completed" wording used on the Home dashboard; everything else
     * title-cases word-by-word ("IN_PROGRESS" → "In Progress").
     */
    fun pretty(value: String): String {
        if (value == "RESOLVED") return "Completed"
        return value.split("_").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }
        }
    }

    /** Stable ordering: HIGH before MEDIUM before LOW; unknown last. */
    fun priorityRank(priority: String): Int = when (priority) {
        "HIGH" -> 0
        "MEDIUM" -> 1
        "LOW" -> 2
        else -> 3
    }

    /** Stable ordering: OPEN before IN_PROGRESS before RESOLVED; unknown last. */
    fun statusRank(status: String): Int = when (status) {
        "OPEN" -> 0
        "IN_PROGRESS" -> 1
        "RESOLVED" -> 2
        else -> 3
    }
}

/**
 * How a punch item's due date relates to today. Carries the label + the
 * semantic bucket so the UI can pick colors and decide what to render.
 */
enum class DueDateStatus(val label: String) {
    None("No due date"),
    Overdue("Overdue"),
    DueToday("Due today"),
    Future("Upcoming");

    companion object {
        fun from(dueDate: Long?, now: Long = System.currentTimeMillis(), isResolved: Boolean): DueDateStatus {
            if (dueDate == null) return None
            val today = DateUtils.startOfToday(now)
            return when {
                isResolved -> None
                dueDate < today -> Overdue
                dueDate < today + 24L * 60 * 60 * 1000 -> DueToday
                else -> Future
            }
        }

        /** Short "Due: Jun 22" / "No due date" line for cards. */
        fun cardLine(dueDate: Long?, now: Long = System.currentTimeMillis()): String {
            val formatted = DateUtils.formatDueDate(dueDate) ?: return "No due date"
            val status = from(dueDate, now, isResolved = false)
            return when (status) {
                Overdue -> "Overdue · was $formatted"
                DueToday -> "Due today"
                else -> "Due $formatted"
            }
        }
    }
}
