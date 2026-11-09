package com.google.mytaskmanager.data.repository

import com.google.mytaskmanager.core.appCall
import com.google.mytaskmanager.data.remote.api.ApiService
import com.google.mytaskmanager.data.remote.dto.*
import com.google.mytaskmanager.data.util.toDomain
import com.google.mytaskmanager.domain.model.*
import com.google.mytaskmanager.domain.repository.CollabRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Thin, uniformly error-mapped wrapper over the collaboration endpoints (no local cache: these screens need a connection). */
@Singleton
class CollabRepositoryImpl @Inject constructor(private val api: ApiService) : CollabRepository {
    override suspend fun boardMembers(boardId: String) = appCall { api.boardMembers(boardId).map { it.toDomain() } }
    override suspend fun addBoardMember(boardId: String, username: String, role: String) { appCall { api.addBoardMember(boardId, AddMemberRequest(username.trim(), role)) } }
    override suspend fun updateBoardMemberRole(boardId: String, userId: String, role: String) = appCall { api.updateBoardMember(boardId, userId, UpdateRoleRequest(role)) }
    override suspend fun removeBoardMember(boardId: String, userId: String) = appCall { api.removeBoardMember(boardId, userId) }
    override suspend fun boardTeams(boardId: String) = appCall { api.boardTeams(boardId).map { it.toDomain() } }
    override suspend fun addBoardTeam(boardId: String, teamId: String, role: String) = appCall { api.addBoardTeam(boardId, AddBoardTeamRequest(teamId, role)) }
    override suspend fun updateBoardTeamRole(boardId: String, teamId: String, role: String) = appCall { api.updateBoardTeam(boardId, teamId, UpdateRoleRequest(role)) }
    override suspend fun removeBoardTeam(boardId: String, teamId: String) = appCall { api.removeBoardTeam(boardId, teamId) }

    override suspend fun teams() = appCall { api.teams().map { it.toDomain() } }
    override suspend fun createTeam(name: String, description: String?) = appCall { api.createTeam(TeamRequest(name.trim(), description?.trim()?.ifEmpty { null })).toDomain() }
    override suspend fun updateTeam(teamId: String, name: String, description: String?) { appCall { api.updateTeam(teamId, TeamRequest(name.trim(), description?.trim()?.ifEmpty { null })) } }
    override suspend fun deleteTeam(teamId: String) = appCall { api.deleteTeam(teamId) }
    override suspend fun teamMembers(teamId: String) = appCall { api.teamMembers(teamId).map { it.toDomain() } }
    override suspend fun addTeamMember(teamId: String, username: String, role: String) { appCall { api.addTeamMember(teamId, AddMemberRequest(username.trim(), role)) } }
    override suspend fun updateTeamMemberRole(teamId: String, userId: String, role: String) = appCall { api.updateTeamMember(teamId, userId, UpdateRoleRequest(role)) }
    override suspend fun removeTeamMember(teamId: String, userId: String) = appCall { api.removeTeamMember(teamId, userId) }
    override suspend fun searchUsers(query: String) = appCall { api.searchUsers(query).map { it.toDomain() } }

    override suspend fun comments(taskId: String) = appCall { api.comments(taskId).map { it.toDomain() } }
    override suspend fun addComment(taskId: String, body: String) = appCall { api.addComment(taskId, CreateCommentRequest(body.trim())).toDomain() }
    override suspend fun deleteComment(taskId: String, commentId: String) = appCall { api.deleteComment(taskId, commentId) }

    override suspend fun boardActivity(boardId: String, before: Long?, limit: Int) = appCall { api.boardActivity(boardId, limit, before).map { it.toDomain() } }
    override suspend fun taskActivity(taskId: String, before: Long?, limit: Int) = appCall { api.taskActivity(taskId, limit, before).map { it.toDomain() } }
}
