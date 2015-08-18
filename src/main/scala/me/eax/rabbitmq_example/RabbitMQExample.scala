package me.eax.rabbitmq_example

import akka.actor._
import me.eax.rabbitmq_example.pubsub._
import scala.util._

object RabbitMQExample extends App {
  val host = "10.110.0.10"
  val login = "scala-client-test"
  val password = login
  val port = 5672
  val vhost = "backend-dev"

  val system = ActorSystem("system")
  val pubSubActorProps = {
    Props(new BroadcastPubSubClientActor(host, port, login, password, vhost))
      .withDispatcher("pubsub-actor-dispatcher")
  }

  val pubSubActorRef = system.actorOf(pubSubActorProps, "pubSubClientActor")
  val pubSub = PubSubClientActor.AskExt(pubSubActorRef)
  val appName = s"app-${Random.nextInt(100)}"
  for(num <- 1 to 10) {
    system.actorOf(Props(new MainActor(pubSub, appName, num)), s"mainActor-$num")
  }
  system.awaitTermination()
}
