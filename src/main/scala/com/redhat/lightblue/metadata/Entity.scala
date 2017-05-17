package com.redhat.lightblue.metadata

import scala.collection.JavaConversions.asScalaIterator
import scala.io.Source

import org.slf4j.LoggerFactory

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.flipkart.zjsonpatch.JsonDiff
import com.flipkart.zjsonpatch.JsonPatch
import com.redhat.lightblue.metadata.Entity.getPath
import com.redhat.lightblue.metadata.Entity.logger
import com.redhat.lightblue.metadata.Entity.mapper
import com.redhat.lightblue.metadata.Entity.parseJson
import com.redhat.lightblue.metadata.Entity.processWithJavascript
import com.redhat.lightblue.metadata.Entity.putPath
import com.redhat.lightblue.metadata.Entity.toSortedFormatedString
import com.redhat.lightblue.metadata.util.JavaUtil

import javax.script.ScriptEngineManager

/*
 * Represents entity versions, as returned by /rest/metadata/{entity}/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
case class EntityVersion(version: String, changelog: String, status: String, defaultVersion: Boolean)

/**
 * Represents entity metadata. Is immutable - will return a copy on every change.
 *
 */
class Entity(rootNode: ObjectNode) {

    implicit val implicitRootNode = rootNode

    def this(jsonStr: String) = this(parseJson(jsonStr))

    def this(e: Entity) = this(e.json.asInstanceOf[ObjectNode].deepCopy())

    rootNode.get("schema").asInstanceOf[ObjectNode].remove("_id")
    rootNode.get("entityInfo").asInstanceOf[ObjectNode].remove("_id")

    def json: JsonNode = rootNode

    def entityInfoJson: JsonNode = rootNode.get("entityInfo")

    def schemaJson: JsonNode = rootNode.get("schema")

    def name: String = entityInfoJson.get("name").asText()

    def version: String = schemaJson.get("version").get("value").asText()

    def text: String = toSortedFormatedString(json)

    def entityInfoText = toSortedFormatedString(entityInfoJson)

    def schemaText = toSortedFormatedString(schemaJson)

    // set all arrays in entityInfo.access to ["anyone"]
    def accessAnyone(): Entity = {

        modifyCopy {
            (rootNode) => {
                val accessNode = rootNode.get("schema").get("access").asInstanceOf[ObjectNode]
                val accessArray = mapper.createArrayNode().add("anyone")

                accessNode.fieldNames().foreach(accessNode.set(_, accessArray))
            }
        }
    }

    // replace node specified by path with the same node from another entity
    def replacePath(path: String, replaceFrom: Entity): Entity = {
        logger.debug(s"""Replacing $path""")

        modifyCopy {
            (rootNode) => {
                val nodeFromPath = getPath(replaceFrom.json, path)
                logger.debug(s"""Replacing with $nodeFromPath""")

                putPath(rootNode, getPath(replaceFrom.json, path), path)
            }
        }
    }

    def changelog(message: String): Entity = {
        logger.debug(s"""Setting changelog to $message""")

        modifyCopy {
            (rootNode) => {
                putPath(rootNode, TextNode.valueOf(message), "schema.version.changelog")
            }
        }
    }

    def changelog = getPath(rootNode, "schema.version.changelog").asText()

    def version(version: String): Entity = {
        logger.debug(s"""Setting version to $version""")

        modifyCopy {
            (rootNode) => {
                putPath(rootNode, TextNode.valueOf(version), "schema.version.value")

                if (rootNode.get("entityInfo").has("defaultVersion")) {
                    putPath(rootNode, TextNode.valueOf(version), "entityInfo.defaultVersion")
                }
            }
        }
    }

    def defaultVersion = getPath(rootNode, "entityInfo.defaultVersion").asText()

    /**
     * Create a diff (RFC 6902 JSON patch).
     *
     */
    def diff(other: Entity) = JsonDiff.asJson(json, other.json)

    /**
     * Apply RFC 6902 JSON patch.
     *
     */
    def apply(patch: JsonNode): Entity = {
        logger.debug(s"""Patching with $patch""")

        new Entity(JsonPatch.apply(patch, rootNode).asInstanceOf[ObjectNode])
    }

    /**
     * Apply javascript logic.
     *
     */
    def apply(javascriptCode: String): Entity = {
       logger.debug(s"""Processing with $javascriptCode""")

       new Entity(processWithJavascript(rootNode, javascriptCode))
    }

    // control structure to operate on a ObjectNode copy and return new Entity
    private def modifyCopy(modifyLogic: (ObjectNode) => Unit)(implicit rootNode: ObjectNode): Entity = {
        val copy = rootNode.deepCopy()
        modifyLogic(copy)
        new Entity(copy)
    }

    override def toString = s"""$name|$version"""

    override def equals(e: Any): Boolean = {

        e match {
            case that: Entity => this.json.equals(that.json)
            case _ => false
        }

    }
}

object MetadataScope extends Enumeration {
    val SCHEMA, ENTITYINFO, BOTH = Value
}

class MetadataManagerException(message: String, t: Throwable) extends Exception {
    def this(message: String) = this(message, null)
}

/**
 * Entity's companion object. Provides utility methods.
 *
 */
object Entity {

    val logger = LoggerFactory.getLogger(Entity.getClass)

    // configure json mapper for scala
    val mapper = new ObjectMapper() with ScalaObjectMapper
    mapper.registerModule(DefaultScalaModule)
    // key sorting for maps
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    // configure json pretty printer
    val prettyPrinter = new DefaultPrettyPrinter();
    val indenter = new DefaultIndenter("    ", DefaultIndenter.SYS_LF)
    prettyPrinter.indentObjectsWith(indenter);
    prettyPrinter.indentArraysWith(indenter);

    // javascript engine
    val util_js = Source.fromInputStream(getClass.getResourceAsStream("/util.js")).mkString
    val engine = new ScriptEngineManager().getEngineByMimeType("text/javascript")
    engine.eval(util_js)

    // entity version selectors
    def entityVersionDefault = (l: List[EntityVersion]) => l collectFirst { case v if v.defaultVersion => v }
    def entityVersionNewest = (l: List[EntityVersion]) => if (l.isEmpty) None else Some(l.sortWith(versionCompare(_, _) > 0)(0))
    def entityVersionExplicit(version: String) = (l: List[EntityVersion]) => l collectFirst { case v if v.version == version => v }

    def versionCompare(v1: EntityVersion, v2: EntityVersion): Int = {
        versionCompare(v1.version, v2.version)
    }

    // solution from http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
    def versionCompare(v1: String, v2: String): Int = {

        val v1IsSnapshot = v1.endsWith("-SNAPSHOT")
        val v2IsSnapshot = v2.endsWith("-SNAPSHOT")

        val vals1 = v1.replace("-SNAPSHOT", "") split ("""\.""")
        val vals2 = v2.replace("-SNAPSHOT", "") split ("""\.""")

        var i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1(i).equals(vals2(i))) {
            i += 1
        }

        // compare first non-equal ordinal number
        if (i < vals1.length && i < vals2.length) {
            val diff = Integer.valueOf(vals1(i)).compareTo(Integer.valueOf(vals2(i)));
            return Integer.signum(diff);
        }

        // the strings are equal
        if (vals1.length == vals2.length) {
            // first one is a snapshot and the other isn't
            if (v1IsSnapshot && !v2IsSnapshot) {
                return -1
            }
            if (!v1IsSnapshot && v2IsSnapshot) {
                return 1
            }
        }

        // the strings are equal or one string is a substring of the other
        // e.g. "1.2.3" = "1.2.3" or "1.2.3" < "1.2.3.4"
        return Integer.signum(vals1.length - vals2.length);
    }

    def entityNameFilter(entity: String)(implicit pattern: String): Boolean = {

        val patternrRegex = "^/(.*?)/$".r

        pattern match {
            case "$all" => {
                logger.debug("Matching with $all")
                true
            }
            case patternrRegex(_pattern) => {
                logger.debug(s"""Matching entity $entity against '${_pattern}' pattern""")
                entity.matches(_pattern)
            }
            case _ => {
                logger.debug("Matching $entity == $pattern")
                entity == pattern
            }
        }
    }

    /**
     * TODO: I don't see a way to key-sort JsonNode
     * instead doing following conversions: JsonNode -> String -> Map -> String sorted by keys
     */
    def toSortedFormatedString(json: JsonNode): String = {
        val jsonStr = toFormatedString(json)

        // jackson-module-scala handles conversion from json to scala map well
        val map = mapper.readValue[Map[String, Any]](jsonStr)

        // TODO: however, conversion from scala map to json does not work (does not recognize the map properly)
        // have to convert scala map to java map first
        mapper.writer(prettyPrinter).writeValueAsString(JavaUtil.toJava(map))
    }

    def toFormatedString(json: JsonNode) = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)

    def toString(json: JsonNode) = mapper.writeValueAsString(json)

    def parseJson(json: String): ObjectNode = {
        mapper.readTree(json).asInstanceOf[ObjectNode]
    }

    /**
     * Where path is something like: 'entityInfo.indexes'
     */
    def getPath(node: JsonNode, path: String):JsonNode = {

        if (!path.contains(".")) {
            return node.get(path)
        }

        val pathArray = path.split("""\.""")

        val nextField = pathArray(0)
        val remainingPath = pathArray.drop(1).mkString(".")

        node.has(nextField) match {
            case true => getPath(node.get(nextField), remainingPath)
            case false => throw new MetadataManagerException(s"""nextField not found!""")
        }
    }

    def putPath(node: JsonNode, nodePut: JsonNode, path: String) {

        if (!path.contains(".")) {
            node.asInstanceOf[ObjectNode].set(path, nodePut)
            return
        }

        val pathArray = path.split("""\.""")

        val nextField = pathArray(0)
        val remainingPath = pathArray.drop(1).mkString(".")

        node.has(nextField) match {
            case true => putPath(node.get(nextField), nodePut, remainingPath)
            case false => throw new MetadataManagerException(s"""nextField not found!""")
        }
    }

    def processWithJavascript(node: JsonNode, javascriptCode: String) = {

        val jsonNodeStr = toString(node)

        val codeToRun = s"""
          var entity = $jsonNodeStr;

          $javascriptCode

          JSON.stringify(entity);
          """

        logger.debug(s"""executing js:\n$codeToRun""")

        val result = engine.eval(codeToRun)

        parseJson(result.toString())
    }

}