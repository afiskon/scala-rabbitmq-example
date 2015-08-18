package me.eax.examples.rabbitmq

import me.eax.examples.rabbitmq.pubsub._

import akka.actor._

import scala.util._
import scala.compat._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class MainActor(pubSub: PubSubClient, appName: String, topicNumber: Int) extends Actor with ActorLogging {
  private val topicName =s"dummy-topic-$topicNumber"

  case object SendMessage

  override def preStart() = {
    scheduleTimer()
  }

  override def receive: Receive = {
    case r: PubSubMessage =>
      log.info(s"Message received: $r")
    case SendMessage =>
      pubSub.subscribe(topicName, context.self) // resubscribe in case of connection loss

      val time = Platform.currentTime
      val msg = s"Message from actor $topicName @ $appName: current time is $time"
      log.info(s"Publishing message '$msg'")
      pubSub.publish(topicName, msg)
      scheduleTimer()

      /*if(Random.nextInt(10) == 3) {
        log.info("I'm lucky!")
        context.self ! PoisonPill
      }*/
    case msg =>
      log.warning(s"Unexpected message: $msg")
  }

  private def scheduleTimer(): Unit = {
    context.system.scheduler.scheduleOnce(
      sendPeriod, context.self, SendMessage
    )
  }

  private def sendPeriod = (2000 + Random.nextInt(200)).millis
}
