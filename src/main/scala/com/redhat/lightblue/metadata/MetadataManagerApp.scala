package com.redhat.lightblue.metadata

import com.redhat.lightblue.client.http.LightblueHttpClient
import com.redhat.lightblue.metadata.MetadataManager._
import org.apache.commons.cli._
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions
import org.apache.commons.cli.HelpFormatter
import com.redhat.lightblue.client.http.LightblueHttpClient
import java.io.PrintWriter
import java.io.File
import java.nio.file.Paths
import java.nio.file.Files

object MetadataManagerApp extends App {

    val logger = LoggerFactory.getLogger(MetadataManagerApp.getClass);

    val options = new Options();
    
    try {
    

    
        val lbClientOption = Option.builder("lc")
            .desc("Configuration file for lightblue-client")
            .longOpt("lightblue-client")
            .hasArg()
            .argName("lightblue-client.properties")
            .build();

        val envOption = Option.builder("e")
            .desc("Lightblue environment (export LB_CLIENT_{ENV}=/home/user/lightblue-clients/lightblue-client-{ENV}.properties")
            .longOpt("environment")
            .hasArg()
            .argName("environment, e.g. dev")
            .build();

        val helpOption = Option.builder("h")
            .required(false)
            .desc("prints usage")
            .longOpt("help")
            .build();

        options.addOption(lbClientOption);
        options.addOption(envOption);
        options.addOption(helpOption);

        val parser = new DefaultParser();
        val cmd = parser.parse(options, args);

        if (cmd.hasOption('h')) {
            // automatically generate the help statement
            printUsage(options)
            System.exit(0);
        }

        if (cmd.hasOption("lc") && cmd.hasOption("e") || !cmd.hasOption("lc") && !cmd.hasOption("e")) {
            throw new ParseException("Either -lc or -e is required");
        }

        val lbClientFilePath = if (cmd.hasOption("lc")) cmd.getOptionValue("lc") else {

            val envVarName = "LB_CLIENT_"+cmd.getOptionValue("e").toUpperCase()
            System.getenv(envVarName) match {
                case null => throw new ParseException(s"""${envVarName} is not set!""")
                case x => x
            }
        }
        logger.debug(s"""Reading lightblue client configuration from ${lbClientFilePath}""")

        val client = new LightblueHttpClient(lbClientFilePath);

        val manager = new MetadataManager(client)

    //    val defaultUserVersion = manager.getEntityVersion("user", entityVersionDefault) match {
    //        case Some(x) => x
    //        case None    => throw new Exception("Version not found!")
    //    }
    //
    //    val userEntity = manager.getEntity("user", defaultUserVersion)

        manager.getEntities("^user.*".r, entityVersionDefault) foreach { entity =>
            val fileName = s"""${entity.name}.json"""
            logger.info(s"""Saving $fileName...""")
            Files.write(Paths.get(s"""${entity.name}.json"""), entity.text.getBytes)
        }

    } catch {
        case pe: ParseException => {
            logger.error(pe.getMessage)
            printUsage(options)
            System.exit(0);
        }
    }

    def printUsage(options: Options) {
        val formatter = new HelpFormatter();
        formatter.printHelp(120, MetadataManagerApp.getClass.getSimpleName, "", options, null);
    }

    
}