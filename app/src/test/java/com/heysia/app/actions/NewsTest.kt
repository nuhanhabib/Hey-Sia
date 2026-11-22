package com.heysia.app.actions

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsTest {
    @Test fun parsesRssTitles() {
        val xml = """
            <rss><channel><title>Google News</title>
              <item><title>Rain expected across the country - The Daily Star</title></item>
              <item><title><![CDATA[Tigers win series 2&amp;1]]></title></item>
              <item><title>Markets &quot;steady&quot; today - BBC News</title></item>
            </channel></rss>
        """.trimIndent()
        assertEquals(
            listOf("Rain expected across the country", "Tigers win series 2&1", "Markets \"steady\" today"),
            News.parseTitles(xml)
        )
    }
}
