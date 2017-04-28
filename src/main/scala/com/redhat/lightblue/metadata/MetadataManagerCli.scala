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

import com.redhat.lightblue.client.LightblueClient
import com.redhat.lightblue.client.http.LightblueHttpClient
import com.redhat.lightblue.metadata.util.Control.using
import com.redhat.lightblue.metadata.util.OptionUtils._

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
                options.addOption(multiEntityOption)
                options.addOption(versionOption)
                options.addOption(stdoutOption)
            }
            case "push" => {
                options.addOption(lbClientOption)
                options.addOption(envOption)
                options.addOption(multiEntityOption)
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

        if (cmd.isHelp) {
            printUsage(options)
            System.exit(0);
        }

        if (!List("push", "pull", "diff", "list", "set", "apply").contains(operation)) {
            throw new ParseException(s"""Unsupported operation $operation""")
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

                val remoteEntities = if (cmd.entityName == "$local") {
                    // -e $local means that all local entities are to be pulled from Lightblue (refresh)
                    createMetadataManager().getEntities(localEntityNames(), cmd.versionsSelector)
                } else {
                    // entityNameValue could be a single entity name or pattern
                    createMetadataManager().getEntities(cmd.entityName, cmd.versionsSelector)
                }

                remoteEntities foreach { remoteEntity =>
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
            case "diff" => {
                val entityName = cmd.entityName

                val metadata = using(Source.fromFile(s"""$entityName.json""")) { source =>
                    source.mkString
                }

                val entity = new Entity(metadata)

                createMetadataManager().diffEntity(entity)
            }
            case "apply" => {

                val metadata = using(Source.fromFile(s"""${cmd.entityName}.json""")) { source =>
                    source.mkString
                }

                val entity = new Entity(metadata)

                val patchedEntity = cmd.patch match {
                    case JsonPatch(path) => {
                        val patchStr = using(Source.fromFile(path)) { source =>
                            source.mkString
                        }

                        entity.apply(Entity.mapper.readTree(patchStr))
                    }
                    case JavascriptPatch(path) => {
                        val patchStr = using(Source.fromFile(path)) { source =>
                            source.mkString
                        }

                        entity.apply(patchStr)
                    }
                }

                Files.write(Paths.get(s"""${cmd.entityName}.json"""), patchedEntity.text.getBytes)

                logger.info(s"""Patched $patchedEntity""")

            }
            case "push" => {
                if (cmd.hasOption("eio") && cmd.hasOption("so")) {
                    throw new ParseException("You need to provide either --entityInfoOnly or --schemaOnly switches, not both")
                }

                val entityNameValue = cmd.entityName

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

                    createMetadataManager().putEntity(entity, cmd.metadataScope)
                }

            }
            case "set" => {
                val entityName = cmd.entityName

                val metadata = using(Source.fromFile(s"""$entityName.json""")) { source =>
                    source.mkString
                }

                var entity = new Entity(metadata)

                cmd.versions match {
                    case Some(vs) => entity = entity.version(vs)
                    case None => ;
                }

                cmd.changelog match {
                    case Some(cl) => entity = entity.version(cl)
                    case None => ;
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

        cmd.lbClientConfigFilePath match {
            case None => None
            case Some(f) => {
                logger.debug(s"""Reading lightblue client configuration from ${f}""")

                Some(new LightblueHttpClient(f))
            }
        }

    }

    /**
     * create metadata manager
     */
    def createMetadataManager()(implicit client: scala.Option[LightblueClient]): MetadataManager = {
        client match {
            case None    => throw new Exception("Lightblue client is needed to create MetadataManager!")
            case Some(x) => new MetadataManager(x)
        }
    }

}
