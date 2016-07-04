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

    val json = """{"name":"Marek", "age":29, "foo": {"bar": "a", "zoo": 13, "nestedArray": ["c", "b", "a"]}, "array": ["c", "b", "a"]}""";

    @Test
    def jsonSort {
        val mapper = new ObjectMapper() with ScalaObjectMapper
        mapper.registerModule(DefaultScalaModule)
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        // jackson-module-scala handles conversion from json to scala map well
        val map = mapper.readValue[Map[Any, Any]](json)

        // TODO: however, conversion from scala map to json does not work (does not recognize the map properly)
        // have to convert scala map to java map first
        val result = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(JavaUtil.toJava(map))

        val expected =
"""{
  "age" : 29,
  "array" : [ "a", "b", "c" ],
  "foo" : {
    "bar" : "a",
    "nestedArray" : [ "a", "b", "c" ],
    "zoo" : 13
  },
  "name" : "Marek"
}"""

        assertEquals(expected, result)

    }
}