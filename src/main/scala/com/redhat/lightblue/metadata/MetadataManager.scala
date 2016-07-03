package com.redhat.lightblue.metadata

import scala.collection.JavaConversions._

import com.redhat.lightblue.client.LightblueClient
import com.redhat.lightblue.client.request.metadata.MetadataGetEntityNamesRequest
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.redhat.lightblue.client.request.metadata.MetadataGetEntityMetadataRequest
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.redhat.lightblue.metadata.MetadataManager._
import scala.util.matching.Regex
import org.slf4j.LoggerFactory

@JsonIgnoreProperties(ignoreUnknown = true)
case class EntityVersion(version: String, changelog: String, status: String, defaultVersion: Boolean)

class Entity(rootNode: ObjectNode) {
            
    rootNode.get("schema").asInstanceOf[ObjectNode].remove("_id")
    rootNode.get("entityInfo").asInstanceOf[ObjectNode].remove("_id")
    
    def json: JsonNode = rootNode
    
    def entityInfoJson: JsonNode = rootNode.get("entityInfo")
    
    def schemaJson: JsonNode = rootNode.get("schema")
    
    def name: String = entityInfoJson.get("name").asText()
    
    def version: String = schemaJson.get("version").get("value").asText()
    
    def text: String = mapper.writerWithDefaultPrettyPrinter.writeValueAsString(json)
    
    def entityInfoText = mapper.writerWithDefaultPrettyPrinter.writeValueAsString(entityInfoJson)
    
    def schemaText = mapper.writerWithDefaultPrettyPrinter.writeValueAsString(schemaJson)
}

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
        
        entityVersionFilter (versionsJson.map( x => mapper.treeToValue(x, classOf[EntityVersion])))
        
    }
    
    private def getEntity(entityName: String, entityVersion: EntityVersion): Entity = getEntity(entityName, entityVersion.version)

    private def getEntity(entityName: String, entityVersion: String): Entity = {

        val getE = new MetadataGetEntityMetadataRequest(entityName, entityVersion)

        val json = client.metadata(getE).getJson        
        
        new Entity(json.asInstanceOf[ObjectNode])       
    }
    
    def getEntity(entityName: String, entityVersionFilter: List[EntityVersion] => Option[EntityVersion]): Option[Entity] = {

        getEntityVersion(entityName, entityVersionFilter) match {
            case Some(v) => Some(getEntity(entityName, v))
            case None => None
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

}

object MetadataManager {
    
    val logger = LoggerFactory.getLogger(MetadataManager.getClass);

    val mapper = new ObjectMapper();
    mapper.registerModule(DefaultScalaModule)
    
    def entityVersionDefault = (l: List[EntityVersion]) => l collectFirst {case v if v.defaultVersion => v}
    def entityVersionNewest = (l: List[EntityVersion]) => Some(l.sortWith(versionCompare(_,_) > 0) (0))
    def entityVersionExplicit(version: String) = (l: List[EntityVersion]) => l collectFirst {case v if v.version == version => v}

    def versionCompare(v1: EntityVersion, v2:EntityVersion): Int = {
        versionCompare(v1.version, v2.version)
    }

    // http://stackoverflow.com/questions/6701948/efficient-way-to-compare-version-strings-in-java
    def versionCompare(v1: String, v2: String): Int = {

        val v1IsSnapshot = v1.endsWith("-SNAPSHOT")
        val v2IsSnapshot = v2.endsWith("-SNAPSHOT")

        val vals1 = v1.replace("-SNAPSHOT", "") split("""\.""")
        val vals2 = v2.replace("-SNAPSHOT", "") split("""\.""")

        var i = 0;
        // set index to first non-equal ordinal or length of shortest version string
        while (i < vals1.length && i < vals2.length && vals1(i).equals(vals2(i))) {
            i+=1
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
            logger.debug(s"""Matching entity $entity against ${_pattern}""")
            entity.matches(_pattern)
        } else {
            // equals
            entity == pattern
        }
    }
}