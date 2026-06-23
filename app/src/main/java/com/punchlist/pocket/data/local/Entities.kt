package com.punchlist.pocket.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a single job / project on the punch list.
 */
@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val client: String = "",
    val address: String = "",
    val description: String = "",
    /**
     * Optional app-private path to a cover image the user attached to the
     * project. Null when no image was set; the Home card then falls back to
     * a letter avatar. Stored as an absolute path inside filesDir/job_images.
     */
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * A single punch (inspection / to-do) item belonging to a [Job].
 */
@Entity(
    tableName = "punch_items",
    foreignKeys = [
        ForeignKey(
            entity = Job::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"])]
)
data class PunchItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val jobId: Long,
    val title: String,
    val description: String = "",
    val location: String = "",
    val trade: String = "",
    val status: String = STATUS_OPEN,
    val priority: String = PRIORITY_MEDIUM,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_OPEN = "OPEN"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_RESOLVED = "RESOLVED"

        const val PRIORITY_LOW = "LOW"
        const val PRIORITY_MEDIUM = "MEDIUM"
        const val PRIORITY_HIGH = "HIGH"
    }
}

/**
 * A photo attached to a [PunchItem].
 * [filePath] is an app-private file path relative to filesDir.
 */
@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = PunchItem::class,
            parentColumns = ["id"],
            childColumns = ["punchItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["punchItemId"])]
)
data class Photo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val punchItemId: Long,
    val filePath: String,
    val caption: String = "",
    val markedUp: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * A reusable template (e.g. "Final Walkthrough") that pre-populates a job
 * with a set of punch items.
 */
@Entity(tableName = "templates")
data class Template(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * One line of a [Template] that becomes a [PunchItem] when applied to a job.
 */
@Entity(
    tableName = "template_items",
    foreignKeys = [
        ForeignKey(
            entity = Template::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class TemplateItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val templateId: Long,
    val title: String,
    val description: String = "",
    val trade: String = "",
    val priority: String = PunchItem.PRIORITY_MEDIUM
)
