package com.redhat.lightblue.metadata

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.redhat.lightblue.metadata.MetadataManager.entityNameFilter
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

class EntityNameFilterTest {

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