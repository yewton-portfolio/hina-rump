package hina.app.modules

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.routing._
import com.google.inject.name.{Named, Names}
import com.google.inject.{AbstractModule, Inject, Provides, Singleton}
import com.typesafe.config.Config
import hina.app.RestConsumer
import hina.app.admin.{PublisherManager, StarvingConsumer}
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.app.publisher.DirtyEventForwarder
import hina.domain.publisher.{PublisherRepository, PublisherRepositoryOnMemory, PublisherTopicRepository, PublisherTopicRepositoryOnMemory}
import hina.domain.{TopicConsumerRepository, TopicConsumerRepositoryOnMemory}
import hina.util.akka.GuiceAkkaActorRefProvider
import kafka.utils.ZKStringSerializer
import net.codingwell.scalaguice.ScalaModule
import org.I0Itec.zkclient.ZkClient

import scala.concurrent.ExecutionContext

class MainModule extends AbstractModule with ScalaModule with GuiceAkkaActorRefProvider {
  override def configure() = {
    bind[Actor].annotatedWith(Names.named(DirtyEventForwarder.name)).to[DirtyEventForwarder]
    bind[Actor].annotatedWith(Names.named(StarvingConsumer.name)).to[StarvingConsumer]
    bind[Actor].annotatedWithName(PublisherManager.Forwarder.name).to[PublisherManager.Forwarder]
    bind[Actor].annotatedWithName(PublisherManager.name).to[PublisherManager]
    bind[PublisherTopicRepository].to[PublisherTopicRepositoryOnMemory]
    bind[TopicConsumerRepository].to[TopicConsumerRepositoryOnMemory]
    bind[PublisherRepository].to[PublisherRepositoryOnMemory]
    bind[ExecutionContext].annotatedWithName(ZkExecutionContextProvider.name).toProvider[ZkExecutionContextProvider]
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
    RoundRobinPool(5, Some(DefaultResizer(lowerBound = 5, upperBound = 10)))

  @Provides
  @Named(PublisherManager.name)
  @Singleton
  @Inject
  def providePublisherManagerRef(system: ActorSystem,
                                 @Named(RestConsumer.poolName) pool: Pool): ActorRef =
    provideActorRef(system, PublisherManager.name, pool)
}
