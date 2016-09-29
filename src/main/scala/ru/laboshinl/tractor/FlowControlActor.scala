package ru.laboshinl.tractor

import java.util.UUID

import akka.actor.{Actor, ActorRef, Terminated}

import scala.collection.mutable

class FlowControlActor(target: ActorRef, windowSize: Int = 10000, tracker: ActorRef) extends Actor {

  val queue = mutable.Queue.empty[Any]
  var pending = 0

  override def preStart(): Unit = {
    context.watch(target)
    context.watch(tracker)
  }

  def receive = {
    case AllSent(id) =>
      tracker ! TrackerMsg(id)
      println(s"to tracker $id")

    case Acknowledged =>
      if (pending > 0) pending -= 1
      if (queue.nonEmpty) {
        target ! queue.dequeue()
        pending += 1
      }

    case msg =>
      if (pending == windowSize) {
        queue enqueue msg
      } else {
        pending += 1
        target ! msg
      }

      //println(s"to tracker $id")
    //case Terminated(targ) => context.stop(self)
  }
}