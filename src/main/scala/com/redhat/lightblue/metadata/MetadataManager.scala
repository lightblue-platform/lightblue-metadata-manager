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
    
    def getEntity(entityName: String, entityVersion: EntityVersion): Entity = getEntity(entityName, entityVersion.version)

    def getEntity(entityName: String, entityVersion: String): Entity = {

        val getE = new MetadataGetEntityMetadataRequest(entityName, entityVersion)

        val json = client.metadata(getE).getJson        
        
        new Entity(json.asInstanceOf[ObjectNode])       
    }
    
    def getEntities(entityNameRegex: Regex, entityVersionFilter: List[EntityVersion] => Option[EntityVersion]): List[Entity] = {
                
        listEntities.filter( _.matches(entityNameRegex.regex)).map { entityName =>
            
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
    
    val mapper = new ObjectMapper();
    mapper.registerModule(DefaultScalaModule)
    
    val entityVersionDefault = (l: List[EntityVersion]) => l collectFirst {case v if v.defaultVersion => v}
    val entityVersionNewest = (l: List[EntityVersion]) => Some(l.sortWith(_.version > _.version) (0))
    
}