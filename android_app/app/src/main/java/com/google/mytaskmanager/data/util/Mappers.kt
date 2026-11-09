package com.google.mytaskmanager.data.util

import com.google.mytaskmanager.data.local.entity.BoardEntity
import com.google.mytaskmanager.data.local.entity.ListEntity
import com.google.mytaskmanager.data.local.entity.TaskEntity
import com.google.mytaskmanager.data.remote.dto.*
import com.google.mytaskmanager.domain.model.*
import com.google.mytaskmanager.domain.rules.*

/* DTO (wire) <-> entity (Room) <-> domain (UI) conversions. Unknown enum values degrade to safe defaults. */

fun UserDto.toDomain() = User(id, username, nickname, email)

fun BoardDto.toEntity() = BoardEntity(id, name, key, description, ownerId, myRole, createdAt, updatedAt, listCount, taskCount, doneCount)
fun BoardEntity.toDomain() = Board(
    id, name, key, description, ownerId, BoardRole.parseOrNull(myRole) ?: BoardRole.VIEWER,
    createdAt, updatedAt, listCount, taskCount, doneCount
)

fun ListDto.toEntity() = ListEntity(id, boardId, title, position, category, wipLimit, updatedAt)
fun ListEntity.toDomain() = BoardList(id, boardId, title, position, ColumnCategory.parse(category), wipLimit, updatedAt)

fun TaskDto.toEntity() = TaskEntity(
    id, boardId, listId, key, number, title, description, position, priority, type, assigneeId, reporterId,
    startDate, dueDate, labels.joinToString(","), createdAt, updatedAt
)

fun TaskEntity.toDomain() = Task(
    id, boardId, listId, key, number, title, description, position, TaskPriority.parse(priority), TaskType.parse(type),
    assigneeId, reporterId, startDate, dueDate, labels.split(',').filter { it.isNotBlank() }, createdAt, updatedAt
)

fun TaskEntity.toDto() = TaskDto(
    id = id, boardId = boardId, listId = listId, key = key, number = number, title = title, description = description,
    position = position, priority = priority, type = type, assigneeId = assigneeId, reporterId = reporterId,
    startDate = startDate, dueDate = dueDate, labels = labels.split(',').filter { it.isNotBlank() },
    createdAt = createdAt, updatedAt = updatedAt
)

fun Task.toEntity() = TaskEntity(
    id, boardId, listId, key, number, title, description, position, priority.name, type.name, assigneeId, reporterId,
    startDate, dueDate, labels.joinToString(","), createdAt, updatedAt
)

fun CommentDto.toDomain() = Comment(id, taskId, authorId, authorName, body, createdAt)

fun ActivityDto.toDomain() = Activity(id, boardId, taskId, actorId, actorName, type, ActivityType.parseOrNull(type), details, createdAt)

fun TeamDto.toDomain() = Team(id, name, description, ownerId, TeamRole.parseOrNull(myRole) ?: TeamRole.MEMBER, memberCount)

fun MemberDto.toDomain() = Member(userId, username, nickname, role, source)

fun BoardTeamDto.toDomain() = BoardTeam(teamId, teamName, BoardRole.parseOrNull(role) ?: BoardRole.MEMBER, memberCount)
