package ru.laboshinl.tractor

import java.util.UUID

import akka.actor.{Actor, ActorRef}

import scala.collection.mutable

/**
 * Created by laboshinl on 9/27/16.
 */

class AggregateActor(reducer: ActorRef, printer: ActorRef) extends Actor {
  val flows = mutable.Map[UUID,mutable.Map[Long, TractorFlow]]()
    .withDefaultValue(mutable.Map[Long,TractorFlow]()
      .withDefaultValue(new TractorFlow()))

  override def receive: Receive = {
    case m: MapperMsg =>
      flows(m.id)(m.key) ++= m.flow
      sender ! Acknowledged
    case id: UUID =>
      reducer ! AggregatorMsg(id, flows(id))
      flows.remove(id)
  }
}
