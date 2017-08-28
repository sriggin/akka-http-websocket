package server

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import scala.collection.mutable

// defines chat actor protocol
object Chat {
  sealed trait ChatEvent
  case class UserJoined(user: String, subscriber: ActorRef) extends ChatEvent
  case class UserLeft(user: String) extends ChatEvent
  case class ReceivedMessage(user: String, message: String) extends ChatEvent
}

// defines application-level chat protocol
object Protocol {
  sealed trait ChatEvent
  case class Joined(user: String, allUsers: Set[String]) extends ChatEvent
  case class Left(user: String, allUsers: Set[String]) extends ChatEvent
  case class ChatMessage(user: String, message: String) extends ChatEvent
}

// connects user-materialized flows with chat actor ref
class Chat(chatActor: ActorRef)(implicit system: ActorSystem) {

  // wires actor representation of a single user into the system flow
  def flow(sender: String): Flow[String, Protocol.ChatEvent, Any] = {
    val in = Flow[String] // messages sent from the websocket are written into this flow, and handled as Chat actor messages
      .map(Chat.ReceivedMessage(sender, _))
      .to(Sink.actorRef(chatActor, Chat.UserLeft(sender)))

    // messages published from the Chat actor are published to this flow, sent to each user's socket's actor
    val out = Source.actorRef[Protocol.ChatEvent](10, OverflowStrategy.fail)
      .mapMaterializedValue { subscriber => chatActor ! Chat.UserJoined(sender, subscriber) }

    // constructs a flow representing both directions of websocket communication
    Flow.fromSinkAndSource(in, out)
  }
}

// handles the global "chat room"
class ChatActor extends Actor {

  private val subscribers = mutable.Map.empty[String, ActorRef]

  def receive: Receive = {
    case Chat.UserJoined(user, subscriber) =>
      context.watch(subscriber)
      subscribers += (user -> subscriber)
      publish(Protocol.Joined(user, members))

    case Chat.UserLeft(user) =>
      subscribers.get(user) foreach { subscriber =>
        subscriber ! Status.Success(())
        subscribers -= user
        publish(Protocol.Left(user, members))
      }

    case Chat.ReceivedMessage(user, message) =>
      publish(Protocol.ChatMessage(user, message))

    // this comes from `context.watch(subscriber)` in the `UserJoined` case
    case Terminated(subscriber) => // this should've come in as a UserLeft
      subscribers.find { case entry => entry._2 == subscriber } foreach { entry =>
        subscribers -= entry._1
      }

    case other =>
      println("Unexpected Message Received: " + other)
  }

  private def publish(event: Protocol.ChatEvent): Unit = subscribers.values.foreach(_ ! event)

  private def members = subscribers.keys.toSet

}
