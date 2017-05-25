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
import com.redhat.lightblue.metadata.util.IOUtils
import com.redhat.lightblue.metadata.util.IOUtilsImpl

/**
 * Command Line Interface for {@link MetadataManager}.
 *
 */
class MetadataManagerCli(args: Array[String], mdm: scala.Option[MetadataManager], ioUtils: IOUtils) {

    def this(args: Array[String]) = this(args, None, new IOUtilsImpl)
    def this(args: String, client: LightblueClient) = this(args.split(" "), Some(new MetadataManager(client)), new IOUtilsImpl)
    def this(args: String, mdm: MetadataManager, ioUtils: IOUtils) = this(args.split(" "), Some(mdm), ioUtils)

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
        implicit val cmd = parser.parse(options, optionsArgs)

        if (cmd.isHelp) {
            printUsage(options)
            System.exit(0);
        }

        if (!List("push", "pull", "diff", "list", "set", "apply").contains(operation)) {
            throw new ParseException(s"""Unsupported operation $operation""")
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
                    if (remoteEntities.size == 1 && cmd.isStdout) {
                        println(remoteEntity.text)
                    } else {
                        // TODO: should be logger.info, but that breaks the integration test
                        println(s"""Saving ${remoteEntity}...""")

                        ioUtils.saveEntityToFile(remoteEntity)
                    }
                }
            }
            case "diff" => {
                val entity = ioUtils.readEntityFromFile(cmd.entityName)

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

                ioUtils.saveEntityToFile(patchedEntity)

                logger.info(s"""Patched $patchedEntity""")

            }
            case "push" => {
                val entityNameValue = cmd.entityName

                // -e $local means that all local files are to be pulled
                val entityNames = if (entityNameValue == "$local") {
                    localEntityNames()
                } else {
                    List(entityNameValue)
                }

                for (entityName <- entityNames) {

                    var entity = ioUtils.readEntityFromFile(entityName)

                    logger.debug(s"""Loaded $entity from local file""")

                    createMetadataManager().putEntity(entity, cmd.metadataScope)
                }

            }
            case "set" => {

                var entity = ioUtils.readEntityFromFile(cmd.entityName)

                cmd.versions match {
                    case Some(vs) => entity = entity.version(vs)
                    case None     => ;
                }

                cmd.changelog match {
                    case Some(cl) => entity = entity.changelog(cl)
                    case None     => ;
                }

                ioUtils.saveEntityToFile(entity)
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

    /**
     * Initialize Lightblue client from cli.
     *
     */
    def createClient(cmd: CommandLine): LightblueClient = {

        cmd.lbClientConfigFilePath match {
            case None => throw new Exception("Can't initialize lightblue client - no configuration provided!")
            case Some(f) => {
                logger.debug(s"""Reading lightblue client configuration from ${f}""")

                new LightblueHttpClient(f)
            }
        }

    }

    /**
     * Initialize metadata manager from cli (unless already initialized).
     */
    def createMetadataManager()(implicit cmd: CommandLine): MetadataManager = {
        mdm match {
            case Some(x) => x
            case None => new MetadataManager(createClient(cmd))
        }
    }

}
