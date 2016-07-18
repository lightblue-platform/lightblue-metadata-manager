package com.redhat.lightblue.metadata

import org.junit.ClassRule
import com.redhat.lightblue.client.integration.test.LightblueExternalResource.LightblueTestMethods
import com.redhat.lightblue.client.integration.test.LightblueExternalResource
import com.redhat.lightblue.util.test.AbstractJsonNodeTest.loadJsonNode
import com.redhat.lightblue.client.LightblueClient
import org.junit.Before
import org.junit.Test

import MetadataManagerIntegrationTests._
import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.After
import org.apache.commons.io.FileUtils
import java.io.File


object MetadataManagerIntegrationTests {

    var lightblue:LightblueExternalResource = _

    // ClassRule annotation can be only used on public, static fields. There are no public fields in scala.
    // As a workaround, using ClassRule on method (methods can be public) instead of a field.
    @ClassRule
    def initLightblue(): LightblueExternalResource = {
        lightblue = new LightblueExternalResource(new LightblueTestMethods() {

            def getMetadataJsonNodes() = {
                Array(loadJsonNode("./metadata/user.json"))
            }
        })

        lightblue
    }
}

/**
 * JUnit integration test using lightblue-client-integration-test.
 *
 */
class MetadataManagerIntegrationTests {

    var client: LightblueClient = _

    var stream:ByteArrayOutputStream = _

    def getReader() = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(stream.toByteArray())))

    @Before
    def before() {
        client = lightblue.getLightblueClient
        stream = new ByteArrayOutputStream()

        lightblue.cleanupMongoCollections("user")
    }

    @Test
    def testListMetadata() {

        Console.withOut(stream) {
            new MetadataManagerCli("list --env dev", client)
        }

        val r = getReader()

        Assert.assertEquals("user", r.readLine())
        Assert.assertEquals(null, r.readLine())
    }

    @Test
    def testPullMetadata() {
        Console.withOut(stream) {
            new MetadataManagerCli("pull --env dev -e user -v newest", client)
        }

        val r = getReader()

        Assert.assertEquals("Saving user|5.0.0...", r.readLine())
        Assert.assertEquals(null, r.readLine())
    }

    @After
    def after() {
          FileUtils.deleteQuietly(new File("user.json"))
    }

}