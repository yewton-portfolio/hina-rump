package hina.app

import hina.app.admin.{ PublisherManager, TopicCreator }
import io.netty.handler.codec.http.HttpResponseStatus
import org.apache.camel.Exchange
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.model.rest.RestBindingMode

import scala.beans.BeanProperty

/**
 *
 */
class MainRouteBuilder() extends RouteBuilder {
  case class ErrorResponse(@BeanProperty message: String, @BeanProperty detail: String)

  override def configure(): Unit = {
    configureRoutes(getContext).onException(classOf[Exception])
      .handled(true)
      .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()))
      .setBody().constant(ErrorResponse("Internal Server Error", exceptionMessage().toString))
      .end()

    restConfiguration()
      .component("netty4-http")
      .host("localhost")
      .port(8875)
      .skipBindingOnErrorCode(false)
      .dataFormatProperty("prettyPrint", "true")
      .bindingMode(RestBindingMode.auto)

    rest("/v1/topics/")

      .post("/{name}/events")
      .produces("application/json")
      .to("seda:dirty-event")

      .put("/{topic}")
      .produces("application/json")
      .to(TopicCreator.Forwarder.endpointUri)

    rest("/v1/publishers/")

      .post("/")
      .consumes("application/json")
      .to(PublisherManager.Forwarder.endpointUri)
  }
}