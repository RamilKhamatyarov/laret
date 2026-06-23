package com.rkhamatyarov.laret.update

import org.junit.jupiter.api.Test
import java.nio.file.Paths
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProtectedLocationTest {

    @Test
    fun test_unix_system_bin_directories_are_protected() {
        assertTrue(ProtectedLocation.isProtected(Paths.get("/usr/local/bin/laret")))
        assertTrue(ProtectedLocation.isProtected(Paths.get("/usr/bin/laret")))
        assertTrue(ProtectedLocation.isProtected(Paths.get("/opt/laret/laret")))
    }

    @Test
    fun test_windows_program_files_is_protected() {
        assertTrue(ProtectedLocation.isProtected(Paths.get("C:\\Program Files\\laret\\laret.exe")))
    }

    @Test
    fun test_user_space_locations_are_not_protected() {
        assertFalse(ProtectedLocation.isProtected(Paths.get("/home/user/.local/bin/laret")))
        assertFalse(ProtectedLocation.isProtected(Paths.get("/tmp/laret")))
    }

    @Test
    fun test_guidance_mentions_the_path_and_elevation() {
        val path = Paths.get("/usr/local/bin/laret")
        val guidance = ProtectedLocation.guidance(path)

        assertTrue(guidance.contains(path.toString()))
        assertTrue(guidance.contains("sudo"))
    }
}
