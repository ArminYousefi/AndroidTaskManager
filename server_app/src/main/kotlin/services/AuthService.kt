
package services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import models.AuthResponse
import models.RefreshTokens
import models.UserDto
import models.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

data class JwtTokens(val accessToken: String, val refreshToken: String, val refreshExpiresAt: Long)

class AuthService(
    private val jwtSecret: String,
    private val accessTokenTtlMs: Long,
    private val refreshTokenTtlMs: Long
) {

    init {
        if (jwtSecret.isBlank()) throw IllegalArgumentException("JWT secret must be provided via env JWT_SECRET")
    }

    fun signup(username: String, password: String, email: String?): AuthResponse {
        val existing = transaction { Users.select { Users.username eq username }.firstOrNull() }
        if (existing != null) throw IllegalArgumentException("User exists")

        val hashed = BCrypt.withDefaults().hashToString(12, password.toCharArray())
        val userId = UUID.randomUUID().toString()
        val now = Instant.now().toEpochMilli()

        // Insert new user
        transaction {
            Users.insert {
                it[Users.id] = userId
                it[Users.username] = username
                it[Users.email] = email
                it[Users.passwordHash] = hashed
                it[Users.createdAt] = now
            }
        }

        val tokens = generateTokens(userId)

        // Save refresh token
        transaction {
            RefreshTokens.insert {
                it[RefreshTokens.token] = tokens.refreshToken
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.createdAt] = now
                it[RefreshTokens.expiresAt] = tokens.refreshExpiresAt
            }
        }

        val userDto = UserDto(id = userId, username = username, email = email)
        return AuthResponse(tokens.accessToken, tokens.refreshToken, userDto)
    }


    fun login(username: String, password: String): AuthResponse {
        val row = transaction { Users.select { Users.username eq username }.firstOrNull() }
            ?: throw IllegalArgumentException("Invalid credentials")

        val storedHash = row[Users.passwordHash]
        val verify = BCrypt.verifyer().verify(password.toCharArray(), storedHash)
        if (!verify.verified) throw IllegalArgumentException("Invalid credentials")

        val userId = row[Users.id]
        val tokens = generateTokens(userId)

        // Save refresh token
        transaction {
            RefreshTokens.insert {
                it[RefreshTokens.token] = tokens.refreshToken
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.expiresAt] = tokens.refreshExpiresAt
                it[RefreshTokens.createdAt] = Instant.now().toEpochMilli()
            }
        }

        val userDto = UserDto(id = userId, username = row[Users.username], email = row[Users.email])
        return AuthResponse(tokens.accessToken, tokens.refreshToken, userDto)
    }

    fun refresh(refreshToken: String): AuthResponse? {
        val now = Instant.now().toEpochMilli()

        val row = transaction {
            RefreshTokens.select { RefreshTokens.token eq refreshToken }.firstOrNull()
        } ?: return null

        val expires = row[RefreshTokens.expiresAt]
        if (expires < now) {
            transaction { RefreshTokens.deleteWhere { RefreshTokens.token eq refreshToken } }
            return null
        }

        val userId = row[RefreshTokens.userId]
        val tokens = generateTokens(userId)

        transaction {
            RefreshTokens.deleteWhere { RefreshTokens.token eq refreshToken }
            RefreshTokens.insert {
                it[RefreshTokens.token] = tokens.refreshToken
                it[RefreshTokens.userId] = userId
                it[RefreshTokens.expiresAt] = tokens.refreshExpiresAt
                it[RefreshTokens.createdAt] = now
            }
        }

        val u = transaction { Users.select { Users.id eq userId }.first() }
        val userDto = UserDto(id = u[Users.id], username = u[Users.username], email = u[Users.email])
        return AuthResponse(tokens.accessToken, tokens.refreshToken, userDto)
    }

    private fun generateTokens(userId: String): JwtTokens {
        val now = Instant.now().toEpochMilli()
        val accessExpiresAt = now + accessTokenTtlMs
        val refreshExpiresAt = now + refreshTokenTtlMs
        val algorithm = Algorithm.HMAC256(jwtSecret)

        val accessToken = JWT.create()
            .withClaim("uid", userId)
            .withIssuedAt(java.util.Date(now))
            .withExpiresAt(java.util.Date(accessExpiresAt))
            .sign(algorithm)

        val refreshToken = UUID.randomUUID().toString()
        return JwtTokens(accessToken, refreshToken, refreshExpiresAt)
    }


    /**
     * Verifies a JWT access token and returns the userId (uid claim) if valid, otherwise null.
     */
    fun verifyToken(token: String): String? {
        return try {
            val algorithm = Algorithm.HMAC256(jwtSecret)
            val verifier = JWT.require(algorithm).build()
            val decoded = verifier.verify(token)
            decoded.getClaim("uid").asString()
        } catch (_: Throwable) {
            null
        }
    }
}