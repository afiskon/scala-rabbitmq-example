package me.eax.rabbitmq_example.pubsub

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._

object PubSubClientActor {

  case class Publish(topic: String, msg: String)
  case class Subscribe(topic: String, ref: ActorRef)
  case class Unsubsribe(topic: String, ref: ActorRef)

  case class AskExt(ref: ActorRef) extends PubSubClient {
    implicit private val timeout = Timeout(5.seconds)

    def publish(topic: String, msg: String): Future[Unit] = (ref ? Publish(topic, msg)).mapTo[Unit]
    def subscribe(topic: String, r: ActorRef): Future[Unit] = (ref ? Subscribe(topic, r)).mapTo[Unit]
    def unsubscribe(topic: String, r: ActorRef): Future[Unit] = (ref ? Unsubsribe(topic, r)).mapTo[Unit]
  }
}
