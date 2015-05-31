package hina

/**
 *
 */
package object domain {

  object Topic {
    final val prefix = "hina.topic"
    def withName(name: String): Topic = Topic(s"$prefix.$name")
  }

  case class Topic private (name: String)
}
