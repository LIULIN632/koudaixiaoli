package com.pocket.xiaoli.data.courseware

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 清单解析的边界：**一条坏条目不该让整份清单失效**，但坏条目本身必须被丢掉。
 *
 * 这是"课件不进 APK"之后最容易出事的地方 —— 清单写错一个字段，用户看到的是
 * "课件消失了"，而我们这边没有任何崩溃可查。
 */
class CoursewareManifestTest {

    private val sha = "5c1c1efbaa8f8e805526f336502e17ff975dbfd52831d56259644b033d03cadf"

    @Test
    fun `解析正常清单`() {
        val json = """
            {
              "version": 4,
              "updatedAt": "2026-11-03",
              "courses": [
                {"id":"beam-deflection","title":"简支梁","subject":"材料力学","version":2,
                 "size":25656,"sha256":"$sha","urls":["https://gitee.com/x/y.zip"],
                 "kind":"course","requires":["vendor"]},
                {"id":"vendor","title":"three.js","version":1,"size":199367,"sha256":"$sha",
                 "urls":["https://gitee.com/x/v.zip"],"kind":"shared"}
              ]
            }
        """.trimIndent()
        val manifest = CoursewareManifestParser.parse(json)!!
        assertEquals(4, manifest.version)
        assertEquals(2, manifest.packages.size)
        val course = manifest.packages.first { it.id == "beam-deflection" }
        assertEquals(2, course.version)
        assertEquals(25656L, course.size)
        assertEquals(listOf("vendor"), course.requires)
        assertFalse(course.isShared)
        assertTrue(manifest.packages.first { it.id == "vendor" }.isShared)
    }

    @Test
    fun `坏条目被丢掉，好条目照常解析`() {
        val json = """
            {"courses":[
              {"id":"ok-one","version":1,"size":10,"urls":["https://a/b.zip"]},
              {"id":"Bad Id","version":1,"size":10,"urls":["https://a/b.zip"]},
              {"id":"../escape","version":1,"size":10,"urls":["https://a/b.zip"]},
              {"id":"no-url","version":1,"size":10,"urls":[]},
              {"id":"http-only","version":1,"size":10,"urls":["http://a/b.zip"]},
              {"id":"zero-size","version":1,"size":0,"urls":["https://a/b.zip"]}
            ]}
        """.trimIndent()
        val manifest = CoursewareManifestParser.parse(json)!!
        assertEquals(listOf("ok-one"), manifest.packages.map { it.id })
    }

    @Test
    fun `sha 不是 64 位十六进制时置空而不是整条丢掉`() {
        val json = """{"courses":[{"id":"a-b","version":1,"size":10,"urls":["https://a/b.zip"],"sha256":"nope"}]}"""
        val manifest = CoursewareManifestParser.parse(json)!!
        assertEquals("", manifest.packages.single().sha256)
    }

    @Test
    fun `不是 json 或空文本时不崩`() {
        assertNull(CoursewareManifestParser.parse(null))
        assertNull(CoursewareManifestParser.parse(""))
        assertNull(CoursewareManifestParser.parse("<html>404</html>"))
    }

    @Test
    fun `BOM 与前后空白不影响解析`() {
        val json = "\uFEFF  {\"courses\":[{\"id\":\"a-b\",\"version\":1,\"size\":10,\"urls\":[\"https://a/b.zip\"]}]}  "
        assertEquals("a-b", CoursewareManifestParser.parse(json)!!.packages.single().id)
    }

    @Test
    fun `没有 courses 字段时是空清单而不是 null`() {
        val manifest = CoursewareManifestParser.parse("""{"version":3}""")!!
        assertEquals(3, manifest.version)
        assertTrue(manifest.packages.isEmpty())
    }
}
