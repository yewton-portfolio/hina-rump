package hina.app.admin

import akka.actor.Actor
import akka.camel.{CamelMessage, Consumer}
import akka.pattern.pipe
import com.google.inject.name.{Named, Names}
import com.google.inject.{AbstractModule, Inject}
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.util.akka.{GuiceAkkaActorRefProvider, NamedActor}
import io.netty.handler.codec.http.HttpResponseStatus
import kafka.admin.AdminUtils
import kafka.common.TopicExistsException
import net.codingwell.scalaguice.ScalaModule
import org.I0Itec.zkclient.ZkClient
import org.apache.camel.Exchange

import scala.beans.BeanProperty
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.control.NonFatal

case class TopicCreateResponse(@BeanProperty topic: String,
                               @BeanProperty partitions: Int,
                               @BeanProperty replicas: Int)
case class TopicCreateErrorResponse(@BeanProperty code: String, @BeanProperty detail: String)
object TopicCreateErrorResponse {
  def asCamelMessage(code: String, detail: String, status: HttpResponseStatus): CamelMessage = {
    CamelMessage(TopicCreateErrorResponse(code, detail), Map(
      Exchange.HTTP_RESPONSE_CODE -> status.code()
    ))
  }
}

class TopicCreatorModule extends AbstractModule with ScalaModule with GuiceAkkaActorRefProvider {
  override def configure(): Unit = {
    bind[Actor].annotatedWith(Names.named(TopicCreator.name)).to[TopicCreator]
  }
}

object TopicCreator extends NamedActor {
  override final val name = "TopicCreator"
}

class TopicCreator @Inject() (zkClient: ZkClient, @Named(ZkExecutionContextProvider.name) ec: ExecutionContext) extends Consumer {
  override val endpointUri = "seda:create-topic"
  implicit private[this] val executionContext = ec

  override def receive = {
    case msg: CamelMessage =>
      import Future.fromTry
      val result = for {
        topic <- fromTry(msg.headerAs[String]("topic"))
        partitions <- fromTry(msg.headerAs[Int]("partitions"))
        replicas <- fromTry(msg.headerAs[Int]("replicas"))
      } yield {
        blocking {
          Seq("dirty", "clean", "invalid").foreach { suffix =>
            AdminUtils.createTopic(zkClient, s"$topic.$suffix", partitions, replicas)
          }
        }
        TopicCreateResponse(topic, partitions, replicas)
      }
      result.recover {
        case e: TopicExistsException =>
          TopicCreateErrorResponse.asCamelMessage("conflict", e.getMessage, HttpResponseStatus.CONFLICT)
        case e: NoSuchElementException =>
          TopicCreateErrorResponse.asCamelMessage(
            "parameter missing",
            e.getMessage + " is missing.",
            HttpResponseStatus.BAD_REQUEST)
        case NonFatal(e) =>
          TopicCreateErrorResponse.asCamelMessage("error", e.getMessage, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      } pipeTo sender()
  }
}
