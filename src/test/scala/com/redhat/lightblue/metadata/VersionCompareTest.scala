package com.redhat.lightblue.metadata

import org.junit.Test
import com.redhat.lightblue.metadata.MetadataManager._
import org.junit.Assert._

class VersionCompareTest {

    @Test
    def testRelease() {
        assertEquals(-1, versionCompare("1.2.3", "1.2.4"))
        assertEquals(-1, versionCompare("1.2.3", "1.3.3"))
        assertEquals(0, versionCompare("1.2.3", "1.2.3"))

    }

    @Test
    def testSnapshot() {
        assertEquals(1, versionCompare("1.2.3", "1.2.3-SNAPSHOT"))
        assertEquals(-1, versionCompare("1.2.3", "1.2.4-SNAPSHOT"))
        assertEquals(0, versionCompare("1.2.3-SNAPSHOT", "1.2.3-SNAPSHOT"))
    }

}