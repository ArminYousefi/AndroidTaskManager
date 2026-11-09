package domain

/**
 * Role a user holds on a board (Jira: project role).
 *
 * @property rank higher rank = more power; used to combine a direct role with team-derived roles.
 */
enum class BoardRole(val rank: Int) {
    /** Read-only access. */
    VIEWER(1),

    /** Can create, edit, move, delete and comment on tasks. */
    MEMBER(2),

    /** Everything a member can do plus manage columns, members, board settings and the board itself. */
    ADMIN(3);

    companion object {
        /** @return the role named [raw] (case-insensitive) or null. */
        fun parseOrNull(raw: String?): BoardRole? = entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }

        /** @return the more powerful of two optional roles (null = no access). */
        fun strongest(a: BoardRole?, b: BoardRole?): BoardRole? = when {
            a == null -> b
            b == null -> a
            else -> if (a.rank >= b.rank) a else b
        }
    }
}

/** Role inside a team. A team admin manages the team's membership. */
enum class TeamRole {
    MEMBER, ADMIN;

    companion object {
        /** @return the role named [raw] (case-insensitive) or null. */
        fun parseOrNull(raw: String?): TeamRole? = entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

/** Fine-grained actions that are checked against a [BoardRole]. */
enum class Permission {
    VIEW_BOARD, CREATE_TASK, EDIT_TASK, MOVE_TASK, DELETE_TASK, COMMENT,
    MANAGE_COLUMNS, MANAGE_MEMBERS, EDIT_BOARD, EMPTY_BOARD, DELETE_BOARD
}

/**
 * The permission matrix. Enforced on the server for every request and mirrored in the app
 * only to hide/disable controls - the server is always the final authority.
 */
object RolePolicy {
    private val viewer = setOf(Permission.VIEW_BOARD)
    private val member = viewer + setOf(
        Permission.CREATE_TASK, Permission.EDIT_TASK, Permission.MOVE_TASK, Permission.DELETE_TASK, Permission.COMMENT
    )
    private val admin = Permission.entries.toSet()

    /**
     * @param role the user's effective role, or null when they have no access at all.
     * @param permission the action being attempted.
     * @return true when [role] allows [permission].
     */
    fun can(role: BoardRole?, permission: Permission): Boolean = when (role) {
        null -> false
        BoardRole.VIEWER -> permission in viewer
        BoardRole.MEMBER -> permission in member
        BoardRole.ADMIN -> permission in admin
    }
}
