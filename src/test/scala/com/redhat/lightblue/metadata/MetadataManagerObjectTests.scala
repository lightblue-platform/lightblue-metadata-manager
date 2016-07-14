package com.redhat.lightblue.metadata

import org.junit.Test
import com.redhat.lightblue.metadata.MetadataManager._
import org.junit.Assert._

class MetadataManagerObjectTests {

    @Test
    def testVersionRelease() {
        assertEquals(-1, versionCompare("1.2.3", "1.2.4"))
        assertEquals(-1, versionCompare("1.2.3", "1.3.3"))
        assertEquals(0, versionCompare("1.2.3", "1.2.3"))

    }

    @Test
    def testVersionSnapshot() {
        assertEquals(1, versionCompare("1.2.3", "1.2.3-SNAPSHOT"))
        assertEquals(-1, versionCompare("1.2.3", "1.2.4-SNAPSHOT"))
        assertEquals(0, versionCompare("1.2.3-SNAPSHOT", "1.2.3-SNAPSHOT"))
    }

    val json = """{"schema": {"_id": "id", "name":"Marek", "age":29, "foo": {"bar": "a", "zoo": 13, "nestedArray": ["c", "b", "a"]}, "array": ["c", "b", "a"], "arrayOfObjects": [{"foo": "bar"}, {"bar":"foo"}]}, "entityInfo": {"_id": "id"}}""";

    @Test
    def getPathTest() {
         assertEquals("Marek", getPath(parseJson(json), "schema.name").asText())
         assertEquals("""["c","b","a"]""", getPath(parseJson(json), "schema.foo.nestedArray").toString())
    }

    @Test
    def putPathTest() {

         val node = parseJson(json)

         val emptyArray = mapper.createArrayNode()
         putPath(node, emptyArray, "schema.foo.nestedArray")

         assertEquals("""[]""", getPath(node, "schema.foo.nestedArray").toString())
    }

}