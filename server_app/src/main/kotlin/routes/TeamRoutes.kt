package routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import models.*
import plugins.body
import services.TeamService

/** `/teams` - create teams and manage their members. */
fun Route.teamRoutes() {
    authenticate("auth-jwt") {
        route("/teams") {
            get { call.respond(TeamService.list(call.userId())) }
            post { call.respond(HttpStatusCode.Created, TeamService.create(call.userId(), call.body<TeamRequest>())) }
            put("/{id}") { call.respond(TeamService.update(call.userId(), call.param("id"), call.body<TeamRequest>())) }
            delete("/{id}") { TeamService.delete(call.userId(), call.param("id")); call.respond(HttpStatusCode.NoContent) }

            get("/{id}/members") { call.respond(TeamService.members(call.userId(), call.param("id"))) }
            post("/{id}/members") { call.respond(HttpStatusCode.Created, TeamService.addMember(call.userId(), call.param("id"), call.body<AddMemberRequest>())) }
            put("/{id}/members/{userId}") {
                TeamService.updateRole(call.userId(), call.param("id"), call.param("userId"), call.body<UpdateRoleRequest>().role)
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{id}/members/{userId}") {
                TeamService.removeMember(call.userId(), call.param("id"), call.param("userId"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
