package com.google.mytaskmanager.data.util

import com.google.mytaskmanager.data.local.model.BoardEntity
import com.google.mytaskmanager.data.local.model.ListEntity
import com.google.mytaskmanager.data.local.model.TaskEntity
import com.google.mytaskmanager.data.remote.dto.BoardDto
import com.google.mytaskmanager.data.remote.dto.ListDto
import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.Task


/**
 * Remote -> Local
 * BoardDto has `name`, BoardEntity has `title` -> map name -> title
 */
fun BoardDto.toEntity(): BoardEntity = BoardEntity(
    id = id,
    title = name,
    updatedAt = updatedAt
)

/** Remote -> Domain */
fun BoardDto.toDomain(): Board = Board(
    id = id,
    title = name
)

/** Local -> Domain */
fun BoardEntity.toDomain(): Board = Board(
    id = id,
    title = title
)

/** Domain -> Local (Room entity) */
fun Board.toEntity(): BoardEntity = BoardEntity(
    id = id,
    title = title
)

/**
 * Local -> Remote DTO.
 * BoardDto requires createdAt and updatedAt; we don't have createdAt on BoardEntity,
 * so we use updatedAt as a best-effort fallback.
 */
fun BoardEntity.toDto(): BoardDto = BoardDto(
    id = id,
    name = title,
    description = null,
    createdAt = updatedAt,
    updatedAt = updatedAt
)

/** Domain -> Remote DTO (used when creating a board) */
fun Board.toDto(): BoardDto = BoardDto(
    id = id,
    name = title,
    description = null,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    boardId = boardId,
    listId = listId,
    title = title,
    description = description,
    position = position,
    status = status,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    boardId = boardId,
    listId = listId,
    title = title,
    description = description,
    position = position,
    status = status,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun TaskDto.toEntity(): TaskEntity = TaskEntity(
    id = id,
    boardId = boardId,
    listId = listId,
    title = title,
    description = description,
    position = position,
    status = status,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun TaskEntity.toDto(): TaskDto = TaskDto(
    id = id,
    boardId = boardId,
    listId = listId,
    title = title,
    description = description,
    position = position,
    status = status,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

fun Task.toDto(): TaskDto = TaskDto(
    id = id,
    boardId = boardId,
    listId = listId,
    title = title,
    description = description,
    position = position,
    status = status,
    updatedAt = updatedAt,
    isDeleted = isDeleted
)

// Entity ↔ Domain
fun ListEntity.toDomain(): BoardList = BoardList(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    updatedAt = 0L
)

fun BoardList.toEntity(): ListEntity = ListEntity(
    id = id,
    boardId = boardId,
    title = title,
    position = position
)

// Dto ↔ Entity
fun ListDto.toEntity(): ListEntity = ListEntity(
    id = id,
    boardId = boardId,
    title = title,
    position = position
)

fun ListEntity.toDto(): ListDto = ListDto(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    createdAt = 0L,
    updatedAt = updatedAt
)

// Domain ↔ Dto
fun BoardList.toDto(): ListDto = ListDto(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    createdAt = 0L,
    updatedAt = updatedAt
)
