package domain

/**
 * Jira-style *status category* of a board column.
 *
 * Every column belongs to exactly one category. The category (not the column title)
 * drives colours, progress calculation and the "done" celebration in the app, so users
 * are free to rename columns without breaking any logic.
 */
enum class ColumnCategory {
    /** Work that has not started yet. */
    TODO,

    /** Work that is actively being done (includes "In Review"). */
    IN_PROGRESS,

    /** Finished work. Counts towards board progress. */
    DONE;

    companion object {
        /**
         * Parses a category name leniently.
         *
         * @param raw name coming from the network or the database.
         * @return the matching category, or [TODO] when [raw] is null/unknown.
         */
        fun parse(raw: String?): ColumnCategory =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: TODO
    }
}

/** Priority of an issue, ordered from most to least urgent (same scale Jira uses). */
enum class TaskPriority {
    HIGHEST, HIGH, MEDIUM, LOW, LOWEST;

    companion object {
        /**
         * @param raw priority name coming from the network or the database.
         * @return the matching priority, or [MEDIUM] when [raw] is null/unknown.
         */
        fun parse(raw: String?): TaskPriority =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: MEDIUM
    }
}

/** Issue type, mirrors the most common Jira issue types. */
enum class TaskType {
    TASK, STORY, BUG, EPIC;

    companion object {
        /**
         * @param raw type name coming from the network or the database.
         * @return the matching type, or [TASK] when [raw] is null/unknown.
         */
        fun parse(raw: String?): TaskType =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: TASK
    }
}

/** Every kind of event that is written to the board activity log (audit trail). */
enum class ActivityType {
    BOARD_CREATED, BOARD_RENAMED, BOARD_UPDATED, BOARD_EMPTIED,
    COLUMN_CREATED, COLUMN_RENAMED, COLUMN_DELETED, COLUMN_REORDERED, COLUMN_WIP_CHANGED, COLUMN_CATEGORY_CHANGED,
    TASK_CREATED, TASK_RENAMED, TASK_MOVED, TASK_REPLANNED, TASK_DELETED,
    TASK_ASSIGNED, TASK_PRIORITY_CHANGED, TASK_TYPE_CHANGED, TASK_DESCRIPTION_CHANGED, TASK_LABELS_CHANGED,
    COMMENT_ADDED, COMMENT_DELETED,
    MEMBER_ADDED, MEMBER_REMOVED, MEMBER_ROLE_CHANGED,
    TEAM_ADDED, TEAM_REMOVED, TEAM_ROLE_CHANGED;

    companion object {
        /**
         * @param raw type name coming from the network or the database.
         * @return the matching type, or null when [raw] is unknown (newer server than client).
         */
        fun parseOrNull(raw: String?): ActivityType? =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
