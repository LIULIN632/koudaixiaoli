package com.pocket.xiaoli.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [VersionCompare] 的用例。
 *
 * 前四组来自岱宗盒子原实现的使用场景（它的 tag 有 `4.72` 这种两段写法、有预发布），
 * 后面几组是本项目自己的发版习惯（`0.1.44`、`-dev.N`、`v` 前缀）。
 */
class VersionCompareTest {

    @Test
    fun sameVersion_isZero() {
        assertEquals(0, VersionCompare.compare("0.1.44", "0.1.44"))
        assertEquals(0, VersionCompare.compare("v0.1.44", "0.1.44"))
        assertEquals(0, VersionCompare.compare("1.2", "1.2.0"))
    }

    @Test
    fun patchAndMinorBumps() {
        assertTrue(VersionCompare.compare("0.1.45", "0.1.44") > 0)
        assertTrue(VersionCompare.compare("0.2.0", "0.1.44") > 0)
        assertTrue(VersionCompare.compare("12.0.0", "11.99.99") > 0)
        assertTrue(VersionCompare.compare("0.1.44", "0.1.45") < 0)
    }

    @Test
    fun twoSegmentLegacyVersion() {
        // 岱宗盒子的老写法：4.72 意思是 4.7.2
        assertEquals(0, VersionCompare.compare("4.72", "4.7.2"))
        assertTrue(VersionCompare.compare("4.72", "4.7.1") > 0)
        assertTrue(VersionCompare.compare("4.61", "4.7.2") < 0)
        // 已经有第三段的不做拆分
        assertEquals(0, VersionCompare.compare("4.7.3", "4.7.3"))
    }

    @Test
    fun prereleaseIsOlderThanRelease() {
        assertTrue(VersionCompare.compare("0.1.41-dev.3", "0.1.41") < 0)
        assertTrue(VersionCompare.compare("0.1.41", "0.1.41-dev.3") > 0)
        assertTrue(VersionCompare.compare("1.0.0-beta", "1.0.0") < 0)
    }

    @Test
    fun prereleaseNumericSegmentsCompareNumerically() {
        // 字典序会认为 "dev.10" < "dev.3"，这里必须是数字比较
        assertTrue(VersionCompare.compare("0.1.41-dev.10", "0.1.41-dev.3") > 0)
        assertEquals(0, VersionCompare.compare("0.1.41-dev.3", "0.1.41-dev.3"))
        // 段数多的更大
        assertTrue(VersionCompare.compare("0.1.41-dev.3.1", "0.1.41-dev.3") > 0)
    }

    @Test
    fun isNewerBasic() {
        assertTrue(VersionCompare.isNewer("v0.1.45", "0.1.44"))
        assertFalse(VersionCompare.isNewer("0.1.44", "0.1.44"))
        assertFalse(VersionCompare.isNewer("v0.1.43", "0.1.44"))
    }

    @Test
    fun isNewerWithPrereleaseTag() {
        // 老实现（只认三段整数）在这里会判 false，于是预览版永远收不到
        assertTrue(VersionCompare.isNewer("v0.1.45-dev.7", "0.1.44"))
        assertFalse(VersionCompare.isNewer("v0.1.44-dev.7", "0.1.44"))
    }

    @Test
    fun unparsableCurrentVersion_neverClaimsUpdate() {
        assertFalse(VersionCompare.isNewer("v11.0.16", "bad"))
        assertFalse(VersionCompare.isNewer("v11.0.16", ""))
    }

    @Test
    fun unparsableCandidate_isNotNewer() {
        assertFalse(VersionCompare.isNewer("abc", "0.1.44"))
        assertFalse(VersionCompare.isNewer("", "0.1.44"))
    }
}
