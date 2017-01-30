package com.redhat.lightblue.metadata

/**
 * Appplication's entry point.
 *
 */
object MetadataManagerApp {

    def main(args: Array[String]): Unit = {
        new MetadataManagerCli(args)
    }

}