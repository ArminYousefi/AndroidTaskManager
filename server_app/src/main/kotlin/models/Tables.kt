
package models

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object Users : Table("users") {
    val id = varchar("id", 36) // UUID stored as string
    val username = varchar("username", 50).uniqueIndex()
    val email = varchar("email", 255).nullable()
    val passwordHash = varchar("password_hash", 60) // bcrypt length ~60
    val createdAt = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_tokens") {
    val token = varchar("token", 36) // UUID stored as string
    val userId = varchar("user_id", 36).references(Users.id)
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")

    override val primaryKey = PrimaryKey(token)
}


object Boards : Table("boards") {
    val id = varchar("id", 36)
    val title = varchar("title", 255)
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object Lists : Table("lists") {
    val id = varchar("id", 36)
    val boardId = varchar("board_id", 36).references(Boards.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val position = integer("position")
    val updatedAt = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object Tasks : Table("tasks") {
    val id = varchar("id", 36)
    val boardId = varchar("board_id", 36).references(Boards.id, onDelete = ReferenceOption.CASCADE)
    val listId = varchar("list_id", 36).references(Lists.id, onDelete = ReferenceOption.CASCADE)
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val position = integer("position")
    val status = varchar("status", 50)
    val updatedAt = long("updated_at")
    val isDeleted = bool("is_deleted").default(false)

    override val primaryKey = PrimaryKey(id)
}
