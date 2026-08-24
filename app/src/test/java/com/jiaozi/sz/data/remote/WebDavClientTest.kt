package com.jiaozi.sz.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

open class WebDavClientTest {

    @Test
    fun buildUrl_joinsBaseDirAndName() {
        assertEquals(
            "https://dav.example.com/jiaozi/jiaozi_sync.json",
            WebDavClient.buildUrl("https://dav.example.com", "jiaozi", "jiaozi_sync.json")
        )
    }

    @Test
    fun buildUrl_stripsTrailingSlashOnBase() {
        assertEquals(
            "https://dav.example.com/jiaozi/jiaozi_sync.json",
            WebDavClient.buildUrl("https://dav.example.com/", "jiaozi", "jiaozi_sync.json")
        )
    }

    @Test
    fun buildUrl_stripsLeadingAndTrailingSlashOnDir() {
        assertEquals(
            "https://dav.example.com/a/b/jiaozi_sync.json",
            WebDavClient.buildUrl("https://dav.example.com", "/a/b/", "jiaozi_sync.json")
        )
    }

    @Test
    fun buildUrl_emptyDirUsesRoot() {
        assertEquals(
            "https://dav.example.com/jiaozi_sync.json",
            WebDavClient.buildUrl("https://dav.example.com", "", "jiaozi_sync.json")
        )
    }

    @Test
    fun dirUrl_endsWithSlash() {
        assertEquals(
            "https://dav.example.com/jiaozi/",
            WebDavClient.dirUrl("https://dav.example.com", "jiaozi")
        )
    }
}
