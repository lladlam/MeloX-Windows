package melox.network

import kotlin.test.*

class NeteaseResponseJsonTest {
    @Test fun duplicatedArtistFieldsMatchAndroidLastValueWinsWithoutLosingPrecisionOrNestedContent() {
        val json = parseNeteaseResponseJson("""{"code":200,"artist":{"id":9007199254740993,"followed":false,"name":"a\\b\"c","followed":true},"songs":[{"al":{"id":1,"id":2}}]}""")
        assertTrue(json.getJSONObject("artist").getBoolean("followed"))
        assertEquals(9007199254740993L, json.getJSONObject("artist").getLong("id"))
        assertEquals("a\\b\"c", json.getJSONObject("artist").getString("name"))
        assertEquals(2, json.getJSONArray("songs").getJSONObject(0).getJSONObject("al").getInt("id"))
    }
    @Test fun malformedProviderPayloadIsStillRejected() {
        assertFails { parseNeteaseResponseJson("""{"artist": [1,}""") }
        assertFails { parseNeteaseResponseJson("""[1,2,3]""") }
    }
}
