package com.redhat.lightblue.metadata.util

import com.redhat.lightblue.metadata.Entity
import java.nio.file.Paths
import java.nio.file.Files
import scala.io.Source
import com.redhat.lightblue.metadata.util.Control._

trait IOUtils {

    def saveToFile(data: String, path: String)

    def readFromFile(path: String): String

    def saveEntityToFile(entity: Entity)

    def readEntityFromFile(name: String): Entity
}

class IOUtilsImpl extends IOUtils {

    def saveToFile(data: String, path: String) = Files.write(Paths.get(path), data.getBytes)

    def readFromFile(path: String) = using(Source.fromFile(path)) { source =>
        source.mkString
    }

    def saveEntityToFile(entity: Entity) = saveToFile(entity.text, s"""${entity.name}.json""")

    def readEntityFromFile(name: String) = new Entity(readFromFile(s"""${name}.json"""))

}