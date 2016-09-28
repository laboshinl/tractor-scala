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
                  var serverPacketCount: Long = 0,
                  var clientPacketCount: Long = 0,
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
                  var serverPackets: Map[Long, TractorPayload] = TreeMap[Long, TractorPayload]().withDefaultValue(new TractorPayload(0, 0))
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
      this.serverPacketCount + f.serverPacketCount,
      this.clientPacketCount + f.clientPacketCount,
      this.clientPayloadSize + f.clientPayloadSize,
      this.serverPayloadSize + f.serverPayloadSize,
      f.serverPort,
      f.clientPort,
      f.serverIp,
      f.clientIp,
      if (f.startTime < this.startTime)
        f.startTime
      else
        this.startTime,
      if (f.stopTime > this.stopTime)
        f.stopTime
      else
        this.stopTime,
      this.clientWindow + f.clientWindow,
      this.serverWindow + f.serverWindow,
      this.clientPackets ++ f.clientPackets,
      this.serverPackets ++ f.serverPackets

    )
  }

  def +(p: TractorPacket): TractorFlow = {
    //client
    if (p.portSrc > p.portDst)
      new TractorFlow(
        this.finCount + (if (p.isFin) 1 else 0),
        this.synCount + (if (p.isSyn) 1 else 0),
        this.ackCount + (if (p.isAck) 1 else 0),
        this.pushCount + (if (p.isPush) 1 else 0),
        this.rstCount + (if (p.isRst) 1 else 0),
        this.clientLength + p.length,
        this.serverLength,
        this.serverPacketCount,
        this.clientPacketCount + 1,
        this.clientPayloadSize + p.paylodPosition.stopPos - p.paylodPosition.startPos,
        this.serverPayloadSize,
        p.portSrc,
        p.portDst,
        p.ipSrc,
        p.ipDst,
        if (p.timestamp < this.startTime)
          p.timestamp
        else
          this.startTime,
        if (p.timestamp > this.stopTime)
          p.timestamp
        else
          this.stopTime,
        this.clientWindow + p.window,
        this.serverWindow,
        if (p.paylodPosition.stopPos - p.paylodPosition.startPos > 0) {
          this.clientPackets + (p.seq -> p.paylodPosition)
        }
        else {
          this.clientPackets
        },
        this.serverPackets
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
        this.serverPacketCount + 1,
        this.clientPacketCount,
        this.clientPayloadSize,
        this.serverPayloadSize + p.paylodPosition.stopPos - p.paylodPosition.startPos,
        p.portDst,
        p.portSrc,
        p.ipDst,
        p.ipSrc,
        if (p.timestamp < this.startTime)
          p.timestamp
        else
          this.startTime,
        if (p.timestamp > this.stopTime)
          p.timestamp
        else
          this.stopTime,
        this.clientWindow,
        this.serverWindow + p.window,
        this.clientPackets,
        if (p.paylodPosition.stopPos - p.paylodPosition.startPos > 0) {
          this.serverPackets + (p.seq -> p.paylodPosition)
        }
        else {
          this.serverPackets
        }
      )
  }
}
