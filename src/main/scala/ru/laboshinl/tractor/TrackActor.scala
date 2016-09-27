package ru.laboshinl.tractor
import java.util.UUID

import akka.actor._
import akka.routing._

/**
 * Created by laboshinl on 9/27/16.
 */

class TrackActor(aggregator: ActorRef, printer:ActorRef) extends Actor {
  val jobs = collection.mutable.Map[UUID, TrackerMsg]().withDefaultValue(TrackerMsg())

  override def receive: Actor.Receive = {
    case m: TrackerMsg =>
      if (m.isNew) {
        jobs(m.id) = m
        printer ! "New job %s. ".format(m.id.toString, self.path.toStringWithoutAddress)
      }
      else {
        jobs(m.id) += m
      }
      if (jobs(m.id).isFinished) {
        aggregator ! new Broadcast(m.id)
        printer ! "Job %s finished in %s ms. ".format(m.id.toString, System.currentTimeMillis - jobs(m.id).startedAt)
        jobs.remove(m.id)
      }
      else{
        printer ! "Job %s %.2f %% complete. Came from (%s)".format(m.id.toString, jobs(m.id).getProgress, /*self.path.toStringWithoutAddress,*/ sender().path.address.toString)
      }
  }
}
