package com.redhat.lightblue.metadata

import org.junit.Assert._
import org.junit.Test

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.redhat.lightblue.metadata.MetadataManager.entityNameFilter
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

class EntityTest {

    @Test
    def nameFilterTest {
        implicit val pattern = "/^user.*/"

        assertTrue(entityNameFilter("user"))
        assertTrue(entityNameFilter("userCredential"))
        assertFalse(entityNameFilter("someUser"))
    }

    @Test
    def nameFilterTest2 {
        implicit val pattern = "/^(user|legalEntity).*/"

        assertTrue(entityNameFilter("user"))
        assertTrue(entityNameFilter("userCredential"))
        assertFalse(entityNameFilter("someUser"))
        assertTrue(entityNameFilter("legalEntity"))
    }

    @Test
    def accessAnyoneTest {

        val json = """{
"schema": {
"access" : {
            "delete" : [
                "lb-user-delete"
            ],
            "find" : [
                "lb-user-find"
            ],
            "insert" : [
                "lb-user-insert"
            ],
            "update" : [
                "lb-user-update"
            ]
        }
    },
 "entityInfo": {}
}"""

        val e = new Entity(json)

        assertEquals("""{
    "entityInfo" : { },
    "schema" : {
        "access" : {
            "delete" : [
                "anyone"
            ],
            "find" : [
                "anyone"
            ],
            "insert" : [
                "anyone"
            ],
            "update" : [
                "anyone"
            ]
        }
    }
}""", e.accessAnyone.text)


    }



}