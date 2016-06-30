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

    val lbClientOption = Option.builder("lc")
        .required(true)
        .desc("Configuration file for lightblue-client")
        .longOpt("lightblue-client")
        .hasArg()
        .argName("lightblue-client.properties")
        .build();

    val helpOption = Option.builder("h")
        .required(false)
        .desc("prints usage")
        .longOpt("help")
        .build();

    options.addOption(lbClientOption);
    options.addOption(helpOption);

    val parser = new DefaultParser();
    val cmd = parser.parse(options, args);

    if (cmd.hasOption('h')) {
        // automatically generate the help statement
        val formatter = new HelpFormatter();
        formatter.printHelp(MetadataManagerApp.getClass.getSimpleName(), options);
        System.exit(0);
    }

    val lbClientFilePath = cmd.getOptionValue("lc");

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
    
    

    
}