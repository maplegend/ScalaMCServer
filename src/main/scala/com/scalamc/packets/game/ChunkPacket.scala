package com.scalamc.packets.game

import com.scalamc.models.utils.VarInt
import com.scalamc.packets.{Packet, PacketDirection, PacketInfo}
import com.scalamc.utils.ByteBuffer

case class ChunkPacket(var x: Int = 0,
                       var z: Int = 0,
                       var continuous: Boolean = true,
                       var primaryMask: VarInt = VarInt(0),
                       var data: ByteBuffer = new ByteBuffer(),
                       var entLn: VarInt = VarInt(0)
                      ) extends Packet(PacketInfo(0x20.toByte, direction = PacketDirection.Client)) {
  def this(){this(0)}
}
