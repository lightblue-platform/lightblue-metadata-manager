package com.redhat.lightblue.metadata.util

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.MissingArgumentException
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import com.redhat.lightblue.metadata.Entity
import com.redhat.lightblue.metadata.EntityVersion
import org.apache.commons.cli.ParseException
import com.redhat.lightblue.metadata.MetadataScope

object OptionUtils {

    val lbClientOption = Option.builder("lc")
        .required(false)
        .desc("Configuration file for lightblue-client. --env is recommended instead.")
        .longOpt("lightblue-client")
        .hasArg()
        .argName("lightblue-client.properties")
        .build();

    val envOption = Option.builder()
        .required(false)
        .desc("Lightblue environment (export LB_CLIENT_{ENV}=/home/user/lightblue-clients/lightblue-client-{ENV}.properties")
        .longOpt("env")
        .hasArg()
        .argName("environment, e.g. dev")
        .build();

    val multiEntityOption = Option.builder("e")
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
        .desc("Entity version selector. Defaults to newest.")
        .hasArg()
        .argName("x.x.x|newest|default")
        .build();

    val helpOption = Option.builder("h")
        .required(false)
        .desc("Prints usage.")
        .longOpt("help")
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

    sealed trait Patch { val path: String }
    case class JsonPatch(val path: String) extends Patch
    case class JavascriptPatch(val path: String) extends Patch

    // implicit enrichment of apache's CommandLine
    implicit class MetadataManagerCommandLine(cmd: CommandLine) {
        //        def hasEntity() = cmd.hasOption("e")
        def entityName = cmd.getOptionValue("e")

        //        def hasVersion() = cmd.hasOption("v")
        def versionsSelector = if (cmd.hasOption("v")) parseVersion(cmd.getOptionValue("v")) else parseVersion("newest")

        def lbClientConfigFilePath = {
            if (!cmd.hasOption("lc") && !cmd.hasOption("env")) {
                None
            } else if (cmd.hasOption("lc") && cmd.hasOption("env")) {
                throw new ParseException("Either -lc or --env is required");
            } else {
                if (cmd.hasOption("lc")) {
                    Some(cmd.getOptionValue("lc"))
                } else {
                    val envVarName = "LB_CLIENT_" + cmd.getOptionValue("env").toUpperCase()
                    System.getenv(envVarName) match {
                        case null => throw new ParseException(s"""Environment var ${envVarName} is not set!""")
                        case x    => Some(x)
                    }
                }
            }
        }

        def isHelp = cmd.hasOption("h")

        def patch = {
            if (cmd.hasOption("jsp") && cmd.hasOption("jp") || !cmd.hasOption("jsp") && !cmd.hasOption("jp")) {
                throw new MissingArgumentException("Either -jp <json patch> or -jsp <javascript> is required")
            }

            if (cmd.hasOption("jsp")) {
                JavascriptPatch(cmd.getOptionValue("jsp"))
            } else {
                JsonPatch(cmd.getOptionValue("jp"))
            }
        }

        def metadataScope = {
            if (cmd.hasOption("eio") && cmd.hasOption("so")) {
                throw new ParseException("You need to provide either --entityInfoOnly or --schemaOnly switches, not both")
            }

            if (!cmd.hasOption("eio") && !cmd.hasOption("so")) {
                MetadataScope.BOTH
            } else if (cmd.hasOption("eio")) {
                MetadataScope.ENTITYINFO
            } else {
                MetadataScope.SCHEMA
            }
        }

        def changelog = optionValue("cl")

        def versions = optionValue("vs")


        private def optionValue(opt: String): scala.Option[String] = {
            if (cmd.hasOption(opt)) {
                Some(cmd.getOptionValue(opt))
            } else {
                None
            }
        }

        private def parseVersion(version: String): List[EntityVersion] => scala.Option[EntityVersion] = {
            version match {
                case "default" => Entity.entityVersionDefault
                case "newest"  => Entity.entityVersionNewest
                case x         => Entity.entityVersionExplicit(x)
            }
        }
    }

}