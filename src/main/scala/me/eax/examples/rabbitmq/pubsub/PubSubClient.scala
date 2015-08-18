package me.eax.examples.rabbitmq.pubsub

import akka.actor.ActorRef

import scala.concurrent._

case class PubSubMessage(topic: String, message: String)

trait PubSubClient {
  def publish(topic: String, msg: String): Future[Unit]
  def subscribe(topic: String, ref: ActorRef): Future[Unit]
  def unsubscribe(topic: String, ref: ActorRef): Future[Unit]
}
