package com.punchlist.pocket.data.repository

import com.punchlist.pocket.data.local.Job
import com.punchlist.pocket.data.local.JobDao
import com.punchlist.pocket.data.local.Photo
import com.punchlist.pocket.data.local.PhotoDao
import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.data.local.PunchItemDao
import com.punchlist.pocket.data.local.StatusCount
import com.punchlist.pocket.data.local.Template
import com.punchlist.pocket.data.local.TemplateDao
import com.punchlist.pocket.data.local.TemplateItem
import com.punchlist.pocket.data.local.TemplateItemDao
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for all persistent data in the app. Wraps every DAO
 * behind clean suspend functions and Kotlin flows so that ViewModels never touch
 * Room directly.
 */
class AppRepository(
    private val jobDao: JobDao,
    private val punchItemDao: PunchItemDao,
    private val photoDao: PhotoDao,
    private val templateDao: TemplateDao,
    private val templateItemDao: TemplateItemDao
) {

    // -------------------- Job --------------------

    fun observeJobs(): Flow<List<Job>> = jobDao.observeAll()

    fun searchJobs(query: String): Flow<List<Job>> = jobDao.search(query)

    fun observeJob(id: Long): Flow<Job?> = jobDao.observeById(id)

    suspend fun getJob(id: Long): Job? = jobDao.getById(id)

    suspend fun insertJob(job: Job): Long = jobDao.insert(job)

    suspend fun updateJob(job: Job) {
        jobDao.update(job.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteJob(job: Job) = jobDao.delete(job)

    suspend fun deleteJobById(id: Long) = jobDao.deleteById(id)

    // -------------------- PunchItem --------------------

    fun observeItemsByJob(jobId: Long): Flow<List<PunchItem>> = punchItemDao.observeByJob(jobId)

    fun observeItemsByStatus(jobId: Long, status: String): Flow<List<PunchItem>> =
        punchItemDao.observeByJobAndStatus(jobId, status)

    fun observeItemsByPriority(jobId: Long, priority: String): Flow<List<PunchItem>> =
        punchItemDao.observeByJobAndPriority(jobId, priority)

    fun observeItemCount(jobId: Long): Flow<Int> = punchItemDao.observeItemCount(jobId)

    suspend fun getItemCount(jobId: Long): Int = punchItemDao.getItemCount(jobId)

    /** Per-status counts for a job (e.g. for the Home dashboard). */
    suspend fun statusCounts(jobId: Long): List<StatusCount> = punchItemDao.statusCounts(jobId)

    fun observeItem(id: Long): Flow<PunchItem?> = punchItemDao.observeById(id)

    suspend fun getItem(id: Long): PunchItem? = punchItemDao.getById(id)

    suspend fun insertItem(item: PunchItem): Long = punchItemDao.insert(item)

    suspend fun updateItem(item: PunchItem) {
        punchItemDao.update(item.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteItem(item: PunchItem) = punchItemDao.delete(item)

    suspend fun deleteItemById(id: Long) = punchItemDao.deleteById(id)

    // -------------------- Photo --------------------

    fun observePhotos(itemId: Long): Flow<List<Photo>> = photoDao.observeByItem(itemId)

    suspend fun getPhotos(itemId: Long): List<Photo> = photoDao.getByItem(itemId)

    suspend fun getPhoto(id: Long): Photo? = photoDao.getById(id)

    suspend fun getPhotoCount(itemId: Long): Int = photoDao.getCount(itemId)

    /** Total photos across all of a job's punch items. */
    suspend fun photoCountForJob(jobId: Long): Int = photoDao.countByJob(jobId)

    suspend fun insertPhoto(photo: Photo): Long = photoDao.insert(photo)

    suspend fun updatePhoto(photo: Photo) = photoDao.update(photo)

    suspend fun deletePhoto(photo: Photo) = photoDao.delete(photo)

    suspend fun deletePhotoById(id: Long) = photoDao.deleteById(id)

    // -------------------- Template --------------------

    fun observeTemplates(): Flow<List<Template>> = templateDao.observeAll()

    suspend fun getTemplateCount(): Int = templateDao.count()

    fun observeTemplate(id: Long): Flow<Template?> = templateDao.observeById(id)

    suspend fun getTemplate(id: Long): Template? = templateDao.getById(id)

    suspend fun insertTemplate(template: Template): Long = templateDao.insert(template)

    suspend fun updateTemplate(template: Template) = templateDao.update(template)

    suspend fun deleteTemplate(template: Template) = templateDao.delete(template)

    suspend fun deleteTemplateById(id: Long) = templateDao.deleteById(id)

    // -------------------- TemplateItem --------------------

    fun observeTemplateItems(templateId: Long): Flow<List<TemplateItem>> =
        templateItemDao.observeByTemplate(templateId)

    suspend fun getTemplateItems(templateId: Long): List<TemplateItem> =
        templateItemDao.getByTemplate(templateId)

    suspend fun insertTemplateItem(item: TemplateItem): Long = templateItemDao.insert(item)

    suspend fun insertTemplateItems(items: List<TemplateItem>): List<Long> =
        templateItemDao.insertAll(items)

    suspend fun deleteTemplateItem(item: TemplateItem) = templateItemDao.delete(item)

    suspend fun deleteTemplateItemById(id: Long) = templateItemDao.deleteById(id)

    suspend fun deleteTemplateItemsByTemplate(templateId: Long) =
        templateItemDao.deleteByTemplate(templateId)

    /**
     * Copies every [TemplateItem] in [templateId] into [jobId] as fresh,
     * open-status [PunchItem]s. Returns the ids of the newly created items.
     * Used by the "Apply template to job" flow. Title/description/trade/
     * priority map across directly; status starts OPEN and dueDate is null.
     */
    suspend fun applyTemplateToJob(jobId: Long, templateId: Long): List<Long> {
        val templateItems = templateItemDao.getByTemplate(templateId)
        if (templateItems.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        return templateItems.map { ti ->
            punchItemDao.insert(
                PunchItem(
                    jobId = jobId,
                    title = ti.title,
                    description = ti.description,
                    trade = ti.trade,
                    status = PunchItem.STATUS_OPEN,
                    priority = ti.priority,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }
}
