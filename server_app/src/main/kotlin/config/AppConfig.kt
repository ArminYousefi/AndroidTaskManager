package config

/**
 * Runtime configuration, read once from environment variables.
 *
 * @property env `production` or `development` (default). Production refuses to start with the dev JWT secret.
 * @property port HTTP port.
 * @property dbUrl JDBC url of the database.
 * @property dbUser database user.
 * @property dbPass database password.
 * @property jwtSecret HMAC secret for access tokens.
 * @property accessTokenMs access-token lifetime.
 * @property refreshTokenMs refresh-token lifetime.
 * @property devApiKey when set, enables `GET /diagnostics/errors` for developers (sent as `X-Dev-Key`).
 */
data class AppConfig(
    val env: String,
    val port: Int,
    val dbUrl: String,
    val dbUser: String,
    val dbPass: String,
    val jwtSecret: String,
    val accessTokenMs: Long,
    val refreshTokenMs: Long,
    val devApiKey: String?
) {
    /** @return true when running with `APP_ENV=production`. */
    val isProduction: Boolean get() = env.equals("production", ignoreCase = true)

    companion object {
        /** Development-only fallback secret. Never accepted in production. */
        const val DEV_SECRET = "my-hardcoded-secret-key"

        /**
         * Builds the configuration from [env] (defaults to the process environment).
         *
         * @throws IllegalStateException in production when `JWT_SECRET` is missing or the dev default.
         */
        fun load(env: Map<String, String> = System.getenv()): AppConfig {
            val appEnv = env["APP_ENV"] ?: "development"
            val secret = env["JWT_SECRET"] ?: DEV_SECRET
            val cfg = AppConfig(
                env = appEnv,
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                dbUrl = env["JDBC_DATABASE_URL"] ?: "jdbc:postgresql://localhost:5432/taskmanagerapp",
                dbUser = env["DB_USER"] ?: "postgres",
                dbPass = env["DB_PASS"] ?: "123456",
                jwtSecret = secret,
                accessTokenMs = env["ACCESS_TOKEN_MS"]?.toLongOrNull() ?: 900_000L,
                refreshTokenMs = env["REFRESH_TOKEN_MS"]?.toLongOrNull() ?: (30L * 24 * 60 * 60 * 1000),
                devApiKey = env["DEV_API_KEY"]?.takeIf { it.isNotBlank() }
            )
            check(!(cfg.isProduction && (secret == DEV_SECRET || secret.length < 16))) {
                "JWT_SECRET must be set to a strong value (16+ chars) when APP_ENV=production"
            }
            return cfg
        }
    }
}
