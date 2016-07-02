package com.redhat.lightblue.metadata

import java.nio.file.Files
import java.nio.file.Paths

import org.apache.commons.cli._
import org.apache.commons.cli.HelpFormatter
import org.slf4j.LoggerFactory

import com.redhat.lightblue.client.http.LightblueHttpClient
import com.redhat.lightblue.client.http.LightblueHttpClient
import com.redhat.lightblue.metadata.MetadataManager._
import scala.util.matching.Regex

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

        val envOption = Option.builder()
            .desc("Lightblue environment (export LB_CLIENT_{ENV}=/home/user/lightblue-clients/lightblue-client-{ENV}.properties")
            .longOpt("env")
            .hasArg()
            .argName("environment, e.g. dev")
            .build();

        val helpOption = Option.builder("h")
            .required(false)
            .desc("prints usage")
            .longOpt("help")
            .build();

        val opOption = Option.builder("o")
            .required(true)
            .longOpt("operation")
            .desc("Fetch remote metadata and save it locally in a json file")
            .hasArg()
            .argName("list|pull|push")
            .build();

        val entityOption = Option.builder("e")
            .required(false)
            .longOpt("entity")
            .desc("Entity name")
            .hasArg()
            .argName("entity name or /regex/")
            .build();

        val versionOption = Option.builder("v")
            .required(false)
            .longOpt("version")
            .desc("Entity version")
            .hasArg()
            .argName("x.x.x|newest|default")
            .build();

        options.addOption(lbClientOption)
        options.addOption(envOption)
        options.addOption(helpOption)
        options.addOption(opOption)
        options.addOption(entityOption)
        options.addOption(versionOption)

        val parser = new DefaultParser()
        val cmd = parser.parse(options, args)

        if (cmd.hasOption('h')) {
            // automatically generate the help statement
            printUsage(options)
            System.exit(0);
        }

        if (cmd.hasOption("lc") && cmd.hasOption("env") || !cmd.hasOption("lc") && !cmd.hasOption("env")) {
            throw new ParseException("Either -lc or --env is required");
        }

        val lbClientFilePath = if (cmd.hasOption("lc")) cmd.getOptionValue("lc") else {

            val envVarName = "LB_CLIENT_" + cmd.getOptionValue("env").toUpperCase()
            System.getenv(envVarName) match {
                case null => throw new ParseException(s"""${envVarName} is not set!""")
                case x    => x
            }
        }

        logger.debug(s"""Reading lightblue client configuration from ${lbClientFilePath}""")
        val client = new LightblueHttpClient(lbClientFilePath);

        val manager = new MetadataManager(client)

        cmd.getOptionValue("o") match {
            case "list" => {
                manager.listEntities.foreach(println(_))
            }
            case "pull" => {

                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e is required")
                }

                if (!cmd.hasOption("v")) {
                    throw new MissingArgumentException("-v is required")
                }

                val version = parseVersion(cmd.getOptionValue("v"))
                val entityName = parseEntity(cmd.getOptionValue("e"))

                entityName match {
                    case entityName:Regex => {

                        manager.getEntities(entityName, version) foreach { entity =>
                            val fileName = s"""${entity.name}.json"""
                            logger.info(s"""Saving $fileName...""")
                            Files.write(Paths.get(s"""${entity.name}.json"""), entity.text.getBytes)
                        }
                    }
                    case entityName:String => {


                        val entity = manager.getEntity(entityName, version)

                        val fileName = s"""${entity.name}.json"""
                        logger.info(s"""Saving $fileName...""")
                        Files.write(Paths.get(s"""${entity.name}.json"""), entity.text.getBytes)
                    }
                }

            }
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

    def operationArgsIndex(args: Array[String]): Int = {

        for (i: Int <- 1 until args.length) {
            if (List("pull", "push", "list").contains(args(i))) {
                return i
            }
        }

        throw new ParseException("Expected an operation: pull, push or list")
    }

    def parseEntity(entity: String): Any = {
        if (entity.startsWith("/") && entity.endsWith("/")) {
            entity.substring(1, entity.length() - 2).r
        } else {
            entity
        }
    }

    def parseVersion(version: String): List[EntityVersion] => scala.Option[EntityVersion] = {
        version match {
            case "default" => MetadataManager.entityVersionDefault
            case "newest"  => MetadataManager.entityVersionNewest
            case x         => MetadataManager.entityVersionExplicit(x)
        }
    }

}