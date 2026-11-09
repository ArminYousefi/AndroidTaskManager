import com.example.module
import config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import models.*
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end tests: the real Ktor module against an in-memory H2 database (PostgreSQL mode).
 * Run with `./gradlew test`.
 */
class ApiIntegrationTest {

    private fun cfg() = AppConfig(
        env = "development", port = 0, dbUrl = "jdbc:h2:mem:t${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        dbUser = "sa", dbPass = "", jwtSecret = "unit-test-secret-unit-test-secret",
        accessTokenMs = 900_000, refreshTokenMs = 86_400_000, devApiKey = "dev-key"
    )

    private fun ApplicationTestBuilder.api(): HttpClient {
        application { module(cfg()) }
        return createClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    private suspend fun HttpClient.signup(name: String, pw: String = "Str0ng!Pass"): AuthResponse =
        post("/auth/signup") { contentType(ContentType.Application.Json); setBody(SignupRequest(name, pw)) }.body()

    private suspend fun HttpClient.createBoard(token: String, name: String = "Website Project"): BoardDto =
        post("/boards") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(CreateBoardRequest(name = name)) }.body()

    private suspend fun HttpClient.lists(token: String, boardId: String): List<ListDto> = get("/boards/$boardId/lists") { bearerAuth(token) }.body()

    private suspend fun HttpClient.addTask(token: String, boardId: String, listId: String, title: String): HttpResponse =
        post("/tasks") { bearerAuth(token); contentType(ContentType.Application.Json); setBody(TaskDto(boardId = boardId, listId = listId, title = title)) }

    @Test fun weakPasswordIsRejectedWithRuleDetails() = testApplication {
        val c = api()
        val r = c.post("/auth/signup") { contentType(ContentType.Application.Json); setBody(SignupRequest("ali_01", "weak")) }
        assertEquals(HttpStatusCode.BadRequest, r.status)
        val err = r.body<ApiErrorBody>().error
        assertEquals("VALIDATION_ERROR", err.code)
        assertTrue(err.details.containsKey("UPPERCASE"))
    }

    @Test fun newBoardGetsJiraStyleDefaultColumns() = testApplication {
        val c = api(); val u = c.signup("owner01")
        val board = c.createBoard(u.token)
        assertEquals("ADMIN", board.myRole)
        assertEquals(listOf("To Do", "In Progress", "In Review", "Done"), c.lists(u.token, board.id).map { it.title })
    }

    @Test fun viewerCannotCreateTasksButMemberCan() = testApplication {
        val c = api(); val owner = c.signup("owner02"); val viewer = c.signup("viewer02"); val member = c.signup("member02")
        val board = c.createBoard(owner.token); val todo = c.lists(owner.token, board.id).first()
        for ((name, role) in listOf("viewer02" to "VIEWER", "member02" to "MEMBER")) {
            c.post("/boards/${board.id}/members") { bearerAuth(owner.token); contentType(ContentType.Application.Json); setBody(AddMemberRequest(name, role)) }
        }
        assertEquals(HttpStatusCode.Forbidden, c.addTask(viewer.token, board.id, todo.id, "nope").status)
        assertEquals(HttpStatusCode.Created, c.addTask(member.token, board.id, todo.id, "ok").status)
    }

    @Test fun strangerCannotSeeBoardAtAll() = testApplication {
        val c = api(); val owner = c.signup("owner03"); val stranger = c.signup("stranger03")
        val board = c.createBoard(owner.token)
        assertEquals(HttpStatusCode.NotFound, c.get("/boards/${board.id}/tasks") { bearerAuth(stranger.token) }.status)
    }

    @Test fun movingAndRenamingATaskIsLoggedInTheActivityStream() = testApplication {
        val c = api(); val u = c.signup("owner04")
        val board = c.createBoard(u.token); val cols = c.lists(u.token, board.id)
        val task: TaskDto = c.addTask(u.token, board.id, cols[0].id, "Write spec").body()
        assertEquals("WP-1", task.key)
        c.post("/tasks/${task.id}/move") { bearerAuth(u.token); contentType(ContentType.Application.Json); setBody(MoveTaskRequest(cols[1].id, 0)) }
        c.put("/tasks/${task.id}") { bearerAuth(u.token); contentType(ContentType.Application.Json); setBody(task.copy(title = "Write the spec", listId = cols[1].id, dueDate = 1_800_000_000_000)) }
        val types = c.get("/boards/${board.id}/activity") { bearerAuth(u.token) }.body<List<ActivityDto>>().map { it.type }
        assertTrue("TASK_MOVED" in types && "TASK_RENAMED" in types && "TASK_REPLANNED" in types && "TASK_CREATED" in types, "got $types")
    }

    @Test fun changePasswordRequiresCurrentPasswordAndPolicy() = testApplication {
        val c = api(); val u = c.signup("owner05", "Old#Passw0rd")
        suspend fun change(cur: String, new: String) = c.post("/users/me/password") { bearerAuth(u.token); contentType(ContentType.Application.Json); setBody(ChangePasswordRequest(cur, new)) }
        assertEquals("INVALID_CURRENT_PASSWORD", change("wrong", "New#Passw0rd").body<ApiErrorBody>().error.code)
        assertEquals("VALIDATION_ERROR", change("Old#Passw0rd", "short").body<ApiErrorBody>().error.code)
        assertEquals(HttpStatusCode.OK, change("Old#Passw0rd", "New#Passw0rd").status)
        assertEquals(HttpStatusCode.Unauthorized, c.post("/auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest("owner05", "Old#Passw0rd")) }.status)
        assertEquals(HttpStatusCode.OK, c.post("/auth/login") { contentType(ContentType.Application.Json); setBody(LoginRequest("owner05", "New#Passw0rd")) }.status)
    }

    @Test fun nicknameCanChangeButUsernameStaysTheSame() = testApplication {
        val c = api(); val u = c.signup("owner06")
        val r = c.patch("/users/me") { bearerAuth(u.token); contentType(ContentType.Application.Json); setBody(UpdateProfileRequest("Cool Owner")) }
        val me = r.body<UserDto>()
        assertEquals("owner06", me.username); assertEquals("Cool Owner", me.nickname)
    }

    @Test fun errorReportsAreStoredAndReadableOnlyWithDevKey() = testApplication {
        val c = api()
        val up = c.post("/diagnostics/errors") { contentType(ContentType.Application.Json); setBody(ErrorReportBatch(listOf(ErrorReportDto(message = "boom", stackTrace = "at X"))) ) }
        assertEquals(HttpStatusCode.Accepted, up.status)
        assertEquals(HttpStatusCode.Forbidden, c.get("/diagnostics/errors").status)
        val list = c.get("/diagnostics/errors") { header("X-Dev-Key", "dev-key") }.body<List<ErrorReportDto>>()
        assertEquals("boom", list.first().message)
    }

    @Test fun malformedJsonIsACleanBadRequestNotA500() = testApplication {
        val c = api()
        val r = c.post("/auth/login") { contentType(ContentType.Application.Json); setBody("{not json") }
        assertEquals(HttpStatusCode.BadRequest, r.status)
        assertEquals("INVALID_BODY", r.body<ApiErrorBody>().error.code)
    }
}
