package com.redhat.lightblue.metadata

import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Test
import org.junit.Assert._

import collection.JavaConversions._
import collection.JavaConverters._
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.JsonNode

class ScalaJacksonTest {

    val json = """{"schema": {"_id": "id", "name":"Marek", "age":29, "foo": {"bar": "a", "zoo": 13, "nestedArray": ["c", "b", "a"]}, "array": ["c", "b", "a"], "arrayOfObjects": [{"foo": "bar"}, {"bar":"foo"}]}, "entityInfo": {"_id": "id"}}""";

    @Test
    def jsonSort {
        val e = new Entity(json)

        println(e.text)

        val expected =
"""{
    "entityInfo" : { },
    "schema" : {
        "age" : 29,
        "array" : [
            "c",
            "b",
            "a"
        ],
        "arrayOfObjects" : [
            {
                "foo" : "bar"
            },
            {
                "bar" : "foo"
            }
        ],
        "foo" : {
            "bar" : "a",
            "nestedArray" : [
                "c",
                "b",
                "a"
            ],
            "zoo" : 13
        },
        "name" : "Marek"
    }
}"""

        assertEquals(expected, e.text)

    }
}