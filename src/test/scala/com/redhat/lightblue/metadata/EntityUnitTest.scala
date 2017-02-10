package com.redhat.lightblue.metadata

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.redhat.lightblue.metadata.Entity._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.ArrayNode
import scala.io.Source
import junit.framework.Assert

/**
 * Unit tests in ScalaTest using FlatSpec style.
 */
@RunWith(classOf[JUnitRunner])
class EntityUnitTest extends FlatSpec with Matchers {

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

    "$all/ entity name filter" should "match all entities" in {

        implicit val pattern = "$all"

        entityNameFilter("user") should be (true)
        entityNameFilter("userCredential") should be (true)
        entityNameFilter("someUser") should be (true)
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

    val entityVersionStr = """{
	"schema": {
		"version": {
		  "value": "0.0.1-SNAPSHOT"
		},
		"field": {
			"foo": "bar"
		}
	},
	"entityInfo": {
		"defaultVersion": "0.0.1-SNAPSHOT"
	}
}"""

    "entity.version" should "set schema and default version" in {
        val e1 = new Entity(entityVersionStr)
        val e2 = e1.version("0.0.1")

        e2.schemaJson.get("version").get("value").asText should be ("0.0.1")
        e2.entityInfoJson.get("defaultVersion").asText should be ("0.0.1")
        e2.schemaJson.has("field") should be (true)
        e1.schemaJson.get("version").get("value").asText should be ("0.0.1-SNAPSHOT")
        e1.entityInfoJson.get("defaultVersion").asText should be ("0.0.1-SNAPSHOT")
    }

    "entity.changelog" should "set changelog" in {
        val e1 = new Entity(entityVersionStr)
        val e2 = e1.changelog("foobar")

        e2.schemaJson.get("version").get("changelog").asText should be ("foobar")
        e2.schemaJson.has("field") should be (true)
        e1.schemaJson.get("version").has("changelog") should be (false)
    }

    val entity1 = """{
	"schema": {
		"version": {
			"value": "0.0.1-SNAPSHOT"
		},
		"arrayInt": [1, 2, 3, 4],
		"arrayObj": [{
		    "id": 10,
			"a": "b",
			"c": "d"
		}, {
		    "id": 20,
			"e": "f",
			"g": "h"
		}],
		"field": {
			"foo": "bar"
		}
	},
	"entityInfo": {
		"defaultVersion": "0.0.1-SNAPSHOT",
		"name": "entity"
	}
}"""

    val entity2 = """{
	"schema": {
		"version": {
			"value": "0.0.1-SNAPSHOT"
		},
		"arrayInt": [1, 2, 3, 4, 5, 6],
		"arrayObj": [{
		    "id": 10,
			"a": "b",
			"c": "d"
		},
		{
		    "id": 15,
			"1": "2",
			"3": "4"
		},
		{
		    "id": 20,
			"e": "f",
			"g": "h"
		}],
		"field": {
			"foo": "bar"
		}
	},
	"entityInfo": {
		"defaultVersion": "0.0.1-SNAPSHOT",
		"name": "entity"
	}
}"""

    val entity3 = """{
	"schema": {
		"version": {
			"value": "0.0.1-SNAPSHOT"
		},
		"arrayInt": [1, 2, 3, 4],
		"arrayObj": [{
		    "id": 10,
			"a": "b",
			"c": "d"
		}, {
		    "id": 20,
			"e": "f",
			"g": "h"
		},
		{
		    "id": 30,
			"new": "val",
			"val": "new"
		}],
		"field": {
			"foo": "bar"
		}
	},
	"entityInfo": {
		"defaultVersion": "0.0.1-SNAPSHOT",
		"name": "entity",
		"changelog": "'Single quote', \"double quote\""
	}
}"""

val jsonDiff = """[ {
  "op" : "replace",
  "path" : "/schema/version/value",
  "value" : "0.0.1"
}, {
  "op" : "replace",
  "path" : "/entityInfo/defaultVersion",
  "value" : "0.0.1"
} ]"""

    "entity.diff" should "create a diff" in {
        val e1 = new Entity(entity1)
        val e2 = e1.version("0.0.1")

        val diff = e1 diff e2

        // TODO: use implicit
        toFormatedString(diff) should be (jsonDiff)
    }

    it should "create an empty array diff for equal entities" in {
        val e1 = new Entity(entity1)
        val e2 = new Entity(e1)

        val diff = e1 diff e2

        toFormatedString(diff) should be ("[ ]")
    }

    it should "not rewrite entire array if the first element is removed" in {
        val e2 = new Entity(entity2)
        val e22 = new Entity(e2)

        e22.json.get("schema").get("arrayObj").asInstanceOf[ArrayNode].remove(0)

        val diff = e2 diff e22

        diff.asInstanceOf[ArrayNode].size() should be (1)
    }

    it should "not rewrite entire array if the first element is changed" in {
        val e2 = new Entity(entity2)
        val e22 = new Entity(e2)

        e22.json.get("schema").get("arrayObj").asInstanceOf[ArrayNode].get(0).asInstanceOf[ObjectNode].remove("a")

        val diff = e2 diff e22

        diff.asInstanceOf[ArrayNode].size() should be (1)
    }

    "entity.apply(JsonPatch)" should "apply a replace patch to source" in {

        val e1 = new Entity(entity1)
        val e2 = e1.version("0.0.1")

        val patch = mapper.readTree(jsonDiff).asInstanceOf[ArrayNode]

        val patched = e1.apply(patch)

        patched should be (e2)
    }

    it should "apply a replace patch to a different source" in {

        val e1 = new Entity(entity2)
        val e2 = e1.version("0.0.1")

        val patch = mapper.readTree(jsonDiff).asInstanceOf[ArrayNode]

        val patched = e1.apply(patch)

        patched should be (e2)
    }

    it should "apply an add patch to source" in {

        val e1 = new Entity(entity1)
        val e2 = new Entity(entity2)

        val patch = e1 diff e2

        val patched = e1.apply(patch)

        patched should be (e2)
    }

    implicit class TestEntityHelper(e: Entity) {
        def arrayObj = e.json.get("schema").get("arrayObj").asInstanceOf[ArrayNode]
        def arrayInt = e.json.get("schema").get("arrayInt").asInstanceOf[ArrayNode]
    }

    it should "be able to merge a sequance of patches" in {

        val e1 = new Entity(entity1)
        val e2 = new Entity(entity2)
        val e3 = new Entity(entity3)

        val patch12 = e1 diff e2
        val patch23 = e1 diff e3

        val patched = e1.apply(patch12).apply(patch23)

        patched.arrayInt.size() should be (6)
        patched.arrayObj.size() should be (4)

        patched.arrayObj.get(0).get("id").asInt() should be (10)
        patched.arrayObj.get(1).get("id").asInt() should be (15)
        // changed the order, but inserted all elements
        patched.arrayObj.get(2).get("id").asInt() should be (30)
        patched.arrayObj.get(3).get("id").asInt() should be (20)
    }

    // json patch operates on array indexes, making modify and remove operations unpredictable
    // to make sure your patch delivers expected changes regardless of target entity state,
    // prepare a javascript patch:

    "entity.apply(javascript)" should "allow to remove array element by arbitrary criteria" in {

        val e3 = new Entity(entity3)

        val patched = e3 apply """
          entity.schema.arrayObj.remove(function(el, i) {
              return el.id > 10;
          });
        """

        patched.arrayObj.size() should be (1)
        patched.arrayObj.get(0).get("id").asInt() should be (10)
    }

    it should "allow to modify array element by arbitrary criteria" in {

        val e3 = new Entity(entity3)

        val patched = e3 apply """
          entity.schema.arrayObj.modify(function(el) {
              return el.id > 10;
          },
          function(el) {
              el.hasIdGreaterThan10 = true;
              return el;
          });
        """

        patched.arrayObj.size() should be (3)
        patched.arrayObj.get(0).hasNonNull("hasIdGreaterThan10") should be (false)
        patched.arrayObj.get(1).get("hasIdGreaterThan10").asBoolean() should be (true)
        patched.arrayObj.get(2).get("hasIdGreaterThan10").asBoolean() should be (true)
    }

    it should "support multi-line strings using `<multiline string>` notation from template literals" in {
        val e3 = new Entity(entity3)

        val patched = e3 apply """
          entity.schema.arrayObj.push(JSON.parse(`
            {
                "field": "value",
                "field1": "value1 \"double quotes\", 'single quotes'"
            }
	      `));
		"""

        patched.arrayObj.size() should be(4)
        patched.arrayObj.get(3).get("field").asText() should be ("value")
    }


}