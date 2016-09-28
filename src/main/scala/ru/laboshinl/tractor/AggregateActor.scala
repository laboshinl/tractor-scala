package ru.laboshinl.tractor

import java.util.UUID
//import java.util.concurrent.ConcurrentHashMap

import akka.actor.{Actor, ActorRef}

//import scala.collection.mutable
import scala.collection._
import scala.collection.convert.decorateAsScala._
import java.util.concurrent.ConcurrentHashMap
/**
 * Created by laboshinl on 9/27/16.
 */

class AggregateActor(reducer: ActorRef, printer: ActorRef) extends Actor {
 // val flows : Map[UUID, Map[Long,TractorFlow]] = ConcurrentHashMap[UUID, ConcurrentHashMap[Long, TractorFlow]]
  val flows: concurrent.Map[UUID, concurrent.Map[Long, TractorFlow]] = new ConcurrentHashMap[UUID, concurrent.Map[Long,TractorFlow]].asScala
  //val flows = new ConcurrentHashMap[UUID, mutable.Map[Long,TractorFlow]].asScala.withDefaultValue(new ConcurrentHashMap[Long,TractorFlow].asScala.withDefaultValue(TractorFlow()))
//  val flows = new mutable.HashMap[UUID,mutable.Map[Long, TractorFlow]] with mutable.SynchronizedMap[UUID, mutable.Map[Long, TractorFlow]]

//    .withDefaultValue(new mutable.HashMap[Long,TractorFlow] with mutable.SynchronizedMap[Long, TractorFlow] .withDefaultValue(new TractorFlow))

  override def receive: Receive = {
    case MapperMsg(jobId, flowId, flow ) =>
//      if(flows(jobId) isDefinedAt(flowId)){
//        flows(jobId)(flowId) ++= flow
//      }
//      else
//      flows(jobId)(flowId) = flow
      if (! (flows isDefinedAt(jobId))){
        flows.put(jobId,new ConcurrentHashMap[Long, TractorFlow].asScala)
        //println("New Job")
      }
      if (flows(jobId) isDefinedAt(flowId)){
        flows(jobId)(flowId) ++= flow
         // println("Old Flow")
      }
      else {
        flows(jobId).put(flowId, flow)
        //println("New flow")
      }



       // flows(jobId)(flowId) ++= flow

      sender ! Acknowledged(jobId)
    case jobId : UUID =>
      println(jobId)
      println(flows(jobId).size)
//      val (key, value) = flows(jobId).head
//      println("(%s) %s".format(self.path.toStringWithoutAddress, value.clientPacketCount))
      //println("Immm %s %s".format(flows(id).size, sender().path.toStringWithoutAddress))
      var totalCount = 0.toLong
      for ((k, v) <- flows(jobId)) {
        totalCount += v.serverPacketCount
        totalCount += v.clientPacketCount
      }
      println("aggregator %s".format(totalCount))
      reducer ! AggregatorMsg(jobId, flows(jobId).clone())
      //flows(jobId) = mutable.Map.empty[Long,TractorFlow]
      flows(jobId).foreach((x : (Long,TractorFlow)) => flows(jobId).remove(x._1))
      flows(jobId).clear()
  }
}
