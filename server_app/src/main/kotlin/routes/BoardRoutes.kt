package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import models.BoardDto
import models.Boards
import models.ListDto
import models.Lists
import models.SyncResponse
import models.TaskDto
import models.Tasks
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import services.WebSocketHub
import java.util.UUID

fun Route.boardRoutes() {
    route("/boards") {
        get {
            val boards = transaction {
                Boards.selectAll().map {
                    BoardDto(
                        id = it[Boards.id],
                        name = it[Boards.title],
                        description = null,
                        createdAt = it[Boards.updatedAt],
                        updatedAt = it[Boards.updatedAt]
                    )
                }
            }
            call.respond(boards)
        }

        post {
            val req = call.receive<BoardDto>()
            val id = req.id.ifBlank { UUID.randomUUID().toString() }
            val now = System.currentTimeMillis()

            val created = transaction {
                Boards.insert {
                    it[this.id] = id
                    it[Boards.title] = req.name
                    it[Boards.updatedAt] = now
                }
                BoardDto(
                    id = id,
                    name = req.name,
                    description = req.description,
                    createdAt = now,
                    updatedAt = now
                )
            }

            call.respond(created)
        }

        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respondText("Missing id", status = HttpStatusCode.BadRequest)
            val req = call.receive<BoardDto>()
            val now = System.currentTimeMillis()
            val updated = transaction {
                val updatedRows = Boards.update({ Boards.id eq id }) {
                    it[Boards.title] = req.name
                    it[Boards.updatedAt] = now
                }
                if (updatedRows > 0) {
                    BoardDto(id = id, name = req.name, description = req.description, createdAt = now, updatedAt = now)
                } else null
            }
            if (updated != null) {
                try { WebSocketHub.broadcastBoardEvent("board_updated", updated) } catch (_: Throwable) {}
                call.respond(updated)
            } else {
                call.respond(HttpStatusCode.NotFound, "Board not found")
            }
        }


        get("{boardId}/lists") {
            val boardId = call.parameters["boardId"]
                ?: return@get call.respondText("Missing boardId", status = HttpStatusCode.BadRequest)

            val lists = transaction {
                Lists.select { Lists.boardId eq boardId }.map {
                    ListDto(
                        id = it[Lists.id],
                        boardId = it[Lists.boardId],
                        title = it[Lists.title],
                        position = it[Lists.position],
                        createdAt = it[Lists.updatedAt],
                        updatedAt = it[Lists.updatedAt]
                    )
                }
            }
            call.respond(lists)
        }

        post("/lists") {
            val req = call.receive<ListDto>()
            val id = req.id.ifBlank { UUID.randomUUID().toString() }
            val now = System.currentTimeMillis()

            val created = transaction {
                Lists.insert {
                    it[Lists.id] = id
                    it[Lists.boardId] = req.boardId
                    it[Lists.title] = req.title
                    it[Lists.position] = req.position
                    it[Lists.updatedAt] = now
                }
                req.copy(id = id, createdAt = now, updatedAt = now)
            }

            call.respond(created)
        }

        put("/lists/{id}") {
            val id = call.parameters["id"]
                ?: return@put call.respondText("Missing id", status = HttpStatusCode.BadRequest)
            val req = call.receive<ListDto>()
            val now = System.currentTimeMillis()

            transaction {
                Lists.update({ Lists.id eq id }) {
                    it[Lists.title] = req.title
                    it[Lists.position] = req.position
                    it[Lists.updatedAt] = now
                }
            }
            call.respondText("OK")
        }

        delete("/lists/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respondText("Missing id", status = HttpStatusCode.BadRequest)

            transaction { Lists.deleteWhere { Lists.id eq id } }
            call.respondText("OK")
        }

        // ---------------- TASKS ----------------

        get("/tasks") {
            val boardId = call.request.queryParameters["boardId"]
                ?: return@get call.respondText("Missing boardId", status = HttpStatusCode.BadRequest)

            val tasks = transaction {
                Tasks.select { Tasks.boardId eq boardId }.map {
                    TaskDto(
                        id = it[Tasks.id],
                        boardId = it[Tasks.boardId],
                        listId = it[Tasks.listId],
                        title = it[Tasks.title],
                        description = it[Tasks.description],
                        position = it[Tasks.position],
                        status = it[Tasks.status],
                        updatedAt = it[Tasks.updatedAt],
                        isDeleted = it[Tasks.isDeleted]
                    )
                }
            }
            call.respond(tasks)
        }

        post("/tasks") {
            val req = call.receive<TaskDto>()
            val id = req.id.ifBlank { UUID.randomUUID().toString() }
            val now = System.currentTimeMillis()

            val created = transaction {
                Tasks.insert {
                    it[Tasks.id] = id
                    it[Tasks.boardId] = req.boardId
                    it[Tasks.listId] = req.listId
                    it[Tasks.title] = req.title
                    it[Tasks.description] = req.description
                    it[Tasks.position] = req.position
                    it[Tasks.status] = req.status
                    it[Tasks.updatedAt] = now
                    it[Tasks.isDeleted] = req.isDeleted
                }
                req.copy(id = id, updatedAt = now)
            }

            try { WebSocketHub.broadcastTaskEvent("task_created", created) } catch (_: Throwable) {}
            call.respond(created)
        }

        put("/tasks/{id}") {
            val id = call.parameters["id"]
                ?: return@put call.respondText("Missing id", status = HttpStatusCode.BadRequest)
            val req = call.receive<TaskDto>()
            val now = System.currentTimeMillis()

            val updated = transaction {
                Tasks.update({ Tasks.id eq id }) {
                    it[Tasks.title] = req.title
                    it[Tasks.description] = req.description
                    it[Tasks.position] = req.position
                    it[Tasks.status] = req.status
                    it[Tasks.updatedAt] = now
                    it[Tasks.isDeleted] = req.isDeleted
                }
                req.copy(id = id, updatedAt = now)
            }

            try { WebSocketHub.broadcastTaskEvent("task_updated", updated) } catch (_: Throwable) {}
            call.respond(updated)
        }

        delete("/tasks/{id}") {
            val id = call.parameters["id"]
                ?: return@delete call.respondText("Missing id", status = HttpStatusCode.BadRequest)
            val now = System.currentTimeMillis()

            transaction {
                Tasks.update({ Tasks.id eq id }) {
                    it[Tasks.isDeleted] = true
                    it[Tasks.updatedAt] = now
                }
            }

            val deleted = TaskDto(
                id = id,
                boardId = "",
                listId = "",
                title = "",
                description = null,
                position = 0,
                status = "DELETED",
                updatedAt = now,
                isDeleted = true
            )

            try { WebSocketHub.broadcastTaskEvent("task_deleted", deleted) } catch (_: Throwable) {}
            call.respondText("OK")
        }

        post("/sync") {
            val changes = call.receive<List<TaskDto>>()
            val serverChanges = mutableListOf<TaskDto>()
            val newTasks = mutableListOf<TaskDto>()
            val updatedTasks = mutableListOf<TaskDto>()

            transaction {
                changes.forEach { t ->
                    val existing = Tasks.select { Tasks.id eq t.id }.firstOrNull()
                    if (existing == null) {
                        Tasks.insert {
                            it[Tasks.id] = t.id
                            it[Tasks.boardId] = t.boardId
                            it[Tasks.listId] = t.listId
                            it[Tasks.title] = t.title
                            it[Tasks.description] = t.description
                            it[Tasks.position] = t.position
                            it[Tasks.status] = t.status
                            it[Tasks.updatedAt] = t.updatedAt
                            it[Tasks.isDeleted] = t.isDeleted
                        }
                        newTasks.add(t)
                    } else {
                        val localUpdated = existing[Tasks.updatedAt]
                        if (localUpdated > t.updatedAt) {
                            serverChanges.add(
                                TaskDto(
                                    id = existing[Tasks.id],
                                    boardId = existing[Tasks.boardId],
                                    listId = existing[Tasks.listId],
                                    title = existing[Tasks.title],
                                    description = existing[Tasks.description],
                                    position = existing[Tasks.position],
                                    status = existing[Tasks.status],
                                    updatedAt = existing[Tasks.updatedAt],
                                    isDeleted = existing[Tasks.isDeleted]
                                )
                            )
                        } else {
                            Tasks.update({ Tasks.id eq t.id }) {
                                it[Tasks.title] = t.title
                                it[Tasks.description] = t.description
                                it[Tasks.position] = t.position
                                it[Tasks.status] = t.status
                                it[Tasks.updatedAt] = t.updatedAt
                                it[Tasks.isDeleted] = t.isDeleted
                            }
                            updatedTasks.add(t)
                        }
                    }
                }
            }

            call.respond(SyncResponse(success = true, serverChanges = serverChanges))

            try {
                newTasks.forEach { WebSocketHub.broadcastTaskEvent("task_created", it) }
                updatedTasks.forEach { WebSocketHub.broadcastTaskEvent("task_updated", it) }
                serverChanges.forEach {
                    WebSocketHub.broadcastTaskEvent(
                        if (it.isDeleted) "task_deleted" else "task_updated",
                        it
                    )
                }
            } catch (_: Throwable) {}
        }
    }
}