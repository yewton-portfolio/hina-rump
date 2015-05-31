package hina.app

import com.fasterxml.jackson.databind.ObjectMapper
import hina.app.admin.{ PublisherManager, TopicCreator }
import hina.app.publisher.EventCreatorHttpPost
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode

import scala.beans.BeanProperty

class MainRouteBuilder() extends RouteBuilder {
  case class ErrorResponse(@BeanProperty message: String, @BeanProperty detail: String)

  override def configure(): Unit = {
    val mapper = new ObjectMapper()
    configureRoutes(getContext).onException(classOf[Exception])
      .handled(true)
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()))
      .setBody()
      .constant(mapper.writeValueAsString(ErrorResponse("Internal Server Error", exceptionMessage().toString)))
      .end()

    restConfiguration()
      .component("netty4-http")
      .host("localhost")
      .port(8875)
      .skipBindingOnErrorCode(false)
      .dataFormatProperty("prettyPrint", "true")
      .bindingMode(RestBindingMode.json)

    rest("/v1/topics/")

      .post("/{topic}/events")
      .produces("application/json")
      .bindingMode(RestBindingMode.off)
      .to(EventCreatorHttpPost.Forwarder.endpointUri)

      .put("/{topic}")
      .produces("application/json")
      .to(TopicCreator.Forwarder.endpointUri)

    rest("/v1/publishers/")

      .post("/")
      .consumes("application/json")
      .to(PublisherManager.Forwarder.endpointUri)
  }
}