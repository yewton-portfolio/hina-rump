package hina.app.modules

import akka.actor.Actor
import akka.routing.{DefaultResizer, Pool, Resizer, RoundRobinPool}
import com.google.inject.name.{Named, Names}
import com.google.inject.{AbstractModule, Inject, Provides}
import com.typesafe.config.Config
import hina.app.admin.StarvingConsumer
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.app.publisher.DirtyEventForwarder
import hina.domain.{PublisherTopicRepository, PublisherTopicRepositoryOnMemory, TopicConsumerRepository, TopicConsumerRepositoryOnMemory}
import kafka.utils.ZKStringSerializer
import net.codingwell.scalaguice.ScalaModule
import org.I0Itec.zkclient.ZkClient

import scala.concurrent.ExecutionContext

class MainModule extends AbstractModule with ScalaModule {
  override def configure() = {
    bind[Actor].annotatedWith(Names.named(DirtyEventForwarder.name)).to[DirtyEventForwarder]
    bind[Actor].annotatedWith(Names.named(StarvingConsumer.name)).to[StarvingConsumer]
    bind[PublisherTopicRepository].to[PublisherTopicRepositoryOnMemory]
    bind[TopicConsumerRepository].to[TopicConsumerRepositoryOnMemory]
    bind[ExecutionContext].annotatedWithName(ZkExecutionContextProvider.name).toProvider[ZkExecutionContextProvider]
  }

  @Provides
  @Inject
  def provideZkClient(config: Config): ZkClient = {
    val zkConnect = config.getString("zookeeper.connect")
    new ZkClient(zkConnect, 30000, 30000, ZKStringSerializer)
  }

  @Provides
  @Named("HttpApiActor")
  def provideHttpApiActorResizer: Resizer = DefaultResizer(lowerBound = 2, upperBound = 10)

  @Provides
  @Named("HttpApiActor")
  def provideHttpApiActorPool: Pool = RoundRobinPool(10)
}
