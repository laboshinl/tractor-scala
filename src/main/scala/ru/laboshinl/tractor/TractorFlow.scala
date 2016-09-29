package ru.laboshinl.tractor

import scala.collection.immutable.TreeMap

/**
 * Created by laboshinl on 9/16/16.
 */

//* Application protocol (as reported by libprotoident)
//* ID number for the application protocol
//* Total number of packets sent from first endpoint to second endpoint
//* Total number of bytes sent from first endpoint to second endpoint
//* Total number of packets sent from second endpoint to first endpoint
//* Total number of bytes sent from second endpoint to first endpoint
//* Minimum payload size sent from first endpoint to second endpoint
//* Mean payload size sent from first endpoint to second endpoint
//* Maximum payload size sent from first endpoint to second endpoint
//* Standard deviation of payload size sent from first endpoint to
//second endpoint
//* Minimum payload size sent from second endpoint to first endpoint
//* Mean payload size sent from second endpoint to first endpoint
//* Maximum payload size sent from second endpoint to first endpoint
//* Standard deviation of payload size sent from second endpoint to
//first endpoint
//* Minimum packet interarrival time for packets sent from first
//endpoint to second endpoint
//* Mean packet interarrival time for packets sent from first
//endpoint to second endpoint
//* Maximum packet interarrival time for packets sent from first
//endpoint to second endpoint
//* Standard deviation of packet interarrival time for packets sent from
//first endpoint to second endpoint
//* Minimum packet interarrival time for packets sent from second
//endpoint to first endpoint
//* Mean packet interarrival time for packets sent from second
//endpoint to first endpoint
//* Maximum packet interarrival time for packets sent from second
//endpoint to first endpoint
//* Standard deviation of packet interarrival time for packets sent from
//second endpoint to first endpoint
//* Flow duration (in microseconds)
//* Flow start time (as a Unix timestamp)

case class TractorFlow(var finCount: Int = 0,
                       var synCount: Int = 0,
                       var ackCount: Int = 0,
                       var pushCount: Int = 0,
                       var rstCount: Int = 0,
                       var clientLength: Int = 0,
                       var serverLength: Int = 0,
                       var clientPacketCount: Long = 0,
                       var serverPacketCount: Long = 0,
                       var clientPayloadSize: Int = 0,
                       var serverPayloadSize: Int = 0,
                       var serverPort: Int = 0,
                       var clientPort: Int = 0,
                       var serverIp: String = "",
                       var clientIp: String = "",
                       val startTime: Long = Long.MaxValue,
                       val stopTime: Long = Long.MinValue,
                       var clientWindow: Int = 0,
                       var serverWindow: Int = 0,
                       var clientPackets: Map[Long, TractorPayload] = TreeMap[Long, TractorPayload]().withDefaultValue(new TractorPayload(0, 0)),
                       var serverPackets: Map[Long, TractorPayload] = TreeMap[Long, TractorPayload]().withDefaultValue(new TractorPayload(0, 0)),
                       var clientTimestamps: List[Long] = Nil,
                       var serverTimestamps: List[Long] = Nil,
                       val clientPayloadSizes: List[Long] = Nil,
                       var serverPayloadSizes: List[Long] = Nil
                        ) extends Serializable {
  def ++(f: TractorFlow): TractorFlow = {
    new TractorFlow(
      this.finCount + f.finCount,
      this.synCount + f.synCount,
      this.ackCount + f.ackCount,
      this.pushCount + f.pushCount,
      this.rstCount + f.rstCount,
      this.clientLength + f.clientLength,
      this.serverLength + f.serverLength,
      this.clientPacketCount + f.clientPacketCount,
      this.serverPacketCount + f.serverPacketCount,
      this.clientPayloadSize + f.clientPayloadSize,
      this.serverPayloadSize + f.serverPayloadSize,
      f.serverPort,
      f.clientPort,
      f.serverIp,
      f.clientIp,
      this.startTime.min(f.startTime),
      this.stopTime.max(f.stopTime),
      this.clientWindow + f.clientWindow,
      this.serverWindow + f.serverWindow,
      this.clientPackets ++ f.clientPackets,
      this.serverPackets ++ f.serverPackets,
      this.clientTimestamps ++ f.clientTimestamps,
      this.serverTimestamps ++ f.serverTimestamps,
      this.clientPayloadSizes ++ f.clientPayloadSizes,
      this.serverPayloadSizes ++ f.serverPayloadSizes

    )
  }

  def +(p: TractorPacket): TractorFlow = {
    //client
    val payloadSize = p.paylodPosition.stopPos - p.paylodPosition.startPos
    if (p.portSrc < p.portDst)
      new TractorFlow(
        this.finCount + (if (p.isFin) 1 else 0),
        this.synCount + (if (p.isSyn) 1 else 0),
        this.ackCount + (if (p.isAck) 1 else 0),
        this.pushCount + (if (p.isPush) 1 else 0),
        this.rstCount + (if (p.isRst) 1 else 0),
        this.clientLength + p.length,
        this.serverLength,
        this.clientPacketCount + 1,
        this.serverPacketCount,
        this.clientPayloadSize + payloadSize.toInt,
        this.serverPayloadSize,
        p.portSrc,
        p.portDst,
        p.ipSrc,
        p.ipDst,
        this.startTime.min(p.timestamp),
        this.stopTime.max(p.timestamp),
        this.clientWindow + p.window,
        this.serverWindow,
        if (payloadSize > 0) {
          this.clientPackets + (p.seq -> p.paylodPosition)
        }
        else {
          this.clientPackets
        },
        this.serverPackets,
        this.clientTimestamps ++ List(p.timestamp),
        this.serverTimestamps,
        this.clientPayloadSizes ++ List(payloadSize.toLong),
        this.serverPayloadSizes

      )
    else
      new TractorFlow(
        this.finCount + (if (p.isFin) 1 else 0),
        this.synCount + (if (p.isSyn) 1 else 0),
        this.ackCount + (if (p.isAck) 1 else 0),
        this.pushCount + (if (p.isPush) 1 else 0),
        this.rstCount + (if (p.isRst) 1 else 0),
        this.clientLength,
        this.serverLength + p.length,
        this.clientPacketCount,
        this.serverPacketCount + 1,
        this.clientPayloadSize,
        this.serverPayloadSize + payloadSize.toInt,
        p.portDst,
        p.portSrc,
        p.ipDst,
        p.ipSrc,
        this.startTime.min(p.timestamp),
        this.stopTime.max(p.timestamp),
        this.clientWindow,
        this.serverWindow + p.window,
        this.clientPackets,
        if (payloadSize > 0) {
          this.serverPackets + (p.seq -> p.paylodPosition)
        }
        else {
          this.serverPackets
        },
        this.clientTimestamps,
        this.serverTimestamps ++ List(p.timestamp),
        this.clientPayloadSizes,
        this.serverPayloadSizes ++ List(payloadSize.toLong)

      )
  }

  private def mean(xs: List[Long]): Long = xs match {
    case Nil => 0
    case ys => ys.sum / ys.size
  }

  private def stddev(xs: List[Long], avg: Long): Long = xs match {
    case Nil => 0
    case ys => math.sqrt((0.0 /: ys) {
   (a,e) => a + math.pow(e - avg, 2.0)
  } / xs.size).toLong
  }

  override def toString(): String = {
    val intTimeServer = this.serverTimestamps.sorted.sliding(2).collect { case List(a, b) => b - a }.toList
    val meanTimeServer = mean(intTimeServer)
    val intTimeClient = this.clientTimestamps.sorted.sliding(2).collect { case List(a, b) => b - a }.toList
    val meanTimeClient = mean(intTimeClient)
    val meanPayloadClient = mean(this.clientPayloadSizes)
    val meanPayloadServer = mean(this.serverPayloadSizes)
    "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s".format(
      //    "%s, %7s,%7s,%7s,%7s,%7s,%9.2f,%7s,%9.2f,%7s,%9.2f,%6s,%9.2f,%7s,%9.2f,%7s,%9.2f,%7s,%9.2f,%7s,%9.2f,%6s,%7s".format(
      6,
      //* Total number of packets sent from first endpoint to second endpoint
      this.clientPacketCount,
      //* Total number of bytes sent from first endpoint to second endpoint
      this.clientLength,
      //* Total number of packets sent from second endpoint to first endpoint
      this.serverPacketCount,
      //* Total number of bytes sent from second endpoint to first endpoint
      this.serverLength,
      //* Minimum payload size sent from first endpoint to second endpoint
      this.clientPayloadSizes.reduceLeftOption(_ min _).getOrElse(0L),
      //* Mean payload size sent from first endpoint to second endpoint
      meanPayloadClient,
      //* Maximum payload size sent from first endpoint to second endpoint
      this.clientPayloadSizes.reduceLeftOption(_ max _).getOrElse(0L),
      //* Standard deviation of payload size sent from first endpoint to
      //second endpoint
      stddev(clientPayloadSizes, meanPayloadClient),
      //* Minimum payload size sent from second endpoint to first endpoint
      this.serverPayloadSizes.reduceLeftOption(_ min _).getOrElse(0L),
      //* Mean payload size sent from second endpoint to first endpoint
      meanPayloadServer,
      //* Maximum payload size sent from second endpoint to first endpoint
      this.serverPayloadSizes.reduceLeftOption(_ max _).getOrElse(0L),
      //* Standard deviation of payload size sent from second endpoint to
      //first endpoint
      stddev(serverPayloadSizes, meanPayloadServer),
      //* Minimum packet interarrival time for packets sent from first
      //endpoint to second endpoint
      intTimeClient.reduceLeftOption(_ min _).getOrElse(0L) * 1000,
    //****
      //* Mean packet interarrival time for packets sent from first
      meanTimeClient * 1000,
      //endpoint to second endpoint
      //* Maximum packet interarrival time for packets sent from first
      //endpoint to second endpoint
      intTimeClient.reduceLeftOption(_ max _).getOrElse(0L) * 1000,
      //* Standard deviation of packet interarrival time for packets sent from
      //first endpoint to second endpoint
      stddev(intTimeClient, meanTimeClient) * 1000 ,
    //****
      //* Minimum packet interarrival time for packets sent from second
      //endpoint to first endpoint
      intTimeServer.reduceLeftOption(_ min _).getOrElse(0L) * 1000,
      //* Mean packet interarrival time for packets sent from second
      //endpoint to first endpoint
      meanTimeServer * 1000,
      //this.serverTimestamps.sorted.sliding(2).collect{case List(a,b) => b-a}.toList.reduceLeft(_ min _),
      //* Maximum packet interarrival time for packets sent from second
      //endpoint to first endpoint
      intTimeServer.reduceLeftOption(_ max _).getOrElse(0L) * 1000,
      //* Standard deviation of packet interarrival time for packets sent from
      //second endpoint to first endpoint
      stddev(intTimeServer, meanTimeServer)*1000,
      //* Flow duration (in microseconds)
      (this.stopTime - this.startTime) * 1000,
      //* Flow start time (as a Unix timestamp)
      this.startTime / 1000)

  }
}
