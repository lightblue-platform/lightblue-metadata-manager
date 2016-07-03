package com.redhat.lightblue.metadata

import org.junit.Test
import com.redhat.lightblue.metadata.MetadataManager._
import org.junit.Assert._

class EntityNameFilter {

    @Test
    def test {
        implicit val pattern = "/^user.*/"

        assertTrue(entityNameFilter("user"))
        assertTrue(entityNameFilter("userCredential"))
        assertFalse(entityNameFilter("someUser"))
    }

    @Test
    def test2 {
        implicit val pattern = "/^(user|legalEntity).*/"

        assertTrue(entityNameFilter("user"))
        assertTrue(entityNameFilter("userCredential"))
        assertFalse(entityNameFilter("someUser"))
        assertTrue(entityNameFilter("legalEntity"))
    }

}