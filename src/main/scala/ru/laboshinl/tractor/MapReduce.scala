package ru.laboshinl.tractor

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder.{BIG_ENDIAN => BE, LITTLE_ENDIAN => LE}
import java.util.UUID

import akka.actor._
import akka.cluster.Cluster
import akka.routing.ConsistentHashingRouter.ConsistentHashable
import akka.routing._
import akka.util.ByteString
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.util.control.Breaks._


case class MapperMsg(id: UUID, key: Long, packet: TractorPacket) extends ConsistentHashable {
  override def consistentHashKey = key
}
case class MapperMsg2(id: UUID, key: Long, flow: TractorFlow) extends ConsistentHashable {
  override def consistentHashKey = key
}


case class TrackerMsg(id: UUID = null, size: Int = 0, finished: Int = 0, startedAt: Long = 0, isNew: Boolean = false) extends ConsistentHashable {
  override def consistentHashKey = id

  def +(m: TrackerMsg): TrackerMsg = new TrackerMsg(m.id, this.size, this.finished + 1, this.startedAt, false)

  def getProgress: Float = if (this.size.equals(0)) 0 else this.finished.toFloat / this.size * 100

  def isFinished: Boolean = this.finished.equals(this.size)
}


case class AggregatorMsg(id: UUID, value: mutable.Map[Long, TractorFlow]) extends ConsistentHashable {
  override def consistentHashKey = id
}

object BigDataProcessor extends App {

  var flag = false
  var bigDataFilePath = "/home/ubuntu/2013-10-10_capture-win14.pcap"

  if (args.length > 0) System.setProperty("akka.remote.netty.tcp.hostname", args(0))

  if (args.length == 2) flag = true


  val system = ActorSystem("ClusterSystem", ConfigFactory.load())
  val cluster = Cluster(system)
  val defaultBlockSize = 64 * 1024 * 1024


  if (flag) {
    Thread.sleep(10000)

    val printer = system.actorOf(Props[PrintActor], "printer")
    val reducer = system.actorOf(FromConfig.props(Props(new ReduceActor(printer))), "reducer")
    val aggregator = system.actorOf(FromConfig.props(Props(new AggregateActor(reducer, printer))), "aggregator")
    val tracker = system.actorOf(FromConfig.props(Props(new TrackActor(aggregator, printer))), "tracker")
    val mapper = system.actorOf(FromConfig.props(Props(new MapActor(tracker, aggregator))), "mapper")


    Thread.sleep(10000)

    val totalChunks = totalMessages(bigDataFilePath)
    val id = UUID.randomUUID()
    tracker ! TrackerMsg(id, totalChunks, 0, System.currentTimeMillis, isNew = true)

    for (i <- 0 to totalChunks)
      mapper ! WorkerMsg(id, bigDataFilePath, i)//} )
  }


  private def totalMessages(bigDataFilePath: String): Int = {
    val randomAccessFile = new RandomAccessFile(bigDataFilePath, "r")
    try {
      (randomAccessFile.length / defaultBlockSize).toInt
    } finally {
      randomAccessFile.close()
    }
  }
}

class AggregateActor(reducer: ActorRef, printer :ActorRef) extends Actor {
  val flows = mutable.Map[UUID, mutable.Map[Long, TractorFlow]]().withDefaultValue(mutable.Map[Long,TractorFlow]().withDefaultValue(new TractorFlow()))

  override def receive: Receive = {
    case m: MapperMsg =>
      flows(m.id)(m.key) += m.packet
    case m: MapperMsg2 =>
      flows(m.id)(m.key) ++= m.flow
    case id: UUID =>
      // printer ! "I've sent"
      reducer ! AggregatorMsg(id, flows(id))
      flows.remove(id)
  }
}

class PrintActor extends Actor {
  override def receive: Actor.Receive = {
    case m: String =>
      println(m)
  }
}

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

  val reducedResult = mutable.Map[UUID, mutable.Map[Long, TractorFlow]]().withDefaultValue(mutable.Map[Long, TractorFlow]())
  val completedAggregations = mutable.Map[UUID, Int]().withDefaultValue(0)

  override def receive: Actor.Receive = {
    case m: AggregatorMsg =>
      reducedResult(m.id) ++= m.value
      completedAggregations(m.id) += 1
      printer ! "Completed aggregations %s from %s".format(completedAggregations(m.id), sender().path.address.toString)
      if (completedAggregations(m.id) == ConfigFactory.load.getInt("akka.actor.deployment./aggregator.nr-of-instances")) {
        printer ! "TotalFlows %s".format(reducedResult(m.id).size)
        var totalSize = 0
        for ((k, v) <- reducedResult(m.id)) {
          totalSize += v.serverPacketCount
          totalSize += v.clientPacketCount

          //println("server: %s (%s) , client %s (%s) ".format(v.serverPackets.size, v.serverPacketCount, v.clientPackets.size, v.clientPacketCount))
          //          if (!v.serverPackets.isEmpty) {
          //            var httpData = ByteString.empty
          //            for ((seq, pos) <- v.serverPackets) {
          //              httpData = httpData.concat(readPacket(BigDataProcessor.bigDataFilePath, pos.startPos, pos.stopPos))
          //            }
          //            //if (httpData.utf8String.contains("jpeg"))
          //              new PrintWriter("/tmp/%s-server.raw".format(k)) {
          //                write(httpData.utf8String); close
          //              }
          //          }
          //          if (!v.clientPackets.isEmpty) {
          //            var httpData = ByteString.empty
          //            for ((seq, pos) <- v.clientPackets) {
          //              httpData = httpData.concat(readPacket(BigDataProcessor.bigDataFilePath, pos.startPos, pos.stopPos))
          //            }
          //           // if (httpData.utf8String.contains("jpeg"))
          //              new PrintWriter("/tmp/%s-client.raw".format(k)) {
          //                write(httpData.utf8String); close
          //              }
          //          }

        }
        printer ! "Total packets %s.".format(totalSize)
        reducedResult.remove(m.id)
        println("Now really done")
      }
  }
}

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
        //println("Job %s %s %% complete. ".format(m.id.toString, jobs(m.id).getProgress))
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

class MapActor(tracker: ActorRef, aggregator: ActorRef) extends Actor {
//  override def preStart(): Unit = {
//    println(self.path.toStringWithoutAddress)
//  }
  val flows = mutable.Map[Long, TractorFlow]().withDefaultValue(new TractorFlow())

//  override def receive: Receive = {
//    case m: MapperMsg =>
//      flows(m.id)(m.key) += m.packet
//    case id: UUID =>
//      reducer ! AggregatorMsg(id, flows(id))
//      flows.remove(id)
//  }

  var byteBuffer = new Array[Byte](BigDataProcessor.defaultBlockSize)

  def receive = {
    case WorkerMsg(id, bigDataFilePath, chunkIndex) =>
      val chunk = readChunk(bigDataFilePath, chunkIndex)
      readPackets(id, chunk)
      //aggregator ! AggregatorMsg(id, flows)
      for ((k,v) <- flows){
        aggregator ! MapperMsg2(id, k, v)
        flows.remove(k)
      }
      tracker ! new TrackerMsg(id)
  }


  def findFirstPacketRecord(chunk: ByteString): Int = {
    val it = chunk.iterator
    var offset = 0
    breakable {
      while (it.hasNext) {
        val itCopy = it.clone()
        try {
          val timestamp = itCopy.getInt(LE)
          itCopy.getBytes(4) // Skip timestamp microseconds
          val inclLen = itCopy.getInt(LE)
          val origLen = itCopy.getInt(LE)
          if (inclLen.equals(origLen) && 41.to(65535).contains(origLen)) {
            itCopy.getBytes(origLen) // Skip PCap record data
            val nextTimestamp = itCopy.getInt(LE)
            if (0.to(600).contains(nextTimestamp - timestamp)) {
              break()
            }
          }
        } catch {
          case e: Exception =>  // println(e)
        }
        it.next()
        offset += 1
      }
    }
    offset
  }

  def ipToString(ip: Array[Byte]): String = {
    "%s.%s.%s.%s".format(ip(0) & 0xFF, ip(1) & 0xFF, ip(2) & 0xFF, ip(3) & 0xFF)
  }

  def computeFlowHash(ipSrc: Array[Byte], portSrc: Int, ipDst: Array[Byte], portDst: Int): Long = {
    val a = ByteBuffer.allocate(8).put(ipSrc).putInt(portSrc).getLong(0)
    val b = ByteBuffer.allocate(8).put(ipDst).putInt(portDst).getLong(0)

    val d = Math.abs(a - b)
    val min = a + (d & d >> 63)
    val max = b - (d & d >> 63)

    max << 64 | min
  }

  def readPackets(id: UUID, chunk: ByteString): Unit = {
    var offset = findFirstPacketRecord(chunk)
    var packets = chunk.splitAt(offset)._2
    breakable {
      while (packets.nonEmpty) {
        val it = packets.iterator
        try {
          val ts_sec = it.getInt(LE)
          val ts_usec = it.getInt(LE)
          val incl_len = it.getInt(LE)
          val orig_len = it.getInt(LE)
          it.getBytes(6)
          it.getBytes(6)
          val etherType = it.getShort(LE)
          if ((etherType & 0xFF).equals(8)) {
            it.getBytes(8)
            it.getByte
            val proto = it.getByte & 0xFF
            it.getBytes(2) //check
            val ipSrc = it.getBytes(4)
            val ipDst = it.getBytes(4)
            if (proto.equals(6)) {
              val portSrc = it.getShort(BE) & 0xffff
              val portDst = it.getShort(BE) & 0xffff
              val seq = it.getInt(BE) & 0xffffffffl
              //58
              val ack = it.getInt(BE) & 0xffffffffl
              //62
              val tcpHeaderLen = (it.getByte & 0xFF) / 4
              val flags = it.getByte
              val conIsSet = ((flags >> 7) & 1) != 0
              val eIsSet = ((flags >> 6) & 1) != 0
              val urIsSet = ((flags >> 5) & 1) != 0
              val ackIsSet = ((flags >> 4) & 1) != 0
              val pusIsSet = ((flags >> 3) & 1) != 0
              val rstIsSet = ((flags >> 2) & 1) != 0
              val synIsSet = ((flags >> 1) & 1) != 0
              val finIsSet = (flags & 1) != 0
              val window = it.getShort(LE)
              //if (portSrc.equals(80) || portDst.equals(80)) {
              //HTTP?
//              aggregator ! MapperMsg(id, computeFlowHash(ipSrc, portSrc, ipDst, portDst),
//                new TractorPacket(ts_sec * 1000L + ts_usec / 1000, ipToString(ipSrc), portSrc, ipToString(ipDst), portDst,
//                  seq, incl_len, synIsSet, finIsSet, ackIsSet, pusIsSet, rstIsSet,
//                  window, new TractorPayload(offset + 16 + 14 + 20 + tcpHeaderLen, offset + incl_len)))
              flows(computeFlowHash(ipSrc, portSrc, ipDst, portDst)) += new TractorPacket(ts_sec * 1000L + ts_usec / 1000, ipToString(ipSrc), portSrc, ipToString(ipDst), portDst,
                seq, incl_len, synIsSet, finIsSet, ackIsSet, pusIsSet, rstIsSet,
                window, new TractorPayload(offset + 16 + 14 + 20 + tcpHeaderLen, offset + incl_len))
              //}
            }
          }
          offset = offset + 16 + incl_len
          packets = packets.splitAt(16 + incl_len)._2
        }
        catch {
          case e: Exception => //println(e)
            break()
        }
      }
    }
  }

  private def readChunk(bigDataFilePath: String, chunkIndex: Int): ByteString = {
    val randomAccessFile = new RandomAccessFile(bigDataFilePath, "r")
    try {
      val seek = chunkIndex * BigDataProcessor.defaultBlockSize
      randomAccessFile.seek(seek & 0xFFFFFFFFL)
      randomAccessFile.read(byteBuffer)
      ByteString.fromArray(byteBuffer)
    } finally {
      randomAccessFile.close()
    }
  }
}

case class BigDataMessage(bigDataFilePath: String, chunkIndex: Int, totalChunks: Int)

case class WorkerMsg(id: UUID, bigDataFilePath: String, chunkIndex: Int)