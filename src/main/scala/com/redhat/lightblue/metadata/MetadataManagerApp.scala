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

object MetadataManagerApp extends App {

    val logger = LoggerFactory.getLogger(MetadataManagerApp.getClass);

    val options = new Options();

    try {

        val lbClientOption = Option.builder("lc")
            .desc("Configuration file for lightblue-client. --env is recommended instead.")
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

        val entityOption = Option.builder("e")
            .required(false)
            .longOpt("entity")
            .desc("Entity name. You can use regular expression to match multiple entities by name.")
            .hasArg()
            .argName("entity name or /regex/")
            .build();

        val versionOption = Option.builder("v")
            .required(false)
            .longOpt("version")
            .desc("Entity version selector.")
            .hasArg()
            .argName("x.x.x|newest|default")
            .build();

        val ignoreHooksOption = Option.builder()
            .required(false)
            .longOpt("ignoreHooks")
            .desc("Don't push hooks.")
            .build();

        val ignoreIndexesOption = Option.builder()
            .required(false)
            .longOpt("ignoreIndexes")
            .desc("Don't push indexes.")
            .build();

        options.addOption(lbClientOption)
        options.addOption(envOption)
        options.addOption(helpOption)
        options.addOption(entityOption)
        options.addOption(versionOption)
        options.addOption(ignoreHooksOption)
        options.addOption(ignoreIndexesOption)

        if (args.length == 0) {
            printUsage(options)
            System.exit(1)
        }

        val operation = args(0)

        val optionsArgs = args.slice(1, args.length)

        val parser = new DefaultParser()
        val cmd = parser.parse(options, optionsArgs)

        if (cmd.hasOption('h')) {
            printUsage(options)
            System.exit(0);
        }

        if (!List("push", "pull", "diff", "list").contains(operation)) {
            throw new ParseException(s"""Unsupported operation $operation""")
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

        operation match {
            case "list" => {
                manager.listEntities.foreach(println(_))
            }
            case "pull" => {

                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e <entity name> is required")
                }

                if (!cmd.hasOption("v")) {
                    throw new MissingArgumentException("-v <entity version> is required")
                }

                val version = parseVersion(cmd.getOptionValue("v"))
                val entity = cmd.getOptionValue("e")

                manager.getEntities(entity, version) foreach { entity =>
                    logger.info(s"""Saving ${entity}...""")
                    Files.write(Paths.get(s"""${entity.name}.json"""), entity.text.getBytes)
                }

            }
            case "diff" => {
                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e <entity name> is required")
                }

                val entityName = cmd.getOptionValue("e")


                val metadata = using (Source.fromFile(s"""$entityName.json""")) { source =>
                    source.mkString
                }

                var entity = new Entity(metadata)

                if (cmd.hasOption("ignoreHooks")) {
                    entity = entity.stripHooks
                }

                if (cmd.hasOption("ignoreIndexes")) {
                    entity = entity.stripIndexes
                }

                manager.diffEntity(entity)
            }
            case "push" => {
                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e <entity name> is required")
                }

                val entityName = cmd.getOptionValue("e")

                val metadata = using (Source.fromFile(s"""$entityName.json""")) { source =>
                    source.mkString
                }

                var entity = new Entity(metadata)

                logger.debug(s"""Loaded $entity from local file""")

                if (cmd.hasOption("ignoreHooks")) {
                    entity = entity.stripHooks
                }

                if (cmd.hasOption("ignoreIndexes")) {
                    entity = entity.stripIndexes
                }

                manager.putEntity(entity, MetadataScope.BOTH)

            }
            case other => throw new UnsupportedOperationException(s"""Unknown operation $other""")
        }

    } catch {
        case pe: ParseException => {
            logger.error(pe.getMessage)
            printUsage(options)
            System.exit(1);
        }
    }

    def printUsage(options: Options) {
        val formatter = new HelpFormatter();
        formatter.printHelp(180, MetadataManagerApp.getClass.getSimpleName+" <operation> <options>",
                "\nAvailable operations: list, pull, push, diff.\n\nOptions:", options, null)


    }

    def operationArgsIndex(args: Array[String]): Int = {

        for (i: Int <- 1 until args.length) {
            if (List("pull", "push", "list").contains(args(i))) {
                return i
            }
        }

        throw new ParseException("Expected an operation: pull, push or list")
    }

    def parseVersion(version: String): List[EntityVersion] => scala.Option[EntityVersion] = {
        version match {
            case "default" => MetadataManager.entityVersionDefault
            case "newest"  => MetadataManager.entityVersionNewest
            case x         => MetadataManager.entityVersionExplicit(x)
        }
    }

}