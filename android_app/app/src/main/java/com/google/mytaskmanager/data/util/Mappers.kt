package com.google.mytaskmanager.data.util

import com.google.mytaskmanager.data.local.model.BoardEntity
import com.google.mytaskmanager.data.local.model.ListEntity
import com.google.mytaskmanager.data.local.model.TaskEntity
import com.google.mytaskmanager.data.remote.dto.BoardDto
import com.google.mytaskmanager.data.remote.dto.ListDto
import com.google.mytaskmanager.data.remote.dto.TaskDto
import com.google.mytaskmanager.data.remote.dto.UserDto
import com.google.mytaskmanager.domain.model.Board
import com.google.mytaskmanager.domain.model.BoardList
import com.google.mytaskmanager.domain.model.Task
import com.google.mytaskmanager.domain.model.User

// DTO -> Entity
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

fun ListDto.toEntity(): ListEntity = ListEntity(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    updatedAt = updatedAt
)

/* ------------------ Task ------------------ */
fun TaskDto.toDomain(): Task = Task(
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

/* ------------------ Board ------------------ */
fun BoardDto.toDomain(): Board = Board(
    id = id,
    title = name
)

fun Board.toDto(): BoardDto = BoardDto(
    id = id,
    name = title,
    description = null, // domain doesn’t have description
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis()
)

/* ------------------ BoardList ------------------ */
fun ListDto.toDomain(): BoardList = BoardList(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    updatedAt = updatedAt
)

fun BoardList.toDto(): ListDto = ListDto(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    createdAt = System.currentTimeMillis(), // domain has no createdAt
    updatedAt = updatedAt
)

/* ------------------ User ------------------ */
fun UserDto.toDomain(): User = User(
    id = id,
    username = username,
    email = email
)

fun User.toDto(): UserDto = UserDto(
    id = id,
    username = username,
    email = email
)


fun BoardDto.toEntity(): BoardEntity = BoardEntity(
    id = id,
    title = name,
    updatedAt = updatedAt
)

// Entity -> DTO
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

fun ListEntity.toDto(): ListDto = ListDto(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    createdAt = System.currentTimeMillis(),
    updatedAt = updatedAt
)

fun BoardEntity.toDto(): BoardDto = BoardDto(
    id = id,
    name = title,
    description = null,
    createdAt = System.currentTimeMillis(),
    updatedAt = updatedAt
)

// Entity -> Domain
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

// BoardList
fun BoardList.toEntity(): ListEntity = ListEntity(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    updatedAt = updatedAt
)

fun ListEntity.toDomain(): BoardList = BoardList(
    id = id,
    boardId = boardId,
    title = title,
    position = position,
    updatedAt = updatedAt
)

fun BoardEntity.toDomain(): Board = Board(
    id = id,
    title = title,
    updatedAt = updatedAt
)

fun Board.toEntity(): BoardEntity = BoardEntity(
    id = id,
    title = title,
    updatedAt = updatedAt
)