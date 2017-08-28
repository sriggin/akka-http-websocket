package server

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.{ Flow, Sink, Source }
import java.io.File
import scala.util.Failure

class WebService(implicit system: ActorSystem) {
  import system.dispatcher

  val webroot = new File("web")
  val chatActor = system.actorOf(Props[ChatActor], "chat-hub")
  val theChat = new Chat(chatActor)

  def websocketChatFlow(user: String): Flow[Message, Message, Any] = Flow[Message]
    .collect { // extracts String from websocket protocol message
      case TextMessage.Strict(msg) => msg
      //TODO - support TextMessage.Streamed to support large messages
    }
    .via(theChat.flow(user)) // passes flow of user messages through the Chat flow
    .map { // converts chat protocol messages into websocket protocol messages
      case Protocol.Joined(user, allUsers) =>
        TextMessage.Strict(s"- $user joined  (In Room: ${allUsers.mkString(", ")})")
      case Protocol.Left(user, allUsers) =>
        TextMessage.Strict(s"- $user left    (In Room: ${allUsers.mkString(", ")})")
      case Protocol.ChatMessage(user, message) =>
        TextMessage.Strict(s"[$user]: $message")
    }
    .via(reportErrorsFlow) // handles any errors that occur in the flow

  def reportErrorsFlow[T]: Flow[T, T, Any] = Flow[T]
    .watchTermination()((_, f) => f.onComplete {
      case Failure(cause) => println(s"WS stream failed with $cause")
      case _ =>
    })

  val route =
    path("join") { // supports Websocket entry
      parameter('name) { name => 
        handleWebSocketMessages(websocketChatFlow(name)) // connects the web socket request to the flow, materialized for this user
      }
    } ~ path(Segment) { name => //serves static files
      get {
        getFromFile(new File(webroot, name))
      }
    }
}
