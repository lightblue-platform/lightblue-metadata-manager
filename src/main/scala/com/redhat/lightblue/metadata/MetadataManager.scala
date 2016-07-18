package com.redhat.lightblue.metadata

import java.nio.file.Files
import java.nio.file.Paths

import scala.collection.JavaConversions.asScalaIterator
import scala.sys.process.stringToProcess

import org.slf4j.LoggerFactory

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.redhat.lightblue.client.LightblueClient
import com.redhat.lightblue.client.LightblueException
import com.redhat.lightblue.client.request.metadata.MetadataCreateNewEntityRequest
import com.redhat.lightblue.client.request.metadata.MetadataGetEntityMetadataRequest
import com.redhat.lightblue.client.request.metadata.MetadataGetEntityNamesRequest
import com.redhat.lightblue.client.response.LightblueMetadataResponse
import com.redhat.lightblue.metadata.MetadataManager._
import com.redhat.lightblue.client.request.metadata.MetadataCreateSchemaRequest

/*
 * Represents entity versions, as returned by /rest/metadata/{entity}/
 */
@JsonIgnoreProperties(ignoreUnknown = true)
case class EntityVersion(version: String, changelog: String, status: String, defaultVersion: Boolean)

/**
 * Represents entity metadata.
 *
 */
class Entity(rootNode: ObjectNode) {

    def this(jsonStr: String) = this(parseJson(jsonStr))

    rootNode.get("schema").asInstanceOf[ObjectNode].remove("_id")
    rootNode.get("entityInfo").asInstanceOf[ObjectNode].remove("_id")

    def json: JsonNode = rootNode

    def entityInfoJson: JsonNode = rootNode.get("entityInfo")

    def schemaJson: JsonNode = rootNode.get("schema")

    def name: String = entityInfoJson.get("name").asText()

    def version: String = schemaJson.get("version").get("value").asText()

    def text: String = toSortedString(json)

    def entityInfoText = toSortedString(entityInfoJson)

    def schemaText = toSortedString(schemaJson)

    // set all arrays in entityInfo.access to ["anyone"]
    def accessAnyone: Entity = {
        val copy = rootNode.deepCopy()
        val accessNode = copy.get("schema").get("access").asInstanceOf[ObjectNode]
        val accessArray = mapper.createArrayNode().add("anyone")

        accessNode.fieldNames().foreach(accessNode.set(_, accessArray))

        new Entity(copy)
    }

    // replace node specified by path with the same node from another entity
    def replacePath(path: String, replaceFrom: Entity): Entity = {
        logger.debug(s"""Replacing $path""")
        val copy = rootNode.deepCopy()

        val nodeFromPath = getPath(replaceFrom.json, path)
        logger.debug(s"""Replacing with $nodeFromPath""")

        putPath(copy, getPath(replaceFrom.json, path), path)
        new Entity(copy)
    }

    override def toString = s"""$name|$version"""
}

object MetadataScope extends Enumeration {
    val SCHEMA, ENTITYINFO, BOTH = Value
}

class MetadataManagerException(message: String, t: Throwable) extends Exception {
    def this(message: String) = this(message, null)
}

/**
 * Metadata manipulation logic. Talks to Lightblue using lightblue-client.
 *
 */
class MetadataManager(val client: LightblueClient) {

    val logger = LoggerFactory.getLogger(MetadataManager.getClass);

    def listEntities: List[String] = {

        val r = new MetadataGetEntityNamesRequest()

        val json = client.metadata(r).getJson

        json.get("entities").asInstanceOf[ArrayNode].iterator.toList.map { x => x.asText() }.sorted

    }

    def getEntityVersion(entityName: String, entityVersionFilter: List[EntityVersion] => Option[EntityVersion]): Option[EntityVersion] = {

        val getE = new MetadataGetEntityMetadataRequest(entityName, null)

        val versionsJson = client.metadata(getE).getJson.asInstanceOf[ArrayNode].iterator().toList

        entityVersionFilter(versionsJson.map(x => mapper.treeToValue(x, classOf[EntityVersion])))

    }

    private def getEntity(entityName: String, entityVersion: EntityVersion): Entity = getEntity(entityName, entityVersion.version)

    private def getEntity(entityName: String, entityVersion: String): Entity = {

        val getE = new MetadataGetEntityMetadataRequest(entityName, entityVersion)

        val response = client.metadata(getE)

        verifyResponse(response)

        val json = response.getJson

        new Entity(json.asInstanceOf[ObjectNode])
    }

    def getEntity(entityName: String, entityVersionFilter: List[EntityVersion] => Option[EntityVersion]): Option[Entity] = {

        getEntityVersion(entityName, entityVersionFilter) match {
            case Some(v) => Some(getEntity(entityName, v))
            case None    => None
        }

    }

    def getEntities(entityNamePattern: String, entityVersionFilter: List[EntityVersion] => Option[EntityVersion]): List[Entity] = {

        implicit val pattern = entityNamePattern

        listEntities.filter(entityNameFilter).map { entityName =>

            getEntityVersion(entityName, entityVersionFilter) match {
                case Some(v) => Some(getEntity(entityName, v))
                case None => {
                    logger.warn(s"""Cound not find version for $entityName""")
                    None
                }
            }
        }
            .filter(_.isDefined) // remove Nones
            .map(_.get) // extract values from Somes

    }

    // TODO: generate diff in java rather than relay on system diff tool
    // Tried google-diff-match-patch and java-diff-utils, but was not able to produce usable results
    // There are solutions using json patch (RFC 6902), but this is not very human readable
    def diffEntity(entity: Entity) {
        val remoteEntity = getEntity(entity.name, entityVersionNewest) match {
            case Some(x) => x
            case None => throw new Exception(s"""${entity.name} does not exist in this environment""")
        }

        // save remote metadata locally
        val remoteEntityFileName = s""".${remoteEntity.name}-remote.json"""
        Files.write(Paths.get(remoteEntityFileName), remoteEntity.text.getBytes)

        // save local metadata to take --ignore operations into account
        val localEntityFileName = s""".${remoteEntity.name}-local.json"""
        Files.write(Paths.get(localEntityFileName), entity.text.getBytes)

        // prints diff to stdin using the system diff command
        s"""diff -u $remoteEntityFileName $localEntityFileName""" !
    }


    def putEntity(entity: Entity, scope: MetadataScope.Value) {
        logger.debug(s"""Uploading $entity scope=$scope""")

        val r = scope match {
            case MetadataScope.SCHEMA => {
                val r = new MetadataCreateSchemaRequest(entity.name, entity.version)
                r.setBodyJson(entity.schemaText)
                r
            }
            case MetadataScope.ENTITYINFO => {
                val r = new MetadataCreateNewEntityRequest(entity.name, null)
                r.setBodyJson(entity.entityInfoText)
                r
            }
            case MetadataScope.BOTH => {
                val r = new MetadataCreateNewEntityRequest(entity.name, entity.version)
                r.setBodyJson(entity.text)
                r
            }
        }

        val response = client.metadata(r)

        verifyResponse(response)

        logger.info(s"""Pushed $entity""")
    }

    private def verifyResponse(response: LightblueMetadataResponse) {
        // TODO: https://github.com/lightblue-platform/lightblue-core/issues/672
        if (response.getJson != null && response.getJson.get("objectType") != null && response.getJson.get("objectType").asText() == "error") {
            throw new LightblueException(response.getText)
        }
    }

}

/**
 * MetadataManager's companion object. Provides utility methods.
 *
 */
object MetadataManager {

    val logger = LoggerFactory.getLogger(MetadataManager.getClass)

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

    // entity version selectors
    def entityVersionDefault = (l: List[EntityVersion]) => l collectFirst { case v if v.defaultVersion => v }
    def entityVersionNewest = (l: List[EntityVersion]) => Some(l.sortWith(versionCompare(_, _) > 0)(0))
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
        if (pattern.startsWith("/") && pattern.endsWith("/")) {
            // regex
            val _pattern = pattern.substring(1, pattern.length() - 1)
            logger.debug(s"""Matching entity $entity against '${_pattern}' pattern""")
            entity.matches(_pattern)
        } else {
            // equals
            entity == pattern
        }
    }

    /**
     * TODO: I don't see a way to key-sort JsonNode
     * instead doing following conversions: JsonNode -> String -> Map -> String sorted by keys
     */
    def toSortedString(json: JsonNode): String = {
        val jsonStr = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json)

        // jackson-module-scala handles conversion from json to scala map well
        val map = mapper.readValue[Map[String, Any]](jsonStr)

        // TODO: however, conversion from scala map to json does not work (does not recognize the map properly)
        // have to convert scala map to java map first
        mapper.writer(prettyPrinter).writeValueAsString(JavaUtil.toJava(map))
    }

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

}