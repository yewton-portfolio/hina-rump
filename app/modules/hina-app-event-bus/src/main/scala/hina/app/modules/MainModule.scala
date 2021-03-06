package hina.app.modules

import akka.actor.{ Actor, ActorRef, ActorSystem }
import akka.routing._
import com.google.inject.name.{ Named, Names }
import com.google.inject.{ AbstractModule, Inject, Provides, Singleton }
import com.typesafe.config.Config
import hina.app.RestConsumer
import hina.app.admin.{ PublisherManager, StarvingConsumer, TopicCreator }
import hina.app.modules.Providers.{ BlockingIOExecutionContextProvider, ZkExecutionContextProvider }
import hina.app.publisher.{ EventCreator, EventCreatorHttpPost, EventCreatorRabbitMQ }
import hina.app.subscriber.EventConsumer
import hina.domain.publisher.{ PublisherRepository, PublisherRepositoryOnMemory, TopicPublisherRepository, TopicPublisherRepositoryOnMemory }
import hina.domain.{ TopicSubscriberRepository, TopicSubscriberRepositoryOnMemory }
import hina.util.akka.GuiceAkkaActorRefProvider
import kafka.utils.ZKStringSerializer
import net.codingwell.scalaguice.ScalaModule
import org.I0Itec.zkclient.ZkClient

import scala.concurrent.ExecutionContext

class MainModule extends AbstractModule with ScalaModule with GuiceAkkaActorRefProvider {
  override def configure() = {
    bind[Actor].annotatedWith(Names.named(StarvingConsumer.name)).to[StarvingConsumer]
    bind[Actor].annotatedWithName(PublisherManager.Forwarder.name).to[PublisherManager.Forwarder]
    bind[Actor].annotatedWithName(PublisherManager.name).to[PublisherManager]
    bind[Actor].annotatedWithName(TopicCreator.Forwarder.name).to[TopicCreator.Forwarder]
    bind[Actor].annotatedWithName(TopicCreator.name).to[TopicCreator]
    bind[Actor].annotatedWithName(EventCreatorHttpPost.Forwarder.name).to[EventCreatorHttpPost.Forwarder]
    bind[Actor].annotatedWithName(EventCreatorHttpPost.name).to[EventCreatorHttpPost]
    bind[Actor].annotatedWithName(EventCreator.name).to[EventCreator]
    bind[Actor].annotatedWithName(EventCreatorRabbitMQ.name).to[EventCreatorRabbitMQ]
    bind[Actor].annotatedWithName(EventConsumer.name).to[EventConsumer]
    bind[TopicPublisherRepository].to[TopicPublisherRepositoryOnMemory]
    bind[TopicSubscriberRepository].to[TopicSubscriberRepositoryOnMemory]
    bind[PublisherRepository].to[PublisherRepositoryOnMemory]
    bind[ExecutionContext].annotatedWithName(ZkExecutionContextProvider.name).toProvider[ZkExecutionContextProvider]
    bind[ExecutionContext].annotatedWithName(BlockingIOExecutionContextProvider.name).toProvider[BlockingIOExecutionContextProvider]
  }

  @Provides
  @Inject
  def provideZkClient(config: Config): ZkClient = {
    val zkConnect = config.getString("zookeeper.connect")
    new ZkClient(zkConnect, 30000, 30000, ZKStringSerializer)
  }

  @Provides
  @Named(RestConsumer.poolName)
  @Inject
  def provideRestConsumerPool(system: ActorSystem): Pool =
    RoundRobinPool(10, Some(DefaultResizer(lowerBound = 5, upperBound = 500)))

  @Provides
  @Named(PublisherManager.name)
  @Singleton
  @Inject
  def providePublisherManagerRef(system: ActorSystem,
                                 @Named(RestConsumer.poolName) pool: Pool): ActorRef =
    provideActorRef(system, PublisherManager.name, pool)

  @Provides
  @Named(TopicCreator.name)
  @Singleton
  @Inject
  def provideTopicCreatorRef(system: ActorSystem,
                             @Named(RestConsumer.poolName) pool: Pool): ActorRef =
    provideActorRef(system, TopicCreator.name, pool)

  @Provides
  @Named(EventCreator.name)
  @Singleton
  @Inject
  def provideEventCreatorRef(system: ActorSystem): ActorRef =
    provideActorRef(
      system,
      EventCreator.name, RoundRobinPool(10, Some(DefaultResizer(lowerBound = 5, upperBound = 500))))

  @Provides
  @Named(EventCreatorHttpPost.name)
  @Singleton
  @Inject
  def provideEventCreatorHttpPostRef(system: ActorSystem,
                                     @Named(RestConsumer.poolName) pool: Pool): ActorRef =
    provideActorRef(system, EventCreatorHttpPost.name, pool)
}
