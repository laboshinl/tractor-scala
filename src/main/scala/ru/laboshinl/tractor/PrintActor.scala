package ru.laboshinl.tractor

import akka.actor.Actor

/**
 * Created by laboshinl on 9/27/16.
 */
class PrintActor extends Actor {
  override def receive: Actor.Receive = {
    case m: String =>
      println(m)
  }
}
