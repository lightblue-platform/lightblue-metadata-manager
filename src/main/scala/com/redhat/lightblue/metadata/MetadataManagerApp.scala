package com.redhat.lightblue.metadata

import java.nio.file.Files
import java.nio.file.Paths

import org.apache.commons.cli._
import org.apache.commons.cli.HelpFormatter
import org.slf4j._

import com.redhat.lightblue.client.http.LightblueHttpClient
import com.redhat.lightblue.client.http.LightblueHttpClient
import com.redhat.lightblue.metadata.MetadataManager._
import scala.io.Source
import com.redhat.lightblue.metadata.Control._

/**
 * Appplication's entry point.
 *
 */
object MetadataManagerApp {

    def main(args: Array[String]): Unit = {
        new MetadataManagerCli(args)
    }

}