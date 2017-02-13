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
import com.redhat.lightblue.client.request.metadata.MetadataCreateSchemaRequest
import java.util.regex.Pattern
import scala.util.matching.Regex
import scala.collection.JavaConversions._
import com.fasterxml.jackson.databind.node.TextNode

import com.redhat.lightblue.metadata.Entity._;


/**
 * Metadata manipulation logic. Talks to Lightblue using lightblue-client.
 *
 */
class MetadataManager(val client: LightblueClient) {

    val logger = LoggerFactory.getLogger(this.getClass);

    def listEntities(): List[String] = {

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

    def getEntities(entityNamesList: List[String], entityVersionFilter: List[EntityVersion] => Option[EntityVersion]): List[Entity] = {

        entityNamesList.map { entityName =>

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

    def getEntities(entityNamePattern: String, entityVersionFilter: List[EntityVersion] => Option[EntityVersion]): List[Entity] = {

        implicit val pattern = entityNamePattern

        getEntities(listEntities().filter(entityNameFilter), entityVersionFilter)

    }

    def diffEntity(entity: Entity) {
        val remoteEntity = getEntity(entity.name, entityVersionNewest) match {
            case Some(x) => x
            case None => throw new Exception(s"""${entity.name} does not exist in this environment""")
        }

        val diff = remoteEntity diff entity

        println(toFormatedString(diff))
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


