package hina.domain.publisher

trait PublisherTopicRepository {
  def exists(publisherName: String, topicName: String): Boolean
}

class PublisherTopicRepositoryOnMemory extends PublisherTopicRepository {
  override def exists(publisherName: String, topicName: String): Boolean = {
    PublisherTopicRepositoryOnMemory.records.exists {
      case (pub, topics) =>
        (pub == publisherName) || topics.contains(topicName)
    }
  }
}

object PublisherTopicRepositoryOnMemory {
  val records: Map[String, Set[String]] = Map(
    "omochi" -> Set("gohan", "sampo", "oyasumi"),
    "coro" -> Set("meshi", "aruku", "neru")
  )
}
