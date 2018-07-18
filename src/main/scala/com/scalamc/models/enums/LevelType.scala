package com.scalamc.models.enums

import com.scalamc.models.enums.PacketEnum.EnumVal
import com.scalamc.utils.BytesUtils._

object LevelType extends Enumeration {
  case class LevelTypeVal(override var value: Any) extends EnumVal

  val Default = LevelTypeVal("default")
  val Flat = LevelTypeVal("flat")
  val LargeBiomes = LevelTypeVal("largeBiomes")
  val Amplified = LevelTypeVal("amplified")
  val Default_1_1 = LevelTypeVal("default_1_1")

}
