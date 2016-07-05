package com.redhat.lightblue.metadata

object JavaUtil {

    /**
     * Converts scala collection to java collection.
     *
     * Taken from http://stackoverflow.com/questions/13981983/how-to-convert-a-nested-scala-collection-to-a-nested-java-collection
     */
    def toJava(x: Any): Any = {
        import scala.collection.JavaConverters._
        x match {
            case y: scala.collection.MapLike[_, _, _] =>
                y.map { case (d, v) => toJava(d) ->  toJava(sortIfList(v)) } asJava
            case y: scala.collection.SetLike[_, _] =>
                y map { item: Any => toJava(item) } asJava
            case y: Iterable[_] =>
                y map { item: Any => toJava(item) } asJava
            case y: Iterator[_] =>
                toJava(y.toIterable)
            case _ =>
                x
        }
    }

    /**
     * Sorts lists of strings because jackson does do this (is only able to sort keys).
     * // TODO: this is really ugly - conversion and sorting are mixed up
     */
    private def sortIfList(list: Any): Any = {
        try {
            // try to treat as a list of strings
            list.asInstanceOf[List[String]].sorted
        } catch {
            case e: ClassCastException => {
                list
            }
        }

    }

}