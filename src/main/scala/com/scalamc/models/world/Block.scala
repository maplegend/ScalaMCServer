package com.scalamc.models.world

import com.scalamc.models.inventory.InventoryItem
import com.scalamc.models.utils.VarInt

object Block{
  implicit def blockToVarInt(block: Block) = VarInt(block.id << 4 | (block.metadata & 15))
}

case class Block(override val id: Int, override val metadata: Int) extends InventoryItem {

}
