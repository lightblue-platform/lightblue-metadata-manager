package com.redhat.lightblue.metadata

import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Test

class JsonSort {
    @Test
    def sort {
        val mapper = new ObjectMapper() with ScalaObjectMapper
        mapper.registerModule(DefaultScalaModule)
        val json = """{"name":"mkyong", "age":29, "foo": {"bar": "a", "zoo": 13}}""";

        val map = mapper.readValue[Map[Object, Object]](json)

        println(map)

        // {"empty":false,"traversableAgain":true} <- why!?
        println(mapper.writeValueAsString(map))

    }
}