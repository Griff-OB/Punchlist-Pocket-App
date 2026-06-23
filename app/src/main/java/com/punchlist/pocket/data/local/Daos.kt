package com.punchlist.pocket.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Result row of a `GROUP BY status` count query. Room maps the two columns by
 * name; both are plain fields (no annotation needed) as long as their names
 * match the query aliases.
 */
data class StatusCount(
    val status: String,
    val count: Int
)

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE name LIKE '%' || :query || '%' OR client LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: Long): Job?

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeById(id: Long): Flow<Job?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: Job): Long

    @Update
    suspend fun update(job: Job)

    @Delete
    suspend fun delete(job: Job)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PunchItemDao {

    @Query("SELECT * FROM punch_items WHERE jobId = :jobId ORDER BY updatedAt DESC")
    fun observeByJob(jobId: Long): Flow<List<PunchItem>>

    @Query("SELECT * FROM punch_items WHERE jobId = :jobId AND status = :status ORDER BY updatedAt DESC")
    fun observeByJobAndStatus(jobId: Long, status: String): Flow<List<PunchItem>>

    @Query("SELECT * FROM punch_items WHERE jobId = :jobId AND priority = :priority ORDER BY updatedAt DESC")
    fun observeByJobAndPriority(jobId: Long, priority: String): Flow<List<PunchItem>>

    @Query("SELECT COUNT(*) FROM punch_items WHERE jobId = :jobId")
    fun observeItemCount(jobId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM punch_items WHERE jobId = :jobId")
    suspend fun getItemCount(jobId: Long): Int

    /**
     * Counts grouped by status for a single job. Returns one row per present
     * status as a list of [StatusCount] values; absent statuses are simply
     * missing (treated as zero by callers).
     */
    @Query("SELECT status, COUNT(*) AS count FROM punch_items WHERE jobId = :jobId GROUP BY status")
    suspend fun statusCounts(jobId: Long): List<StatusCount>

    @Query("SELECT * FROM punch_items WHERE id = :id")
    suspend fun getById(id: Long): PunchItem?

    @Query("SELECT * FROM punch_items WHERE id = :id")
    fun observeById(id: Long): Flow<PunchItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PunchItem): Long

    @Update
    suspend fun update(item: PunchItem)

    @Delete
    suspend fun delete(item: PunchItem)

    @Query("DELETE FROM punch_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PhotoDao {

    @Query("SELECT * FROM photos WHERE punchItemId = :itemId ORDER BY createdAt ASC")
    fun observeByItem(itemId: Long): Flow<List<Photo>>

    @Query("SELECT * FROM photos WHERE punchItemId = :itemId ORDER BY createdAt ASC")
    suspend fun getByItem(itemId: Long): List<Photo>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getById(id: Long): Photo?

    @Query("SELECT COUNT(*) FROM photos WHERE punchItemId = :itemId")
    suspend fun getCount(itemId: Long): Int

    /**
     * Total photos across every punch item in a job. Joins through
     * `punch_items` so a job-level count can be computed in one query instead
     * of looping the job's items in Kotlin.
     */
    @Query(
        "SELECT COUNT(*) FROM photos p INNER JOIN punch_items i ON p.punchItemId = i.id " +
            "WHERE i.jobId = :jobId"
    )
    suspend fun countByJob(jobId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: Photo): Long

    @Update
    suspend fun update(photo: Photo)

    @Delete
    suspend fun delete(photo: Photo)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TemplateDao {

    @Query("SELECT * FROM templates ORDER BY name ASC")
    fun observeAll(): Flow<List<Template>>

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun count(): Int

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: Long): Template?

    @Query("SELECT * FROM templates WHERE id = :id")
    fun observeById(id: Long): Flow<Template?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: Template): Long

    @Update
    suspend fun update(template: Template)

    @Delete
    suspend fun delete(template: Template)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface TemplateItemDao {

    @Query("SELECT * FROM template_items WHERE templateId = :templateId ORDER BY id ASC")
    fun observeByTemplate(templateId: Long): Flow<List<TemplateItem>>

    @Query("SELECT * FROM template_items WHERE templateId = :templateId")
    suspend fun getByTemplate(templateId: Long): List<TemplateItem>

    @Query("SELECT * FROM template_items WHERE id = :id")
    suspend fun getById(id: Long): TemplateItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TemplateItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TemplateItem>): List<Long>

    @Delete
    suspend fun delete(item: TemplateItem)

    @Query("DELETE FROM template_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM template_items WHERE templateId = :templateId")
    suspend fun deleteByTemplate(templateId: Long)
}
