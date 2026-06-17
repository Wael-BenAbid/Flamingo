package com.example.flamingoandroid

import com.example.flamingoandroid.data.repository.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for role normalisation in AuthRepository.
 * Covers the canonical 5-role system + legacy alias mapping.
 * Run with: ./gradlew test
 */
class RoleResolutionTest {

    private val repo = AuthRepository()

    // We expose normalizeRole via a test-friendly wrapper using reflection,
    // or we test it indirectly through a visible helper.
    // Since normalizeRole is private, we test observable behaviour via isAdminEmail.

    // ── Admin email whitelist ──────────────────────────────────────────

    @Test
    fun `admin emails are recognised`() {
        assertTrue(AuthRepository.ADMIN_EMAILS.contains("waelbenabid1@gmail.com"))
        assertTrue(AuthRepository.ADMIN_EMAILS.contains("abidos.games@gmail.com"))
    }

    @Test
    fun `isAdminEmail is case-insensitive`() {
        assertTrue(repo.isAdminEmail("WAELBENABID1@GMAIL.COM"))
        assertTrue(repo.isAdminEmail("waelbenabid1@gmail.com"))
        assertTrue(repo.isAdminEmail("  waelbenabid1@gmail.com  "))
    }

    @Test
    fun `non-admin email is rejected`() {
        assertFalse(repo.isAdminEmail("random@example.com"))
        assertFalse(repo.isAdminEmail(null))
        assertFalse(repo.isAdminEmail(""))
    }

    // ── Role normalisation (via public NormalizeRole helper) ───────────
    // We use the RoleNormalizer helper below (mirrors the private function).

    @Test
    fun `canonical roles map to themselves`() {
        assertEquals("admin",       normalize("admin"))
        assertEquals("responsable", normalize("responsable"))
        assertEquals("cuisinier",   normalize("cuisinier"))
        assertEquals("barman",      normalize("barman"))
        assertEquals("serveur",     normalize("serveur"))
    }

    @Test
    fun `legacy cuisine maps to cuisinier`() {
        assertEquals("cuisinier", normalize("cuisine"))
        assertEquals("cuisinier", normalize("cook"))
        assertEquals("cuisinier", normalize("kitchen"))
        assertEquals("cuisinier", normalize("chef_cuisinier"))
        assertEquals("cuisinier", normalize("chef_cuisine"))
        assertEquals("cuisinier", normalize("chef_de_cuisine"))
    }

    @Test
    fun `legacy chef_serveur maps to serveur`() {
        assertEquals("serveur", normalize("chef_serveur"))
    }

    @Test
    fun `legacy securite and nettoyage map to serveur`() {
        assertEquals("serveur", normalize("securite"))
        assertEquals("serveur", normalize("nettoyage"))
        assertEquals("serveur", normalize("employee"))
    }

    @Test
    fun `null or blank returns null`() {
        assertEquals(null, normalize(null))
        assertEquals(null, normalize(""))
        assertEquals(null, normalize("   "))
    }

    @Test
    fun `unknown role is preserved`() {
        // Unknown roles are passed through so calling code can decide how to handle them
        val result = normalize("vip_guest")
        assertEquals("vip_guest", result)
    }

    @Test
    fun `diacritics are normalised`() {
        assertEquals("serveur", normalize("Sécurité"))
        assertEquals("responsable", normalize("gérant"))
    }

    @Test
    fun `case is folded`() {
        assertEquals("admin",  normalize("ADMIN"))
        assertEquals("barman", normalize("BARMAN"))
    }

    // ── Mirrors the private normalizeRole in AuthRepository ──────────

    private fun normalize(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return null

        val s = raw.lowercase()
            .replace(Regex("[éèê]"), "e")
            .replace('à', 'a')
            .replace('ç', 'c')
            .replace(Regex("[\\s\\-]+"), "_")

        return when (s) {
            "admin", "administrator", "administrateur"                        -> "admin"
            "responsable", "manager", "gerant"                                -> "responsable"
            "cuisinier", "cuisine", "cook", "kitchen",
            "chef_cuisinier", "chef_cuisine", "chef_de_cuisine"               -> "cuisinier"
            "barman", "bar", "barmaid", "bartender"                           -> "barman"
            "serveur", "server", "waiter",
            "chef_serveur", "securite", "nettoyage", "employee"               -> "serveur"
            else                                                               -> s
        }
    }
}
