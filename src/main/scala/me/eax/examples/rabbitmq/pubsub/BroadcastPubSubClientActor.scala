package me.eax.examples.rabbitmq.pubsub

import akka.actor._
import com.rabbitmq.client._
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class BroadcastPubSubClientActor(
    host: String = "localhost",
    port: Int = 5672,
    login: String = "guest",
    password: String = "qwerty",
    vhost: String) extends Actor with ActorLogging {
  import PubSubClientActor._

  private case object Connect
  private case object CheckDelivery
  private case class SubscribeStatus(queueBindDone: Boolean, refs: Set[ActorRef])
  
  private val defaultSubscribeStatus = SubscribeStatus(queueBindDone = false, Set.empty)

  private val reconnectTimeoutMs = 10000L
  private val waitTimeoutMs = 10L
  private val subscriptions: mutable.Map[String, SubscribeStatus] = mutable.Map.empty

  private var chan: Channel = _
  private var consumer: QueueingConsumer = _
  private var pubsubQueueName: String = _
  private var dummyQueueName: String = _

  override def preStart(): Unit = {
    log.debug("preStart() called")
    context.self ! Connect
  }

  override def postRestart(e: Throwable): Unit = {
    log.warning(s"pubsub actor restarted, connecting in $reconnectTimeoutMs ms")
    context.system.scheduler.scheduleOnce(
      reconnectTimeoutMs.millis, context.self, Connect
    )
  }

  override def receive: Receive = {
    case Connect =>
      log.debug("Creating connection factory")

      val connFactory = new ConnectionFactory()
      connFactory.setHost(host)
      connFactory.setPort(port)
      connFactory.setUsername(login)
      connFactory.setPassword(password)
      connFactory.setVirtualHost(vhost)

      log.debug("Creating connection")
      val conn = connFactory.newConnection()

      log.debug("Creating channel")
      chan = conn.createChannel()

      log.debug("Creating consumer")
      consumer = new QueueingConsumer(chan)

      log.debug("Creating dummy queue")
      dummyQueueName = chan.queueDeclare().getQueue

      log.debug("Creating pubsub client queue")
      pubsubQueueName = chan.queueDeclare().getQueue

      log.debug("Calling basicConsume")
      chan.basicConsume(pubsubQueueName, true, consumer)

      scheduleCheckDelivery()
      context.become(connected)

    case msg =>
      log.debug(s"Ignoring message $msg - not connected to RabbitMQ server")
  }

  def connected: Receive = {
    case r: Publish =>
      val exchange = exchangeName(r.topic)
      val fullMsgJson = JArray(List(JString(r.topic), JString(r.msg)))
      val fullMsgStr = compact(fullMsgJson)

      log.debug(s"Declaring exchange $exchange")
      declareExchange(exchange)

      log.debug(s"Publishing message $fullMsgStr to exchange $exchange")
      chan.basicPublish(exchange, r.topic, null, fullMsgStr.getBytes("UTF-8"))
      sender() ! {}

    case r: Subscribe =>
      log.debug(s"Subscribing ${r.ref} to ${r.topic}")
      context.watch(r.ref)
      updateSubscribers(r.topic, s => s + r.ref)
      sender() ! {}

    case r: Unsubsribe =>
      log.debug(s"Unsubscribing ${r.ref} from ${r.topic}")
      context.unwatch(r.ref)
      updateSubscribers(r.topic, s => s - r.ref)
      sender() ! {}

    case r: Terminated =>
      log.debug(s"Actor terminated: ${r.actor}, removing all subscriptions")
      for(topic <- subscriptions.keys) {
        updateSubscribers(topic, s => s - r.actor)
      }

    case CheckDelivery =>
      // log.debug("Checking delivery")
      val nextDelivery = Option(consumer.nextDelivery(waitTimeoutMs))
      // log.debug(s"nextDelivery = $nextDelivery")
      nextDelivery match {
        case None => scheduleCheckDelivery()
        case Some(delivery) =>
          val fullMsgStr = new String(delivery.getBody, "UTF-8")
          log.debug(s"About to process json: $fullMsgStr")
          val JArray(List(JString(topic), JString(msg))) = parse(fullMsgStr)
          log.debug(s"Decoded topic: $topic, message: $msg")
          val pubSubMsg = PubSubMessage(topic, msg)
          for(ref <- subscriptions.getOrElse(topic, defaultSubscribeStatus).refs) {
            log.debug(s"Sending pubsub message to $ref")
            ref ! pubSubMsg
          }
          context.self ! CheckDelivery
      }
    case msg =>
      log.warning(s"Unexpected message: $msg")
  }

  private def updateSubscribers(topic: String, updateFunc: Set[ActorRef] => Set[ActorRef]): Unit = {
    val oldSubscribeStatus = subscriptions.getOrElse(topic, defaultSubscribeStatus)
    val newSubscribers = updateFunc(oldSubscribeStatus.refs)
    lazy val exchange = exchangeName(topic)

    if(newSubscribers.isEmpty) {
      log.debug(s"There is no more subscribers on $topic")
      subscriptions.remove(topic)
      if(oldSubscribeStatus.queueBindDone) {
        log.debug(s"Calling queueUnbind")
        declareExchange(exchange)
        chan.queueUnbind(pubsubQueueName, exchange, topic)
      }
    } else {
      if(!oldSubscribeStatus.queueBindDone) {
        log.debug("queueBindDone is false, calling queueBind")
        declareExchange(exchange)
        chan.queueBind(pubsubQueueName, exchange, topic, null)
      }
      subscriptions.update(topic, SubscribeStatus(queueBindDone = true, newSubscribers))
    }
  }

  private def declareExchange(exchange: String): Unit = {
    chan.exchangeDeclare(exchange, "topic", false, true, messageTtlArgs)
    chan.queueBind(dummyQueueName, exchange, "", null) // causes guaranteed exchange auto-delete after disconnection
  }

  private def scheduleCheckDelivery(): Unit = {
    // log.debug(s"Scheduling delivery check in $waitTimeoutMs ms")
    context.system.scheduler.scheduleOnce(
      waitTimeoutMs.millis, context.self, CheckDelivery
    )
  }

  private def exchangeName(topic: String): String = {
    s"pubsub-exchange-${Math.abs(topic.hashCode) % 3}" // use prime number!
  }

  private val messageTtlArgs: java.util.HashMap[String, AnyRef] = {
    val messageTtlMs = 60000
    val args = new java.util.HashMap[String, AnyRef]()
    args.put("x-message-ttl", new Integer(messageTtlMs))
    args
  }
}
