package com.google.mytaskmanager.domain.repository

import com.google.mytaskmanager.domain.model.*
import com.google.mytaskmanager.domain.rules.BoardTemplate
import com.google.mytaskmanager.domain.rules.ColumnCategory
import com.google.mytaskmanager.domain.rules.TaskPriority
import com.google.mytaskmanager.domain.rules.TaskType
import kotlinx.coroutines.flow.Flow

/**
 * Account and session. Every suspend function throws `AppException` on failure.
 */
interface AuthRepository {
    /** True while a session exists. */
    val isLoggedIn: Flow<Boolean>

    /** The signed-in user's cached profile. */
    val currentUser: Flow<User?>

    /** Signs in and stores the session. */
    suspend fun login(username: String, password: String): User

    /** Creates an account (policy-checked) and signs in. */
    suspend fun signup(username: String, password: String, email: String?, nickname: String?): User

    /** Signs out: revokes the refresh token on the server (best effort) and wipes all local data. */
    suspend fun logout()

    /** Re-reads the profile from the server. */
    suspend fun refreshProfile(): User

    /** Changes the nickname (the username can never change). */
    suspend fun updateNickname(nickname: String): User

    /** Changes the password. Throws `AppException(code = INVALID_CURRENT_PASSWORD)` when [current] is wrong. */
    suspend fun changePassword(current: String, new: String)
}

/**
 * Boards, columns and tasks, offline-first: reads come from the local cache as Flows; task writes are applied locally
 * at once and sent to the server (queued when offline); board/column writes need a connection.
 * Every suspend function throws `AppException` on failure.
 */
interface TaskRepository {
    fun observeBoards(): Flow<List<Board>>
    fun observeBoard(boardId: String): Flow<Board?>
    fun observeLists(boardId: String): Flow<List<BoardList>>
    fun observeTasks(boardId: String): Flow<List<Task>>
    fun observeTask(taskId: String): Flow<Task?>

    /** Number of changes waiting to be sent. */
    fun observePendingCount(): Flow<Int>

    suspend fun refreshBoards()
    suspend fun refreshBoard(boardId: String)

    /** Sends queued offline changes. @return true when nothing is left in the queue. */
    suspend fun flushPending(): Boolean

    suspend fun createBoard(name: String, key: String?, description: String?, template: BoardTemplate): Board
    suspend fun updateBoard(boardId: String, name: String, description: String?)
    suspend fun deleteBoard(boardId: String)
    suspend fun emptyBoard(boardId: String): Int

    suspend fun createList(boardId: String, title: String, category: ColumnCategory, wipLimit: Int?)
    suspend fun updateList(list: BoardList)
    suspend fun deleteList(listId: String, moveTo: String?, deleteTasks: Boolean)
    suspend fun reorderLists(boardId: String, orderedIds: List<String>)

    suspend fun createTask(
        boardId: String, listId: String, title: String, description: String? = null,
        priority: TaskPriority = TaskPriority.MEDIUM, type: TaskType = TaskType.TASK,
        assigneeId: String? = null, dueDate: Long? = null
    ): Task
    suspend fun updateTask(task: Task)
    suspend fun moveTask(taskId: String, toListId: String, toIndex: Int)
    suspend fun deleteTask(taskId: String)
}

/**
 * Online-only collaboration features: members, teams, comments and the activity log.
 * Every suspend function throws `AppException` on failure.
 */
interface CollabRepository {
    suspend fun boardMembers(boardId: String): List<Member>
    suspend fun addBoardMember(boardId: String, username: String, role: String)
    suspend fun updateBoardMemberRole(boardId: String, userId: String, role: String)
    suspend fun removeBoardMember(boardId: String, userId: String)
    suspend fun boardTeams(boardId: String): List<BoardTeam>
    suspend fun addBoardTeam(boardId: String, teamId: String, role: String)
    suspend fun updateBoardTeamRole(boardId: String, teamId: String, role: String)
    suspend fun removeBoardTeam(boardId: String, teamId: String)

    suspend fun teams(): List<Team>
    suspend fun createTeam(name: String, description: String?): Team
    suspend fun updateTeam(teamId: String, name: String, description: String?)
    suspend fun deleteTeam(teamId: String)
    suspend fun teamMembers(teamId: String): List<Member>
    suspend fun addTeamMember(teamId: String, username: String, role: String)
    suspend fun updateTeamMemberRole(teamId: String, userId: String, role: String)
    suspend fun removeTeamMember(teamId: String, userId: String)
    suspend fun searchUsers(query: String): List<User>

    suspend fun comments(taskId: String): List<Comment>
    suspend fun addComment(taskId: String, body: String): Comment
    suspend fun deleteComment(taskId: String, commentId: String)

    /** Board activity, newest first; pass the last item's `createdAt` as [before] to page. */
    suspend fun boardActivity(boardId: String, before: Long? = null, limit: Int = 50): List<Activity>
    suspend fun taskActivity(taskId: String, before: Long? = null, limit: Int = 50): List<Activity>
}
