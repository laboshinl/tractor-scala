package ru.laboshinl.tractor

import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.UUID

import akka.actor.{Props, Actor, ActorRef}
import akka.util.ByteString

import scala.collection.mutable
import java.nio.ByteOrder.{BIG_ENDIAN => BE, LITTLE_ENDIAN => LE}
import scala.util.control.Breaks._

/**
 * Created by laboshinl on 9/27/16.
 */
class MapActor(tracker: ActorRef, aggregator: ActorRef) extends Actor {

  val aggregatorController =  context.system.actorOf(Props(new FlowControlActor(aggregator)))
  val trackerController =  context.system.actorOf(Props(new FlowControlActor(tracker)))

  val flows = mutable.Map[UUID,mutable.Map[Long, TractorFlow]]()
    .withDefaultValue(mutable.Map[Long,TractorFlow]()
      .withDefaultValue(new TractorFlow))

  def receive = {
    case WorkerMsg(id, bigDataFilePath, chunkIndex) =>
      val chunk = readChunk(bigDataFilePath, chunkIndex)
      readPackets(id, chunk)
      for ((k,v) <- flows(id)){
        aggregatorController ! MapperMsg(id, k, v)
        flows(id).remove(k)
      }
      trackerController ! TrackerMsg(id)
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
          case e: Exception =>  println(e)
            break()
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
      while (! packets.isEmpty) {
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

              flows(id)(computeFlowHash(ipSrc, portSrc, ipDst, portDst)) += new TractorPacket(ts_sec * 1000L + ts_usec / 1000, ipToString(ipSrc), portSrc, ipToString(ipDst), portDst,
                seq, incl_len, synIsSet, finIsSet, ackIsSet, pusIsSet, rstIsSet,
                window, new TractorPayload(offset + 16 + 14 + 20 + tcpHeaderLen, offset + incl_len))

            }
          }
          offset = offset + 16 + incl_len
          packets = packets.splitAt(16 + incl_len)._2
        }
        catch {
          case e: Exception => println(e)
            break()
        }
      }
    }
  }

  private def readChunk(bigDataFilePath: String, chunkIndex: Int): ByteString = {
    val randomAccessFile = new RandomAccessFile(bigDataFilePath, "r")
    var byteBuffer = new Array[Byte](ApplicationMain.defaultBlockSize)
    try {
      val seek = chunkIndex * ApplicationMain.defaultBlockSize
      randomAccessFile.seek(seek & 0xFFFFFFFFL)
      randomAccessFile.read(byteBuffer)
      ByteString.fromArray(byteBuffer)
    } finally {
      randomAccessFile.close()
    }
  }
}
