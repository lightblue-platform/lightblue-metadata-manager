package com.redhat.lightblue.metadata

import java.nio.file.Files
import java.nio.file.Paths

import scala.io.Source

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.MissingArgumentException
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.slf4j.LoggerFactory

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.redhat.lightblue.client.LightblueClient
import com.redhat.lightblue.client.http.LightblueHttpClient
import com.redhat.lightblue.metadata.util.Control.using

/**
 * Command Line Interface for {@link MetadataManager}.
 *
 */
class MetadataManagerCli(args: Array[String], _client: scala.Option[LightblueClient]) {

    def this(args: Array[String]) = this(args, None)
    def this(args: String, client: LightblueClient) = this(args.split(" "), Some(client))

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
            .desc("Prints usage.")
            .longOpt("help")
            .build();

        val entityOption = Option.builder("e")
            .required(true)
            .longOpt("entity")
            .desc("Entity name. You can use regular expression to match multiple entities by name. You can use $local to match all entities in current local directory).")
            .hasArg()
            .argName("entity name or /regex/ or $local")
            .build();

        val singleEntityOption = Option.builder("e")
            .required(true)
            .longOpt("entity")
            .desc("Entity name.")
            .hasArg()
            .argName("entity name")
            .build();

        val versionOption = Option.builder("v")
            .required(false)
            .longOpt("version")
            .desc("Entity version selector.")
            .hasArg()
            .argName("x.x.x|newest|default")
            .build();

        val pathOption = Option.builder("p")
            .required(false)
            .longOpt("path")
            .desc("Pull specified path, e.g 'entityInfo.indexes'. Leave local metadata otherwise intact.")
            .hasArg()
            .argName("path")
            .build();

        val entityInfoOnlyOption = Option.builder("eio")
            .required(false)
            .longOpt("entityInfoOnly")
            .desc("Push entityInfo only.")
            .build();

        val schemaOnlyOption = Option.builder("so")
            .required(false)
            .longOpt("schemaOnly")
            .desc("Push schema only.")
            .build();

        val setChangelogOption = Option.builder("cl")
            .required(false)
            .longOpt("changelog")
            .desc("Set version.changelog")
            .hasArg()
            .build()

        val setVersionsOption = Option.builder("vs")
            .required(false)
            .longOpt("versions")
            .desc("Set schema version.value and entityInfo.defaultVersion")
            .hasArg()
            .build()

        val jsonPatchOption = Option.builder("jp")
            .required(false)
            .longOpt("json-patch")
            .desc("A file containing RFC 6902 JSON patch")
            .hasArg()
            .build()

        val jsPatchOption = Option.builder("jsp")
            .required(false)
            .longOpt("js-patch")
            .desc("A file containing entity modification logic in javascript")
            .hasArg()
            .build()
            
        val stdoutOption = Option.builder("c")
            .required(false)
            .longOpt("stdout")
            .desc("Send resuls to stdout. Will work only if you pull a single entity.")
            .build()

        // options which apply to any operation
        options.addOption(helpOption)

        if (args.length == 0) {
            printUsage(options)
            System.exit(1)
        }

        val operation = args(0)

        // operation specific options
        operation match {
             case "list" => {
                options.addOption(lbClientOption)
                options.addOption(envOption)
            }
            case "pull" => {
                options.addOption(lbClientOption)
                options.addOption(envOption)
                options.addOption(entityOption)
                options.addOption(versionOption)
                options.addOption(pathOption)
                options.addOption(stdoutOption)
            }
            case "push" => {
                options.addOption(lbClientOption)
                options.addOption(envOption)
                options.addOption(entityOption)
                options.addOption(entityInfoOnlyOption)
                options.addOption(schemaOnlyOption)
            }
            case "diff" => {
                options.addOption(lbClientOption)
                options.addOption(envOption)
                options.addOption(singleEntityOption)
            }
            case "set" => {
                options.addOption(singleEntityOption)
                options.addOption(setVersionsOption)
                options.addOption(setChangelogOption)
            }
            case "apply" => {
                options.addOption(singleEntityOption)
                options.addOption(jsonPatchOption)
                options.addOption(jsPatchOption)
            }
            case _ => ;
        }

        val optionsArgs = args.slice(1, args.length)

        val parser = new DefaultParser()
        val cmd = parser.parse(options, optionsArgs)

        if (cmd.hasOption('h')) {
            printUsage(options)
            System.exit(0);
        }

        if (!List("push", "pull", "diff", "list", "set", "apply").contains(operation)) {
            throw new ParseException(s"""Unsupported operation $operation""")
        }

        if (cmd.hasOption("lc") && cmd.hasOption("env")) {
            throw new ParseException("Either -lc or --env is required");
        }

        // initialize Lightblue client
        // use explicitly passed client if provided (for unit tests)
        implicit val lbClient = _client match {
            case Some(x) => {
                logger.debug("""Lightblue client passed to cli.""")
                Some(x)
            }
            case None => createClient(cmd)
        }


        operation match {
            case "list" => {
                createMetadataManager().listEntities().foreach(println)
            }
            case "pull" => {

                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e <entity name> is required")
                }

                if (!cmd.hasOption("v")) {
                    throw new MissingArgumentException("-v <entity version> is required")
                }

                val version = parseVersion(cmd.getOptionValue("v"))
                val entityNameValue = cmd.getOptionValue("e")

                val remoteEntities = if (entityNameValue == "$local") {
                    // -e $local means that all local entities are to be pulled from Lightblue (refresh)
                    createMetadataManager().getEntities(localEntityNames(), version)
                } else {
                    // entityNameValue could be a single entity name or pattern
                    createMetadataManager().getEntities(entityNameValue, version)
                }

                remoteEntities foreach { remoteEntity =>
                    if (cmd.hasOption("p")) {
                        // download metadata path from Lightblue and save it locally
                        val path = cmd.getOptionValue("p")

                        val localEntity = new Entity(using(Source.fromFile(s"""${remoteEntity.name}.json""")) { source =>
                            source.mkString
                        })

                        val updatedLocalEntity = localEntity.replacePath(path, remoteEntity)

                        if (remoteEntities.size == 1 && cmd.hasOption("c")) {
                            println(updatedLocalEntity.text)
                        } else {
                            logger.info(s"""Saving $path to ${updatedLocalEntity.name}.json...""")
                            Files.write(Paths.get(s"""${updatedLocalEntity.name}.json"""), updatedLocalEntity.text.getBytes)
                        }
                    } else {
                        // download metadata from Lightblue and save it locally
                        if (remoteEntities.size == 1 && cmd.hasOption("c")) {
                            println(remoteEntity.text)
                        } else {
                            // TODO: should be logger.info, but that breaks the integration test
                            println(s"""Saving ${remoteEntity}...""")
                            Files.write(Paths.get(s"""${remoteEntity.name}.json"""), remoteEntity.text.getBytes)
                        }
                    }
                }
            }
            case "diff" => {
                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e <entity name> is required")
                }

                val entityName = cmd.getOptionValue("e")

                val metadata = using(Source.fromFile(s"""$entityName.json""")) { source =>
                    source.mkString
                }

                val entity = new Entity(metadata)

                createMetadataManager().diffEntity(entity)
            }
            case "apply" => {

                if (cmd.hasOption("jsp") && cmd.hasOption("jp") || !cmd.hasOption("jsp") && !cmd.hasOption("jp")) {
                    throw new MissingArgumentException("Either -jp <json patch> or -jsp <javascript> is required")
                }

                val entityName = cmd.getOptionValue("e")

                val metadata = using(Source.fromFile(s"""$entityName.json""")) { source =>
                    source.mkString
                }

                val entity = new Entity(metadata)

                val patchedEntity = if (cmd.hasOption("jp")) {
                    // json patch

                    val patchPath = cmd.getOptionValue("jp")

                    val patchStr = using(Source.fromFile(patchPath)) { source =>
                        source.mkString
                    }

                    entity.apply(Entity.mapper.readTree(patchStr))
                } else {
                    // javascript

                    val patchPath = cmd.getOptionValue("jsp")

                    val patchStr = using(Source.fromFile(patchPath)) { source =>
                        source.mkString
                    }

                    entity.apply(patchStr)
                }

                Files.write(Paths.get(s"""${entityName}.json"""), patchedEntity.text.getBytes)

                logger.info(s"""Patched $patchedEntity""")

            }
            case "push" => {
                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e <entity name> is required")
                }

                if (cmd.hasOption("eio") && cmd.hasOption("so")) {
                    throw new ParseException("You need to provide either --entityInfoOnly or --schemaOnly switches, not both")
                }

                val entityNameValue = cmd.getOptionValue("e")

                // -e $local means that all local files are to be pulled
                val entityNames = if (entityNameValue == "$local") {
                    localEntityNames()
                } else {
                    List(entityNameValue)
                }

                for (entityName <- entityNames) {

                    val metadata = using(Source.fromFile(s"""$entityName.json""")) { source =>
                        source.mkString
                    }

                    var entity = new Entity(metadata)

                    logger.debug(s"""Loaded $entity from local file""")

                    if (cmd.hasOption("eio")) {
                        createMetadataManager().putEntity(entity, MetadataScope.ENTITYINFO)
                    } else if (cmd.hasOption("so")) {
                        createMetadataManager().putEntity(entity, MetadataScope.SCHEMA)
                    } else {
                        createMetadataManager().putEntity(entity, MetadataScope.BOTH)
                    }
                }

            }
            case "set" => {
                if (!cmd.hasOption("e")) {
                    throw new MissingArgumentException("-e <entity name> is required")
                }

                val entityName = cmd.getOptionValue("e")

                val metadata = using(Source.fromFile(s"""$entityName.json""")) { source =>
                    source.mkString
                }

                var entity = new Entity(metadata)

                if (cmd.hasOption("vs")) {
                    entity = entity.version(cmd.getOptionValue("vs"))
                }

                if (cmd.hasOption("cl")) {
                    entity = entity.changelog(cmd.getOptionValue("cl"))
                }

                Files.write(Paths.get(s"""${entityName}.json"""), entity.text.getBytes)
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

    // return *.json file names from current directory
    def localEntityNames(): List[String] = {
        new java.io.File(".").listFiles.filter(f => { f.isFile() && f.getName.endsWith("json") && !f.getName.startsWith(".") }).map { f => f.getName.replaceAll("""\.json""", "") }.toList
    }

    def printUsage(options: Options) {
        val formatter = new HelpFormatter();
        formatter.printHelp(180, MetadataManagerApp.getClass.getSimpleName + " <operation> <options>",
            "\nAvailable operations: list, pull, push, diff, apply and set. Add -h after operation to see options it accepts.\n\nOptions:", options, null)

    }

    def parseVersion(version: String): List[EntityVersion] => scala.Option[EntityVersion] = {
        version match {
            case "default" => Entity.entityVersionDefault
            case "newest"  => Entity.entityVersionNewest
            case x         => Entity.entityVersionExplicit(x)
        }
    }

    /**
     * Initialize Lightblue client from cli.
     *
     */
    def createClient(cmd: CommandLine): scala.Option[LightblueClient] = {

        if (!cmd.hasOption("lc") && !cmd.hasOption("env")) {
            None
        } else {
            val lbClientFilePath = if (cmd.hasOption("lc")) cmd.getOptionValue("lc") else {
                val envVarName = "LB_CLIENT_" + cmd.getOptionValue("env").toUpperCase()
                System.getenv(envVarName) match {
                    case null => throw new ParseException(s"""${envVarName} is not set!""")
                    case x    => x
                }
            }

            logger.debug(s"""Reading lightblue client configuration from ${lbClientFilePath}""")
            Some(new LightblueHttpClient(lbClientFilePath))
        }

    }

    /**
     * create metadata manager
     */
    def createMetadataManager()(implicit client: scala.Option[LightblueClient]): MetadataManager = {
        client match {
            case None => throw new Exception("Lightblue client is needed to create MetadataManager!")
            case Some(x) => new MetadataManager(x)
        }
    }

}
