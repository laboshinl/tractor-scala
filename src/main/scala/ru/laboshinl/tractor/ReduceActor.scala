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

  private def readPacket(bigDataFilePath: String, startPos: Int, stopPos: Int): ByteString = {
    val byteBuffer = new Array[Byte](stopPos - startPos)
    val randomAccessFile = new RandomAccessFile(bigDataFilePath, "r")
    try {
      val seek = startPos
      randomAccessFile.seek(seek)
      randomAccessFile.read(byteBuffer)
      ByteString.fromArray(byteBuffer)
    } finally {
      randomAccessFile.close()
    }
  }

  val reducedResult = mutable.Map[UUID, mutable.Map[Long, TractorFlow]]()
    .withDefaultValue(mutable.Map[Long, TractorFlow]())
  val completedAggregations = mutable.Map[UUID, Int]().withDefaultValue(0)

  override def receive: Actor.Receive = {
    case m: AggregatorMsg =>
      reducedResult(m.id) ++= m.value
      completedAggregations(m.id) += 1
      //printer ! "Completed aggregations %s from %s".format(completedAggregations(m.id), sender().path.address.toString)
      if (completedAggregations(m.id) == ConfigFactory.load
        .getInt("akka.actor.deployment./aggregator.nr-of-instances")) {
        printer ! "Job %s TotalFlows %s".format(m.id, reducedResult(m.id).size)
        var totalSize = 0
        for ((k, v) <- reducedResult(m.id)) {
          totalSize += v.serverPacketCount
          totalSize += v.clientPacketCount
        }
        //printer ! "Job %s Total packets %s.".format(m.id, totalSize)
        reducedResult.remove(m.id)
        completedAggregations.remove(m.id)
      }
  }
}
