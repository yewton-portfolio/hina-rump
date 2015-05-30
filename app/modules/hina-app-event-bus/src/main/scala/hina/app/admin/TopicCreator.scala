package hina.app.admin

import java.util.Properties

import akka.actor.ActorRef
import akka.camel.CamelMessage
import akka.pattern.pipe
import com.google.inject.Inject
import com.google.inject.name.Named
import hina.app.modules.Providers.ZkExecutionContextProvider
import hina.app.{ RestConsumer, RestProcessor }
import hina.util.akka.NamedActor
import io.netty.handler.codec.http.HttpResponseStatus
import kafka.admin.AdminUtils
import kafka.common.TopicExistsException
import org.I0Itec.zkclient.ZkClient
import org.apache.camel.Exchange

import scala.beans.BeanProperty
import scala.concurrent.{ ExecutionContext, Future, blocking }
import scala.util.control.NonFatal

object TopicCreator extends NamedActor {
  override final val name = "TopicCreator"

  object Forwarder extends NamedActor {
    override final val name = "TopicCreatorForwarder"
    final val endpointUri = "seda:topic-creator"
  }

  class Forwarder @Inject() (@Named(TopicCreator.name) override val processor: ActorRef) extends RestConsumer {
    override final val endpointUri = Forwarder.endpointUri
  }

  case class Response(@BeanProperty topic: String,
                      @BeanProperty partitions: Int,
                      @BeanProperty replicas: Int)
  case class ErrorResponse(@BeanProperty code: String, @BeanProperty detail: String)
  object ErrorResponse {
    def asCamelMessage(code: String, detail: String, status: HttpResponseStatus): CamelMessage = {
      CamelMessage(ErrorResponse(code, detail), Map(
        Exchange.HTTP_RESPONSE_CODE -> status.code()
      ))
    }
  }
}

class TopicCreator @Inject() (zkClient: ZkClient,
                              @Named(ZkExecutionContextProvider.name) implicit val ec: ExecutionContext) extends RestProcessor {
  override def receive = {
    case msg: CamelMessage =>
      val params = for {
        topic <- msg.headerAs[String]("topic")
        partitions <- msg.headerAs[Int]("partitions")
        replicas <- msg.headerAs[Int]("replicas")
      } yield (topic, partitions, replicas)
      val result = Future.fromTry(params).map {
        case (topic, partitions, replicas) =>
          val topicConfig = new Properties()
          topicConfig.put("min.insync.replicas", "2")
          blocking {
            AdminUtils.createTopic(zkClient, topic, partitions, replicas, topicConfig)
          }
          TopicCreator.Response(topic, partitions, replicas)
      }
      val future = result.recover {
        case e: TopicExistsException =>
          TopicCreator.ErrorResponse.asCamelMessage("conflict", e.getMessage, HttpResponseStatus.CONFLICT)
        case e: NoSuchElementException =>
          TopicCreator.ErrorResponse.asCamelMessage(
            "parameter missing",
            e.getMessage + " is missing.",
            HttpResponseStatus.BAD_REQUEST)
        case NonFatal(e) =>
          TopicCreator.ErrorResponse.asCamelMessage("error", e.getMessage, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      }
      pipe(future).to(sender())
  }
}
