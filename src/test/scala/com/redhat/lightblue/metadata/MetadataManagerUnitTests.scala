package com.redhat.lightblue.metadata

import org.scalatest.FlatSpec

import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.redhat.lightblue.metadata.MetadataManager.entityNameFilter
import org.scalatest.Matchers
import com.redhat.lightblue.metadata.MetadataManager._

/**
 * Unit tests in ScalaTest using FlatSpec style.
 */
class MetadataManagerUnitTests extends FlatSpec with Matchers {

    "/^user.*/ entity name filter" should "match only entities starting with user" in {
        implicit val pattern = "/^user.*/"

        entityNameFilter("user") should be (true)
        entityNameFilter("userCredential") should be (true)
        entityNameFilter("someUser") should be (false)
    }

    "/^(user|legalEntity).*/ entity name filter" should "match entities starting with user or legalEntity" in {

        implicit val pattern = "/^(user|legalEntity).*/"

        entityNameFilter("user") should be (true)
        entityNameFilter("userCredential") should be (true)
        entityNameFilter("someUser") should be (false)
        entityNameFilter("legalEntity") should be (true)
    }

    "versionCompare" should "compare X.X.X versions correctly" in {
        versionCompare("1.2.3", "1.2.4") should be < 0
        versionCompare("1.2.3", "1.3.3") should be < 0
        versionCompare("1.2.3", "1.2.3") should be (0)
        versionCompare("2.2.3", "1.2.3") should be > 0
    }

    it should "compare X.X.X-SNAPSHOT versions correctly" in {
        versionCompare("1.2.3", "1.2.3-SNAPSHOT") should be > 0
        versionCompare("1.2.3", "1.2.4-SNAPSHOT") should be < 0
        versionCompare("1.2.3-SNAPSHOT", "1.2.3-SNAPSHOT") should be (0)
    }

    "schema.access" should "be changed to anyone with entity.accessAnyone" in {

            val before = """{
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

             val expected = """{
    "schema": {
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
            },
     "entityInfo": {}
    }"""

            // TODO: compare entities
            new Entity(before).accessAnyone.text should be (new Entity(expected).text)
    }


    val jsonStr = """{"schema": {"_id": "id", "name":"Marek", "age":29, "foo": {"bar": "a", "zoo": 13, "nestedArray": ["c", "b", "a"]}, "array": ["c", "b", "a"], "arrayOfObjects": [{"foo": "bar"}, {"bar":"foo"}]}, "entityInfo": {"_id": "id"}}""";

    s"""getPath($jsonStr)""" should "return Marek for schema.name path" in {
        getPath(parseJson(jsonStr), "schema.name").asText() should be ("Marek")
    }

    it should """return ["c","b","a"] for schema.foo.nestedArray path""" in {
        getPath(parseJson(jsonStr), "schema.foo.nestedArray").toString() should be ("""["c","b","a"]""")
    }

    it should "throw MetadataManagerExcepion if path does not exist" in {
        intercept[MetadataManagerException] {
            getPath(parseJson(jsonStr), "path.dont.exist")
        }
    }

    s"""putPath""" should "replace node under path specified" in {

         val node = parseJson(jsonStr)
         val emptyArray = mapper.createArrayNode()
         putPath(node, emptyArray, "schema.foo.nestedArray")

         getPath(node, "schema.foo.nestedArray").toString() should be ("[]")
    }

    it should "throw MetadataManagerExcepion if path does not exist" in {

         val node = parseJson(jsonStr)
         val emptyArray = mapper.createArrayNode()

         intercept[MetadataManagerException] {
             putPath(node, emptyArray, "path.dont.exist")
         }
    }

}