package ru.laboshinl.tractor

import java.util.UUID

import akka.actor.{Actor, ActorRef, Terminated}

import scala.collection.mutable

class FlowControlActor(target: ActorRef, windowSize: Int = 1, forwardTo : ActorRef ) extends Actor {

  val queue = mutable.Map[UUID, mutable.Queue[Any]]().withDefaultValue(mutable.Queue.empty[Any])
  var pending = mutable.Map[UUID, Int]().withDefaultValue(0)
  var flag = mutable.Map[UUID, Boolean]().withDefaultValue(false)

  override def preStart(): Unit = context.watch(target)



  def receive = {
    case Acknowledged(id) =>
      if (pending(id) > 0) pending(id) -= 1
      if (queue(id).nonEmpty) {
        target ! queue(id).dequeue()
        pending(id) += 1
      }
      if (pending(id) == 0 && queue(id).isEmpty && flag(id)) {
        forwardTo ! TrackerMsg(id)
      }
    case Terminated(targ) => context.stop(self)
    case AllSent(id) =>
       flag(id) = true
    case m: MapperMsg =>
      if (pending(m.id) == windowSize) {
        queue(m.id) enqueue m
      } else {
        pending(m.id) += 1
        target ! m
      }
  }
}
