package com.punchlist.pocket.data.seed

import com.punchlist.pocket.data.local.PunchItem
import com.punchlist.pocket.data.local.Template
import com.punchlist.pocket.data.local.TemplateItem
import com.punchlist.pocket.data.repository.AppRepository

/**
 * Seeds the bundled starter templates on first launch so the Templates
 * feature is immediately useful instead of empty. Runs only when the
 * templates table is empty, so it never clobbers user-created or user-edited
 * data; re-runs (e.g. after the user deletes every template) will repopulate
 * the defaults.
 */
object TemplateSeeder {

    /**
     * Each starter template: name, short description, and its line items.
     * Item fields map directly onto a future [PunchItem] when applied. Trade is
     * left blank where it doesn't apply so cards stay uncluttered.
     */
    private val seedTemplates: List<TemplateSeed> = listOf(
        TemplateSeed(
            name = "Final Walkthrough",
            description = "Whole-home punch list before handover.",
            items = listOf(
                SeedItem("Inspect all doors and hardware", "Hardware"),
                SeedItem("Verify paint touch-ups", "Painting"),
                SeedItem("Test all fixtures and outlets", "Electrical"),
                SeedItem("Check caulking throughout", "General"),
                SeedItem("Confirm clean-up complete", "General")
            )
        ),
        TemplateSeed(
            name = "Bathroom Remodel",
            description = "Phased inspection for a bathroom renovation.",
            items = listOf(
                SeedItem("Rough-in plumbing inspection", "Plumbing"),
                SeedItem("Waterproofing / membrane check", "Waterproofing"),
                SeedItem("Tile layout approval", "Tile"),
                SeedItem("Grout and sealant", "Tile"),
                SeedItem("Fixture installation", "Plumbing"),
                SeedItem("Final leak test", "Plumbing")
            )
        ),
        TemplateSeed(
            name = "Kitchen Remodel",
            description = "Phased inspection for a kitchen renovation.",
            items = listOf(
                SeedItem("Cabinet install inspection", "Cabinetry"),
                SeedItem("Countertop template & install", "Countertop"),
                SeedItem("Plumbing rough-in", "Plumbing"),
                SeedItem("Electrical rough-in", "Electrical"),
                SeedItem("Appliance fit check", "General"),
                SeedItem("Backsplash install", "Tile")
            )
        ),
        TemplateSeed(
            name = "Painting Inspection",
            description = "Quality check across a paint job.",
            items = listOf(
                SeedItem("Surface prep approved", "Painting"),
                SeedItem("Primer coat check", "Painting"),
                SeedItem("Finish coat evenness", "Painting"),
                SeedItem("Cut-in lines clean", "Painting"),
                SeedItem("Touch-up walk", "Painting")
            )
        ),
        TemplateSeed(
            name = "Rental Turnover",
            description = "Turnover checklist between tenants.",
            items = listOf(
                SeedItem("Deep clean verified", "General"),
                SeedItem("Appliances tested", "Appliances"),
                SeedItem("Smoke / CO detectors checked", "Electrical"),
                SeedItem("Locks rekeyed", "Hardware"),
                SeedItem("Carpet / floor condition", "Flooring"),
                SeedItem("Bathroom caulking", "Plumbing")
            )
        ),
        TemplateSeed(
            name = "Electrical Check",
            description = "Whole-home electrical inspection.",
            items = listOf(
                SeedItem("Panel labeling", "Electrical"),
                SeedItem("GFCI / AFCI test", "Electrical"),
                SeedItem("Outlet polarity", "Electrical"),
                SeedItem("Switch function", "Electrical"),
                SeedItem("Fixture mounting", "Electrical")
            )
        ),
        TemplateSeed(
            name = "Plumbing Check",
            description = "Whole-home plumbing inspection.",
            items = listOf(
                SeedItem("Pressure test", "Plumbing"),
                SeedItem("Drain flow", "Plumbing"),
                SeedItem("Shut-off valve function", "Plumbing"),
                SeedItem("Leak inspection", "Plumbing"),
                SeedItem("Water heater check", "Plumbing")
            )
        ),
        TemplateSeed(
            name = "Drywall / Paint Touch-Up",
            description = "Final drywall and paint touch-up walk.",
            items = listOf(
                SeedItem("Nail pops", "Drywall"),
                SeedItem("Seam sanding", "Drywall"),
                SeedItem("Texture match", "Drywall"),
                SeedItem("Primer spots", "Painting"),
                SeedItem("Final finish", "Painting")
            )
        )
    )

    /**
     * Inserts the bundled templates if the table is empty. Safe to call on
     * every app start; the count guard makes it a no-op after the first run.
     */
    suspend fun seedIfEmpty(repository: AppRepository) {
        if (repository.getTemplateCount() > 0) return
        seedTemplates.forEach { template ->
            val templateId = repository.insertTemplate(
                Template(name = template.name, description = template.description)
            )
            val rows = template.items.map { item ->
                TemplateItem(
                    templateId = templateId,
                    title = item.title,
                    trade = item.trade,
                    priority = PunchItem.PRIORITY_MEDIUM
                )
            }
            repository.insertTemplateItems(rows)
        }
    }

    private data class TemplateSeed(
        val name: String,
        val description: String,
        val items: List<SeedItem>
    )

    private data class SeedItem(val title: String, val trade: String)
}
