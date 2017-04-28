package com.redhat.lightblue.metadata

/**
 * Application's entry point.
 *
 */
object MetadataManagerApp {

    def main(args: Array[String]): Unit = {
        new MetadataManagerCli(args)
    }

}