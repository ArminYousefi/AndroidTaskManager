package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import models.*
import plugins.body
import services.ActivityService
import services.BoardService
import services.TaskService

/**
 * Board-scoped endpoints: boards, columns ("lists"), members, team grants, tasks, activity.
 * Every handler passes the caller id to a service which authorises it against the caller's role on that board.
 */
fun Route.boardRoutes() {
    authenticate("auth-jwt") {
        route("/boards") {
            get { call.respond(BoardService.listFor(call.userId())) }
            post { call.respond(HttpStatusCode.Created, BoardService.create(call.userId(), call.body<CreateBoardRequest>())) }
            get("/{id}") { call.respond(BoardService.get(call.userId(), call.param("id"))) }
            put("/{id}") { call.respond(BoardService.update(call.userId(), call.param("id"), call.body<UpdateBoardRequest>())) }
            delete("/{id}") { BoardService.delete(call.userId(), call.param("id")); call.respond(HttpStatusCode.NoContent) }
            post("/{id}/empty") { call.respond(mapOf("removed" to BoardService.empty(call.userId(), call.param("id")))) }

            get("/{id}/lists") { call.respond(BoardService.lists(call.userId(), call.param("id"))) }
            post("/{id}/lists/reorder") { call.respond(BoardService.reorderLists(call.userId(), call.param("id"), call.body<ReorderRequest>().ids)) }
            get("/{id}/tasks") { call.respond(TaskService.list(call.userId(), call.param("id"))) }

            get("/{id}/members") { call.respond(BoardService.members(call.userId(), call.param("id"))) }
            post("/{id}/members") { call.respond(HttpStatusCode.Created, BoardService.addMember(call.userId(), call.param("id"), call.body<AddMemberRequest>())) }
            put("/{id}/members/{userId}") {
                BoardService.updateMemberRole(call.userId(), call.param("id"), call.param("userId"), call.body<UpdateRoleRequest>().role)
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{id}/members/{userId}") {
                BoardService.removeMember(call.userId(), call.param("id"), call.param("userId"))
                call.respond(HttpStatusCode.NoContent)
            }

            get("/{id}/teams") { call.respond(BoardService.boardTeams(call.userId(), call.param("id"))) }
            post("/{id}/teams") { BoardService.addBoardTeam(call.userId(), call.param("id"), call.body<AddBoardTeamRequest>()); call.respond(HttpStatusCode.Created) }
            put("/{id}/teams/{teamId}") {
                BoardService.updateBoardTeamRole(call.userId(), call.param("id"), call.param("teamId"), call.body<UpdateRoleRequest>().role)
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{id}/teams/{teamId}") {
                BoardService.removeBoardTeam(call.userId(), call.param("id"), call.param("teamId"))
                call.respond(HttpStatusCode.NoContent)
            }

            get("/{id}/activity") {
                val uid = call.userId(); val boardId = call.param("id")
                services.AccessService.require(boardId, uid, domain.Permission.VIEW_BOARD)
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val before = call.request.queryParameters["before"]?.toLongOrNull()
                call.respond(ActivityService.query(boardId, null, limit, before))
            }
        }

        route("/lists") {
            post { call.respond(HttpStatusCode.Created, BoardService.createList(call.userId(), call.body<ListDto>())) }
            put("/{id}") { call.respond(BoardService.updateList(call.userId(), call.param("id"), call.body<ListDto>())) }
            delete("/{id}") {
                val q = call.request.queryParameters
                BoardService.deleteList(call.userId(), call.param("id"), q["moveTo"]?.takeIf { it.isNotBlank() }, q["deleteTasks"] == "true")
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
