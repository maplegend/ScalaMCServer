package com.scalamc.packets.game

import com.scalamc.models.utils.VarInt
import com.scalamc.packets.{Packet, PacketDirection, PacketInfo}

case class TeleportConfirmPacket(var id: VarInt = VarInt(0))
  extends Packet(PacketInfo(0x00.toByte, direction = PacketDirection.Server)){

  def this(){this(VarInt(0))}
}
