package hina.app.modules

import akka.actor.Actor
import com.google.inject.{ Inject, Provides, AbstractModule }
import com.google.inject.name.{ Named, Names }
import com.typesafe.config.Config
import hina.app.admin.StarvingConsumer
import hina.app.publisher.DirtyEventForwarder
import hina.domain.{ TopicConsumerRepositoryOnMemory, TopicConsumerRepository, PublisherTopicRepositoryOnMemory, PublisherTopicRepository }
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
  }

  @Provides
  @Inject
  def provideZkClient(config: Config): ZkClient = {
    val zkConnect = config.getString("zookeeper.connect")
    new ZkClient(zkConnect, 30000, 30000, ZKStringSerializer)
  }

  @Provides
  @Named("ZkIO")
  def provideZkIOExecutionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  @Provides
  @Named("KafkaIO")
  def provideKafkaIOExecutionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
