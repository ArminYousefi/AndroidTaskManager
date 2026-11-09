package services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import domain.NicknamePolicy
import domain.PasswordPolicy
import domain.SlidingWindowLimiter
import domain.UsernamePolicy
import models.AuthResponse
import models.RefreshTokens
import models.UserDto
import models.Users
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import plugins.ApiException
import java.util.Date
import java.util.UUID

/** A freshly issued access/refresh token pair. */
data class JwtTokens(val accessToken: String, val refreshToken: String, val refreshExpiresAt: Long)

/**
 * Accounts and sessions: signup, login (with brute-force throttle), token refresh, logout,
 * profile (nickname) and password change.
 *
 * @param jwtSecret HMAC secret for access tokens.
 * @param accessTokenTtlMs access-token lifetime.
 * @param refreshTokenTtlMs refresh-token lifetime.
 */
class AuthService(
    private val jwtSecret: String,
    private val accessTokenTtlMs: Long,
    private val refreshTokenTtlMs: Long
) {
    init { require(jwtSecret.isNotBlank()) { "JWT secret must be provided via env JWT_SECRET" } }

    /** 5 failed logins per username per minute. */
    private val loginFailures = SlidingWindowLimiter(maxEvents = 5, windowMs = 60_000)

    /** 5 password-change attempts per user per minute (stops guessing the current password). */
    private val passwordAttempts = SlidingWindowLimiter(maxEvents = 5, windowMs = 60_000)

    /**
     * Creates an account and signs the user in.
     *
     * @throws ApiException.Validation when username, password, nickname or e-mail break the rules
     * (`details` lists every failed password rule).
     * @throws ApiException.Conflict (`USER_EXISTS`) when the username is taken.
     */
    fun signup(username: String, password: String, email: String?, nickname: String?): AuthResponse {
        val name = username.trim()
        if (!UsernamePolicy.isValid(name)) throw ApiException.Validation("Username must be 3-30 characters: letters, numbers, . _ -")
        val failed = PasswordPolicy.violations(PasswordPolicy.evaluate(password, name))
        if (failed.isNotEmpty()) throw ApiException.Validation("Password does not meet the requirements", failed.associate { it.name to it.label })
        val nick = nickname?.takeIf { it.isNotBlank() }?.let { NicknamePolicy.normalize(it) }
        nick?.let { n -> NicknamePolicy.validate(n)?.let { throw ApiException.Validation(it.message) } }
        val mail = email?.trim()?.takeIf { it.isNotEmpty() }
        if (mail != null && (!mail.contains('@') || mail.length > 255)) throw ApiException.Validation("E-mail address is not valid")

        val userId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        try {
            transaction {
                if (Users.select { Users.username eq name }.any()) throw ApiException.Conflict("Username is already taken", "USER_EXISTS")
                Users.insert {
                    it[Users.id] = userId; it[Users.username] = name; it[Users.nickname] = nick
                    it[Users.email] = mail; it[Users.passwordHash] = hash(password)
                    it[Users.createdAt] = now; it[Users.updatedAt] = now
                }
            }
        } catch (e: ExposedSQLException) {
            throw ApiException.Conflict("Username is already taken", "USER_EXISTS")
        }
        return issueSession(UserDto(userId, name, nick, mail))
    }

    /**
     * Signs a user in.
     *
     * @throws ApiException.TooManyRequests after 5 failures in a minute for that username.
     * @throws ApiException.Unauthorized (`INVALID_CREDENTIALS`) for unknown user or wrong password (same message for both).
     */
    fun login(username: String, password: String): AuthResponse {
        val key = username.trim().lowercase()
        if (loginFailures.retryAfterMs(key) > 0) throw ApiException.TooManyRequests("Too many failed attempts. Try again in a minute.")
        val row = transaction { Users.select { Users.username eq username.trim() }.firstOrNull() }
        val ok = row != null && BCrypt.verifyer().verify(password.toCharArray(), row[Users.passwordHash]).verified
        if (!ok) {
            loginFailures.tryAcquire(key)
            throw ApiException.Unauthorized("Invalid username or password", "INVALID_CREDENTIALS")
        }
        loginFailures.reset(key)
        return issueSession(toDto(row!!))
    }

    /**
     * Rotates a refresh token.
     *
     * @throws ApiException.Unauthorized (`INVALID_REFRESH_TOKEN`) when unknown or expired.
     */
    fun refresh(refreshToken: String): AuthResponse {
        val now = System.currentTimeMillis()
        val row = transaction { RefreshTokens.select { RefreshTokens.token eq refreshToken }.firstOrNull() }
            ?: throw ApiException.Unauthorized("Session expired, please sign in again", "INVALID_REFRESH_TOKEN")
        transaction { RefreshTokens.deleteWhere { RefreshTokens.token eq refreshToken } }
        if (row[RefreshTokens.expiresAt] < now) throw ApiException.Unauthorized("Session expired, please sign in again", "INVALID_REFRESH_TOKEN")
        val user = transaction { Users.select { Users.id eq row[RefreshTokens.userId] }.firstOrNull() }
            ?: throw ApiException.Unauthorized("Account no longer exists", "INVALID_REFRESH_TOKEN")
        return issueSession(toDto(user))
    }

    /** Revokes [refreshToken] (if given) so it can never be used again. Always succeeds. */
    fun logout(refreshToken: String?) {
        if (!refreshToken.isNullOrBlank()) transaction { RefreshTokens.deleteWhere { RefreshTokens.token eq refreshToken } }
    }

    /** @throws ApiException.NotFound when [userId] does not exist. */
    fun me(userId: String): UserDto = transaction { Users.select { Users.id eq userId }.firstOrNull() }?.let(::toDto)
        ?: throw ApiException.NotFound("User not found")

    /**
     * Changes the display name. The username can never change.
     *
     * @throws ApiException.Validation when [nickname] breaks [NicknamePolicy].
     */
    fun updateNickname(userId: String, nickname: String): UserDto {
        val n = NicknamePolicy.normalize(nickname)
        NicknamePolicy.validate(n)?.let { throw ApiException.Validation(it.message) }
        transaction { Users.update({ Users.id eq userId }) { it[Users.nickname] = n; it[Users.updatedAt] = System.currentTimeMillis() } }
        return me(userId)
    }

    /**
     * Changes the password after verifying the current one, revokes *all* refresh tokens (other devices are
     * signed out) and returns a fresh session for the calling device.
     *
     * @throws ApiException.TooManyRequests after 5 attempts in a minute.
     * @throws ApiException.BadRequest (`INVALID_CURRENT_PASSWORD`) when the current password is wrong. (400, not 401,
     * so the app does not mistake it for an expired token.)
     * @throws ApiException.Validation when the new password breaks [PasswordPolicy] (`details` = failed rules).
     */
    fun changePassword(userId: String, currentPassword: String, newPassword: String): AuthResponse {
        if (!passwordAttempts.tryAcquire(userId)) throw ApiException.TooManyRequests("Too many attempts. Try again in a minute.")
        val row = transaction { Users.select { Users.id eq userId }.firstOrNull() } ?: throw ApiException.NotFound("User not found")
        if (!BCrypt.verifyer().verify(currentPassword.toCharArray(), row[Users.passwordHash]).verified)
            throw ApiException.BadRequest("INVALID_CURRENT_PASSWORD", "Current password is incorrect")
        val failed = PasswordPolicy.violations(PasswordPolicy.evaluate(newPassword, row[Users.username], currentPassword))
        if (failed.isNotEmpty()) throw ApiException.Validation("New password does not meet the requirements", failed.associate { it.name to it.label })
        transaction {
            Users.update({ Users.id eq userId }) { it[Users.passwordHash] = hash(newPassword); it[Users.updatedAt] = System.currentTimeMillis() }
            RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
        }
        passwordAttempts.reset(userId)
        return issueSession(toDto(row))
    }

    /**
     * Finds users to invite, by (partial) username or nickname.
     *
     * @param query at least 2 characters; characters other than letters, digits and `_ . -` are ignored.
     * @param callerId excluded from the results.
     */
    fun search(query: String, callerId: String): List<UserDto> {
        val q = query.lowercase().filter { it.isLetterOrDigit() || it in "_.-" }
        if (q.length < 2) return emptyList()
        return transaction {
            Users.select { ((Users.username.lowerCase() like "%$q%") or (Users.nickname.lowerCase() like "%$q%")) and (Users.id neq callerId) }
                .limit(10).map(::toDto)
        }
    }

    /** Verifies an access token and returns its user id, or null. */
    fun verifyToken(token: String): String? = try {
        JWT.require(Algorithm.HMAC256(jwtSecret)).build().verify(token).getClaim("uid").asString()
    } catch (e: Throwable) { null }

    private fun hash(password: String) = BCrypt.withDefaults().hashToString(12, password.toCharArray())

    private fun toDto(r: ResultRow) = UserDto(r[Users.id], r[Users.username], r[Users.nickname], r[Users.email])

    private fun issueSession(user: UserDto): AuthResponse {
        val now = System.currentTimeMillis()
        val tokens = generateTokens(user.id, now)
        transaction {
            RefreshTokens.insert {
                it[RefreshTokens.token] = tokens.refreshToken; it[RefreshTokens.userId] = user.id
                it[RefreshTokens.createdAt] = now; it[RefreshTokens.expiresAt] = tokens.refreshExpiresAt
            }
        }
        return AuthResponse(tokens.accessToken, tokens.refreshToken, user)
    }

    private fun generateTokens(userId: String, now: Long): JwtTokens {
        val access = JWT.create().withClaim("uid", userId).withIssuedAt(Date(now)).withExpiresAt(Date(now + accessTokenTtlMs))
            .sign(Algorithm.HMAC256(jwtSecret))
        return JwtTokens(access, UUID.randomUUID().toString(), now + refreshTokenTtlMs)
    }
}
