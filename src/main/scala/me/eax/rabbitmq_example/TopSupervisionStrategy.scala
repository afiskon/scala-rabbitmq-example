package me.eax.rabbitmq_example

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import scala.concurrent.duration._

class TopSupervisionStrategy extends SupervisorStrategyConfigurator {
  override def create(): SupervisorStrategy = OneForOneStrategy(5, 5.seconds, loggingEnabled = true) {
    case e => Restart
  }
}