package domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unit tests for the pure rules shared by server and app (password, nickname, roles, keys, ordering, limiter). */
class DomainRulesTest {

    private fun failed(pw: String, user: String? = null, cur: String? = null) =
        PasswordPolicy.violations(PasswordPolicy.evaluate(pw, user, cur))

    @Test fun strongPasswordPassesEveryRule() {
        assertTrue(PasswordPolicy.isValid(PasswordPolicy.evaluate("Str0ng!Pass", "ali", "Old#Pass1")))
    }

    @Test fun eachRuleIsReportedIndependently() {
        assertEquals(listOf(PasswordRule.MIN_LENGTH), failed("Ab1!xyz").filter { it == PasswordRule.MIN_LENGTH })
        assertTrue(PasswordRule.UPPERCASE in failed("lower1!lower"))
        assertTrue(PasswordRule.LOWERCASE in failed("UPPER1!UPPER"))
        assertTrue(PasswordRule.DIGIT in failed("NoDigits!Here"))
        assertTrue(PasswordRule.SPECIAL in failed("NoSpecial123"))
        assertTrue(PasswordRule.NO_SPACES in failed("Has Space1!x"))
    }

    @Test fun usernameInsidePasswordIsRejectedCaseInsensitively() {
        assertTrue(PasswordRule.NOT_USERNAME in failed("MyALIpass1!", user = "ali"))
        assertFalse(PasswordRule.NOT_USERNAME in failed("Other#Pass1", user = "ali"))
        // very short usernames are ignored, otherwise "a" would ban almost every password
        assertFalse(PasswordRule.NOT_USERNAME in failed("Apple#Pass1", user = "a"))
    }

    @Test fun newPasswordMustDifferFromCurrent() {
        assertTrue(PasswordRule.DIFFERENT_FROM_CURRENT in failed("Same#Pass1", cur = "Same#Pass1"))
        assertFalse(PasswordRule.DIFFERENT_FROM_CURRENT in failed("New#Pass12", cur = "Same#Pass1"))
        // rule is absent in the signup flow
        assertTrue(PasswordPolicy.evaluate("New#Pass12").none { it.rule == PasswordRule.DIFFERENT_FROM_CURRENT })
    }

    @Test fun bcryptByteLimitIsEnforced() {
        assertTrue(PasswordRule.MAX_LENGTH in failed("Aa1!" + "x".repeat(80)))
        // 40 Persian letters = 80 bytes even though only 40 characters
        assertTrue(PasswordRule.MAX_LENGTH in failed("Aa1!" + "س".repeat(40)))
    }

    @Test fun strengthMeterIsMonotonic() {
        fun s(p: String) = PasswordPolicy.strength(p, PasswordPolicy.evaluate(p))
        assertEquals(PasswordStrength.NONE, s(""))
        assertEquals(PasswordStrength.WEAK, s("abc"))
        assertEquals(PasswordStrength.GOOD, s("Str0ng!Pass"))
        assertEquals(PasswordStrength.STRONG, s("Str0ng!Passw0rd"))
    }

    @Test fun nicknameRules() {
        assertNull(NicknamePolicy.validate("Ali Rezaei"))
        assertNull(NicknamePolicy.validate("  علی   رضایی "))
        assertEquals(NicknameError.TOO_SHORT, NicknamePolicy.validate(" a "))
        assertEquals(NicknameError.TOO_LONG, NicknamePolicy.validate("x".repeat(31)))
        assertEquals(NicknameError.INVALID_CHARS, NicknamePolicy.validate("bad<script>"))
        assertEquals("Ali Rezaei", NicknamePolicy.normalize("  Ali    Rezaei "))
    }

    @Test fun usernameRules() {
        assertTrue(UsernamePolicy.isValid("ali_1.x-y"))
        assertFalse(UsernamePolicy.isValid("al"))
        assertFalse(UsernamePolicy.isValid("has space"))
    }

    @Test fun permissionMatrix() {
        assertTrue(RolePolicy.can(BoardRole.VIEWER, Permission.VIEW_BOARD))
        assertFalse(RolePolicy.can(BoardRole.VIEWER, Permission.EDIT_TASK))
        assertFalse(RolePolicy.can(BoardRole.VIEWER, Permission.COMMENT))
        assertTrue(RolePolicy.can(BoardRole.MEMBER, Permission.MOVE_TASK))
        assertTrue(RolePolicy.can(BoardRole.MEMBER, Permission.DELETE_TASK))
        assertFalse(RolePolicy.can(BoardRole.MEMBER, Permission.MANAGE_COLUMNS))
        assertFalse(RolePolicy.can(BoardRole.MEMBER, Permission.MANAGE_MEMBERS))
        assertFalse(RolePolicy.can(BoardRole.MEMBER, Permission.DELETE_BOARD))
        Permission.entries.forEach { assertTrue(RolePolicy.can(BoardRole.ADMIN, it)) }
        Permission.entries.forEach { assertFalse(RolePolicy.can(null, it)) }
    }

    @Test fun strongestRoleWins() {
        assertEquals(BoardRole.ADMIN, BoardRole.strongest(BoardRole.VIEWER, BoardRole.ADMIN))
        assertEquals(BoardRole.MEMBER, BoardRole.strongest(null, BoardRole.MEMBER))
        assertNull(BoardRole.strongest(null, null))
    }

    @Test fun defaultTemplateMatchesJiraStyleColumns() {
        val cols = BoardTemplate.STANDARD.columns
        assertEquals(listOf("To Do", "In Progress", "In Review", "Done"), cols.map { it.title })
        assertEquals(ColumnCategory.DONE, cols.last().category)
        assertEquals(BoardTemplate.STANDARD, BoardTemplate.parse("nonsense"))
    }

    @Test fun boardKeys() {
        assertEquals("WP", BoardKeys.generate("Website Project"))
        assertEquals("MWR", BoardKeys.generate("my web redesign"))
        assertEquals("TAS", BoardKeys.generate("Tasks"))
        assertEquals(BoardKeys.FALLBACK, BoardKeys.generate("برد اول"))
        assertTrue(BoardKeys.isValid("WEB2"))
        assertFalse(BoardKeys.isValid("2WEB"))
        assertEquals("ABC", BoardKeys.normalizeOrGenerate(" abc ", "x"))
        assertEquals("WP", BoardKeys.normalizeOrGenerate("!!", "Website Project"))
    }

    @Test fun orderingMovesAndClamps() {
        val l = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), Ordering.moveTo(l, "a", 2))
        assertEquals(listOf("d", "a", "b", "c"), Ordering.moveTo(l, "d", 0))
        assertEquals(listOf("b", "c", "d", "a"), Ordering.moveTo(l, "a", 99))
        assertEquals(listOf("x", "a", "b", "c", "d"), Ordering.moveTo(l, "x", -5))
        assertEquals(mapOf("a" to 0, "b" to 1), Ordering.positions(listOf("a", "b")))
    }

    @Test fun limiterBlocksThenRecovers() {
        var t = 0L
        val lim = SlidingWindowLimiter(3, 1000) { t }
        repeat(3) { assertTrue(lim.tryAcquire("k")) }
        assertFalse(lim.tryAcquire("k"))
        assertTrue(lim.tryAcquire("other"))
        assertEquals(1000, lim.retryAfterMs("k"))
        t = 1001
        assertTrue(lim.tryAcquire("k"))
        lim.reset("k")
        assertEquals(0, lim.retryAfterMs("k"))
    }
}
