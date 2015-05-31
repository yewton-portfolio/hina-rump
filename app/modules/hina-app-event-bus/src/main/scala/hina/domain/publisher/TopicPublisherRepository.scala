package hina.domain.publisher

trait TopicPublisherRepository {
  def exists(publisherName: String, topicName: String): Boolean
}

class TopicPublisherRepositoryOnMemory extends TopicPublisherRepository {
  override def exists(publisherName: String, topicName: String): Boolean = {
    TopicPublisherRepositoryOnMemory.records.exists {
      case (pub, topics) =>
        (pub == publisherName) || topics.contains(topicName)
    }
  }
}

object TopicPublisherRepositoryOnMemory {
  val records: Map[String, Set[String]] = Map(
    "omochi" -> Set("gohan", "sampo", "oyasumi"),
    "coro" -> Set("meshi", "aruku", "neru")
  )
}
