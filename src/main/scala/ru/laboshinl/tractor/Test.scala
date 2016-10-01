package ru.laboshinl.tractor

import scala.util.control.Breaks._
import java.io.File
import java.nio.ByteBuffer

import scala.collection.mutable.ListBuffer

/**
  * Created by laboshinl on 30.09.16.
  */
object Test extends App {
  val file = new File("/home/laboshinl/Downloads/smallFlows.pcap")

  val a = readFileChunk(file, 1024,1100)
  //val b = TractorTcpFlow() += a
  //splitFile(file, 1024*1024).foreach((x : (Long, Long)) => readFileChunk(file, x._1, x._2))

  def splitFile(file : File, chunkSize : Long) : ListBuffer[(Long, Long)] = {
    val chunksCount : Long = file.length / chunkSize
    var splits = new ListBuffer[(Long,Long)]()
    for (i <- 0L to chunksCount - 1) {
      val start : Long = chunkSize * i
      val stop : Long = if (i.equals(chunksCount - 1)) file.length() else chunkSize * (i + 1) - 1;
      splits += Tuple2(start, stop)
    }
    splits
  }

  def seekToFirstPacketRecord(file: RandomAccessFile): Unit = {
    var position = file.getFilePointer
    breakable {
      while (position < file.length) {
        position = file.getFilePointer
        val timestamp = file.readInt()
        file.skipBytes(4)
        val length = file.readInt32(2)
        if (length(0).equals(length(1)) && 41.to(65535).contains(length(0))) {
          file.skipBytes(length(0))
          if (0.to(600).contains(timestamp - file.readInt()))
            file.seek(position)
          break()
        } else file.seek(position + 1)
      }
    }
  }

  def readFileChunk(file: File, start : Long, stop : Long) :Unit = {
    val chunk = new RandomAccessFile(file)(ByteConverterLittleEndian)
    chunk.seek(start)
    seekToFirstPacketRecord(chunk)
    while (chunk.getFilePointer < stop ) {
      readPacket(chunk).toString
    }
    chunk.close
  }

  def computeIpHeaderLength(byte: Byte): Int = {
    var res = 0D
    0.to(3).foreach((shift: Int) => if ((byte >> shift & 1).equals(1)) res += math.pow(2, shift))
    res.toInt * 4
  }

  def ipToString(ip: Array[Byte]): String = {
    "%s.%s.%s.%s".format(ip(0) & 0xff, ip(1) & 0xff , ip(2) & 0xff, ip(3) & 0xff)
  }

  def computeFlowHash(ipSrc: Array[Byte], portSrc: Int, ipDst: Array[Byte], portDst: Int): Long = {
    val a = ByteBuffer.allocate(8).put(ipSrc).putInt(portSrc).getLong(0)
    val b = ByteBuffer.allocate(8).put(ipDst).putInt(portDst).getLong(0)

    val d = Math.abs(a - b)
    val min = a + (d & d >> 63)
    val max = b - (d & d >> 63)

    max << 64 | min
  }

  def readPacket(file: RandomAccessFile): TractorTcpPacket = {
    var packet : TractorTcpPacket = null
    /*Eth header is always 14 bytes long*/
    val EthHeaderLen = 14
    /* PCap timestamp */
    val timestamp = file.readInt * 1000000D + file.readInt
    /* Original length */
    file.skipBytes(4)
    /* Included length */
    val packetLen = file.readInt()
    /* --- End of PCap Header --- */
    val currentPosition = file.getFilePointer
    /* MAC addresses */
    val macSrc = file.readByte(6)
    val macDst = file.readByte(6)
    /* ethernet type */
    val etherType = file.readUnsignedShort()
    /* IP packet */
    if (etherType.equals(8)) {
      val ipHeaderLen = computeIpHeaderLength(file.readByte())

      file.skipBytes(1) // DSCP
      file.skipBytes(2) // Total Len
      file.skipBytes(2) // ID
      file.skipBytes(2) // Flags, Offset
      file.skipBytes(1) // TTL

      /* ip protocol */
      val protocol = file.readUnsignedByte()
      /* --- TCP  --- */
      if (protocol.equals(6)) {
        file.skipBytes(2) //check
        val ipSrc = file.readByte(4)
        val ipDst = file.readByte(4)
        val portSrc = file.readUnsignedBShort()
        val portDst = file.readUnsignedBShort()
        val seq = file.readUnsignedBInt32()
        val ack = file.readUnsignedBInt32()
        val tcpHeaderLen = file.readUnsignedByte() / 4
        /* TCP flags */
        val flagsAsByte = file.readByte()
        var tcpFlags = new Array[Short](8)
        0.to(7).foreach((x: Int) => tcpFlags(7 - x) = (flagsAsByte >> x & 1).toShort)
        /* window */
        val window = file.readShort()
        file.skipBytes(2) //Checksum
        file.skipBytes(2) //Urgent Pointer
        var tcpOptionsSize = tcpHeaderLen - 20
        /* Find sack option */
        var sackPermitted : Short = 0
        if (tcpOptionsSize > 0) {
          var currentPosition = file.getFilePointer
          val stop = currentPosition + tcpOptionsSize
          while (currentPosition < stop) {
            val kind = file.readUnsignedByte()
            currentPosition = file.getFilePointer
            if (kind.equals(4)) {
              if (file.readUnsignedByte().equals(2))
                sackPermitted = 1
              else file.seek(currentPosition)
            }
          }
        }
        val packetHeadersLen = EthHeaderLen + ipHeaderLen + tcpHeaderLen
        val payloadLen = packetLen - packetHeadersLen
        packet = TractorTcpPacket(timestamp, ipToString(ipSrc), portSrc, ipToString(ipDst), portDst,
          seq, tcpFlags, currentPosition + packetHeadersLen, payloadLen, packetLen  )
      }
    }
    file.seek(currentPosition + packetLen) //skip non ip packet
    packet
  }
}

case class TractorTcpPacket (timestamp: Double, ipSrc : String, portSrc : Int, ipDst : String, portDst : Int, seq: Long,
                             tcpFlags: Array[Short], payloadStart : Long, payloadLen : Int, length : Int) extends Serializable {
  override def toString: String = {
    s"$timestamp  $ipSrc:$portSrc -> $ipDst:$portDst  $length  %s".format(tcpFlags.toList)
  }
}

case class TractorTcpFlow(tcpFlags : Array[Int] = new Array[Int](8), payloads : collection.immutable.SortedMap[Long, (Long, Int)] = collection.immutable.SortedMap()){
  def +(p :TractorTcpPacket) : TractorTcpFlow = {
    TractorTcpFlow((this.tcpFlags,p.tcpFlags).zipped.map(_+_),
      this.payloads + (p.seq -> (p.payloadStart, p.payloadLen)))
  }
}

case class BidirectionalTcpFlow(clientFlow : TractorTcpFlow, serverFlow : TractorTcpFlow)