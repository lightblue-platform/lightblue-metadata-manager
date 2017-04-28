package com.redhat.lightblue.metadata

import org.junit.Test

import org.scalatest.FlatSpec
import org.scalatest.{Matchers => ScalaTestMatchers}
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.redhat.lightblue.metadata.util.IOUtils

import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers
import org.mockito.Matchers.eq
import org.scalatest.BeforeAndAfterAll
import org.mockito.Mockito
import com.redhat.lightblue.metadata.Entity._
import org.mockito.ArgumentCaptor

/**
 * Test cli with an exception of Lightblue client initialization (so no env required).
 *
 */
@RunWith(classOf[JUnitRunner])
class MetadataManagerCliUnitTest extends FlatSpec with BeforeAndAfterAll with ScalaTestMatchers with MockitoSugar {

    val fooEntityStr = """{
	"schema": {
		"version": {
		  "value": "0.0.1-SNAPSHOT"
		},
		"field": {
			"foo": "bar"
		}
	},
	"entityInfo": {
		"defaultVersion": "0.0.1-SNAPSHOT",
		"name": "foo"
	}
}"""

    var mdm: MetadataManager = _
    var ioUtils: IOUtils = _
    var fooEntity: Entity = _

    override def beforeAll() {
        mdm = mock[MetadataManager]
        ioUtils = mock[IOUtils]
        fooEntity = new Entity(fooEntityStr)
    }

    "list" should "list all entities" in {

        when(mdm.listEntities()) thenReturn(List("foo", "bar"))

        new MetadataManagerCli("list", mdm, ioUtils)

        verify(mdm).listEntities()
    }

    "pull -e foo -v 1.0.0" should "download foo|1.0.0" in {

        when(mdm.getEntities(Matchers.eq("foo"), Matchers.any())) thenReturn(List(fooEntity))

        new MetadataManagerCli("pull -e foo -v 1.0.0", mdm, ioUtils)

        var argCaptor = ArgumentCaptor.forClass(classOf[ List[EntityVersion] => Option[EntityVersion]]);
        verify(mdm).getEntities(Matchers.eq("foo"), argCaptor.capture())
        verify(ioUtils).saveEntityToFile(fooEntity)

        // verify correct version selector is used
        val versionSelectorFunction = argCaptor.getValue
        versionSelectorFunction(List(EntityVersion("1.0.0", "changelog", "inactive", false))) should be (Some(EntityVersion("1.0.0", "changelog", "inactive", false)))
    }

}