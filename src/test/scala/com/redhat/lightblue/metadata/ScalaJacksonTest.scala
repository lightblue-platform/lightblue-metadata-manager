package com.redhat.lightblue.metadata

import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.Test

import collection.JavaConversions._
import collection.JavaConverters._
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature

class ScalaJacksonTest {

    @Test
    def sort {
        val mapper = new ObjectMapper() with ScalaObjectMapper
        mapper.registerModule(DefaultScalaModule)
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        val json = """{"name":"Marek", "age":29, "foo": {"bar": "a", "zoo": 13}}""";

        // jackson-module-scala handles conversion from json to scala map well
        val map = mapper.readValue[Map[String, Any]](json)

        // TODO: however, conversion from scala map to json does not work (does not recognize the map properly)
        // have to convert scala map to java map first
        println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(JavaUtil.toJava(map)))
    }

}