package com.rkhamatyarov.laret.update

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionComparatorTest {

    @Test
    fun test_higher_patch_is_newer() {
        assertTrue(VersionComparator.isNewer("0.2.1", "0.2.0"))
        assertFalse(VersionComparator.isNewer("0.2.0", "0.2.1"))
    }

    @Test
    fun test_higher_minor_and_major_are_newer() {
        assertTrue(VersionComparator.isNewer("0.3.0", "0.2.9"))
        assertTrue(VersionComparator.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun test_release_is_newer_than_its_snapshot() {
        assertTrue(VersionComparator.isNewer("0.2.0", "0.2.0-SNAPSHOT"))
        assertFalse(VersionComparator.isNewer("0.2.0-SNAPSHOT", "0.2.0"))
    }

    @Test
    fun test_equal_versions_are_not_newer() {
        assertFalse(VersionComparator.isNewer("0.2.0", "0.2.0"))
        assertFalse(VersionComparator.isNewer("0.2.0-SNAPSHOT", "0.2.0-SNAPSHOT"))
    }

    @Test
    fun test_leading_v_prefix_is_tolerated() {
        assertTrue(VersionComparator.isNewer("v0.3.0", "0.2.0"))
    }

    @Test
    fun test_unparseable_versions_never_trigger_update() {
        assertFalse(VersionComparator.isNewer("not-a-version", "0.2.0"))
        assertFalse(VersionComparator.isNewer("0.3.0", "garbage"))
    }

    @Test
    fun test_is_valid_recognises_formats() {
        assertTrue(VersionComparator.isValid("0.2.0"))
        assertTrue(VersionComparator.isValid("v1.2.3-SNAPSHOT"))
        assertFalse(VersionComparator.isValid("1.0"))
        assertFalse(VersionComparator.isValid("abc"))
    }

    @Test
    fun test_is_major_bump_detects_breaking_upgrades() {
        assertTrue(VersionComparator.isMajorBump("2.0.0", "1.4.3"))
        assertTrue(VersionComparator.isMajorBump("v2.0.0", "1.9.9"))
    }

    @Test
    fun test_minor_and_patch_changes_are_not_major_bumps() {
        assertFalse(VersionComparator.isMajorBump("1.5.0", "1.4.0"))
        assertFalse(VersionComparator.isMajorBump("1.4.4", "1.4.3"))
        assertFalse(VersionComparator.isMajorBump("not-a-version", "1.0.0"))
    }
}
