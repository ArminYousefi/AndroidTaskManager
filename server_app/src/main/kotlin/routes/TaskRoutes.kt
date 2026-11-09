package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import models.*
import plugins.body
import services.TaskService

/** `/tasks` - create, edit, move, delete, comment, history, plus the legacy `/sync`. */
fun Route.taskRoutes() {
    authenticate("auth-jwt") {
        route("/tasks") {
            post { call.respond(HttpStatusCode.Created, TaskService.create(call.userId(), call.body<TaskDto>())) }
            get("/{id}") { call.respond(TaskService.get(call.userId(), call.param("id"))) }
            put("/{id}") { call.respond(TaskService.update(call.userId(), call.param("id"), call.body<TaskDto>())) }
            post("/{id}/move") { call.respond(TaskService.move(call.userId(), call.param("id"), call.body<MoveTaskRequest>())) }
            delete("/{id}") { TaskService.delete(call.userId(), call.param("id")); call.respond(HttpStatusCode.NoContent) }

            get("/{id}/comments") { call.respond(TaskService.comments(call.userId(), call.param("id"))) }
            post("/{id}/comments") { call.respond(HttpStatusCode.Created, TaskService.addComment(call.userId(), call.param("id"), call.body<CreateCommentRequest>().body)) }
            delete("/{id}/comments/{commentId}") {
                TaskService.deleteComment(call.userId(), call.param("id"), call.param("commentId"))
                call.respond(HttpStatusCode.NoContent)
            }
            get("/{id}/activity") {
                val q = call.request.queryParameters
                call.respond(TaskService.history(call.userId(), call.param("id"), q["limit"]?.toIntOrNull() ?: 50, q["before"]?.toLongOrNull()))
            }
        }
        post("/sync") { call.respond(TaskService.sync(call.userId(), call.body<List<TaskDto>>())) }
    }
}
