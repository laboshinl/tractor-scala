package ru.laboshinl.tractor

/**
 * Created by laboshinl on 9/16/16.
 */
class TractorPacket (val timestamp: Long, val ipSrc : String, val portSrc : Int, val ipDst : String, val portDst : Int, val seq: Long, val length : Int,
                    val isSyn : Boolean, val isFin : Boolean, val isAck : Boolean, val isPush : Boolean, val isRst : Boolean, val window : Short, val paylodPosition : TractorPayload) extends Serializable