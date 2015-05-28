package hina.domain

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.beans.BeanProperty

package object publisher {
  case class PublisherId(id: UUID)
  case class Publisher(@BeanProperty id: PublisherId, @BeanProperty name: String)

  trait PublisherRepository {
    def nextIdentity: PublisherId
    def save(entity: Publisher): Unit
    def resolve(id: PublisherId): Publisher
  }

  class PublisherRepositoryOnMemory extends PublisherRepository {
    private[this] val records: java.util.Map[PublisherId, Publisher] = new ConcurrentHashMap[PublisherId, Publisher]()
    Seq("hina", "omochi", "tantan").foreach { name =>
      val id = nextIdentity
      records.put(id, Publisher(id, name))
    }

    override def nextIdentity: PublisherId = PublisherId(UUID.randomUUID())

    override def save(entity: Publisher): Unit = records.put(entity.id, entity)

    override def resolve(id: PublisherId): Publisher = records.get(id)
  }
}
