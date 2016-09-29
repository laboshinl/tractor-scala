package ru.laboshinl.tractor

import java.io.RandomAccessFile
import java.util.UUID

import akka.actor._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.collection.mutable


/**
 * Created by laboshinl on 9/27/16.
 */
class ReduceActor(printer : ActorRef) extends Actor {
  var aggregatorCount = 5//ConfigFactory.load.getInt("akka.actor.deployment./aggregator.nr-of-instances")

//  private def readPacket(bigDataFilePath: String, startPos: Int, stopPos: Int): ByteString = {
//    val byteBuffer = new Array[Byte](stopPos - startPos)
//    val randomAccessFile = new RandomAccessFile(bigDataFilePath, "r")
//    try {
//      val seek = startPos
//      randomAccessFile.seek(seek)
//      randomAccessFile.read(byteBuffer)
//      ByteString.fromArray(byteBuffer)
//    } finally {
//      randomAccessFile.close()
//    }
//  }

  val reducedResult = mutable.Map[UUID, mutable.Map[Long, TractorFlow]]()
    .withDefaultValue(mutable.Map[Long, TractorFlow]().withDefaultValue(new TractorFlow))
  val completedAggregations = mutable.Map[UUID, Int]().withDefaultValue(0)

  override def receive: Actor.Receive = {
    case m: akka.routing.Routees =>
      //aggregatorCount = m.getRoutees.size()
    case m: AggregatorMsg =>
      reducedResult(m.id) ++= m.value
      completedAggregations(m.id) += 1
      //printer ! "Completed aggregations %s from %s".format(completedAggregations(m.id), sender().path.address.toString)
      if (completedAggregations(m.id) == aggregatorCount) {
        printer ! "Job %s TotalFlows %s".format(m.id, reducedResult(m.id).size)
        //printer ! "Job %s Total packets %s".format(m.id, totalCount(reducedResult(m.id)))
        reducedResult(m.id).foreach((x : (Long, TractorFlow)) => reducedResult(m.id).remove(x._1))
        completedAggregations(m.id) = 0
      }
  }

  def totalCount(flows : mutable.Map[Long, TractorFlow]): Long ={
    //val (key, value) = flows.head
    //value.clientPacketCount
    //array(0)
  //}
    var totalCount = 0.toLong
    for ((k, v) <- flows) {
      totalCount += v.serverPacketCount
      totalCount += v.clientPacketCount
    }
    totalCount
  }
}
