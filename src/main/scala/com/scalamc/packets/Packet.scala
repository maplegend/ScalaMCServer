package com.scalamc.packets

import java.io.{ByteArrayInputStream, DataOutput, DataOutputStream, File}
import java.util.UUID

import akka.util.ByteString
import com.scalamc.models.Position
import com.scalamc.models.utils.VarLong
import com.scalamc.models.enums.PacketEnum
import com.scalamc.models.enums.PacketEnum.EnumVal
import com.scalamc.models.metadata.{EntityMetadata, EntityMetadataRaw}
import com.scalamc.models.utils.{VarInt, VarLong}
import com.scalamc.utils.{ByteBuffer, ByteStack, ClassFieldsCache}
import com.scalamc.utils.BytesUtils._
import com.xorinc.scalanbt.tags._
import com.xorinc.scalanbt.io._
import org.clapper.classutil.{ClassFinder, ClassInfo}

import scala.util.Try
import scala.reflect.runtime.{universe, currentMirror => rm}
import org.reflections.Reflections

import scala.annotation.ClassfileAnnotation
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.reflect.api.JavaUniverse


object PacketState extends Enumeration {
  val Login, Playing, Status = Value
}
object PacketDirection extends Enumeration {
  val Server, Client = Value
}

case class PacketInfo(var ids: Map[Int, Byte],
                      state: PacketState.Value = PacketState.Playing,
                      direction: PacketDirection.Value)


object Packet{

  def newInstance(className: String): Option[Packet] = Try {
    val m = universe.runtimeMirror(getClass.getClassLoader)
    if (className.endsWith("$")) {
      m.reflectModule(m.staticModule(className.init)).instance.asInstanceOf[Packet]
    } else {
      Class.forName(className).newInstance().asInstanceOf[Packet]
    }
  }.toOption

  lazy val packets = {

    val reflections = new Reflections("com.scalamc.packets")
    val subclasses = reflections.getSubTypesOf(classOf[Packet])

    subclasses.asScala map (p=> (p.newInstance().packetInfo, p))
  }

  def getPacket(id: Byte, protocolId: Int, state: PacketState.Value = PacketState.Playing, direction: PacketDirection.Value = PacketDirection.Server) =
    packets.find((p) => (p._1.ids(protocolId) == id || p._1.ids(-1) == id) && p._1.state == state && p._1.direction == direction).get._2


  def fromByteBuffer(buf: ByteBuffer, state: PacketState.Value = PacketState.Playing)(implicit protocolId: Int): Packet ={
    val packet = getPacket(buf(0),protocolId, state).newInstance()
    buf.remove(0)
    packet.read(buf)
    packet
  }

  implicit def pack2ArrayBuff(pack: Packet)(implicit protocolId: Int): ByteBuffer = pack.write()

  implicit def packet2ByteStrign(pack: Packet)(implicit protocolId: Int) = ByteString(pack.toArray)
}



abstract class Packet(val packetInfo: PacketInfo) {
  import scala.reflect.runtime.universe._

  implicit class TermSymbolExtend(ts: TermSymbol)(implicit instanceMirror: InstanceMirror){
    implicit def :=(any: Any) = instanceMirror.reflectField(ts).set(any)
  }

  val instanceMirror: universe.InstanceMirror = rm.reflect(this)

  //Set default id
  packetInfo.ids = packetInfo.ids.withDefault(_ => 0xFF.toByte)

  //val fields: List[(Any, universe.TermSymbol)] =

  def read(byteBuffer: ByteBuffer): Unit ={
    val stack = new ByteStack(byteBuffer.toArray)
    readFields(stack, this)
    //println("read end ",javax.xml.bind.DatatypeConverter.printHexBinary(stack.toArray))
  }

  def readFields(stack: ByteStack, obj: Any): Unit ={
    val classFields: List[(Any, universe.TermSymbol)] = ClassFieldsCache.getFields(obj)
    implicit val instMirror: universe.InstanceMirror = rm.reflect(obj)

    for((name, field) <- classFields){
      readField(stack, name, field)
    }
  }

  def readField(stack: ByteStack, name: Any, field: universe.TermSymbol)(implicit instMirror: universe.InstanceMirror): Unit ={
    name match {
      case _: String =>
        val len = stack.popVarInt()
        field := ByteString(stack.popWith(len).toArray).decodeString("utf-8")
      case _: VarInt =>
        field := VarInt(stack.popVarInt())
      case _: Double =>
        field := stack.popDouble()
      case _: Float =>
        field := stack.popFloat()
      case _: Boolean =>
        field := (if (stack.pop()==0) false else true)
      case _: Long =>
        field := stack.popLong()
      case _: Byte =>
        field := stack.pop()
      case _: Array[Byte] =>
        field := stack.popWith(stack.popVarInt()).toArray
      case _: Short =>
        field := stack.popShort()
      case _: Int =>
        field := stack.popInt()
      case _: com.scalamc.models.Position =>
        val value = stack.popLong()
        val x = value >> 38
        // signed
        val y = value >> 26 & 0xfff
        // unsigned
        // this shifting madness is used to preserve sign
        val z = value << 38 >> 38
        field := com.scalamc.models.Position(x.toInt, y.toInt, z.toInt)


        //println(e.enum.values.find(v => v.id == stack.pop().toInt))
        //println(e.enum.values.mkString)
        //field := e.getClass.getConstructors()(0).newInstance(stack.pop())
      //  println("read enum")
      //  readField(stack, e.value, rm.reflect(e.value).symbol.asTerm)
      case _: TagCompound =>
        field := readNBT(new ByteArrayInputStream(stack.toArray))._2
      case op: Option[Any] =>
        if(stack.nonEmpty) readFields(stack, op.get)
      case other =>
        readFields(stack, other)
    }
  }

  def write()(implicit protocolId: Int): ByteBuffer ={
    var buff: ByteBuffer = new ByteBuffer()

    var id = packetInfo.ids(protocolId)
    buff += (if(id==0xFF.toByte) packetInfo.ids(-1) else id)

    buff += writeFields(ClassFieldsCache.getFields(this), instanceMirror, isMetadata = false).toArray

    var lenBuff = new ByteBuffer()
    lenBuff.writeVarInt(buff.length)

    lenBuff+buff.toArray
  }

  def writeFieldValue(buff: ByteBuffer, ind: Int, value: Any, isMetadata: Boolean): Unit ={
    value match {
      case str: String =>
        if(isMetadata) buff.writeVarInt(3)
        buff += str
      case option: Option[Any] => if(option.isEmpty) buff += 0x00.toByte else writeFieldValue(buff, ind, option.get, isMetadata)
      case int: Int =>
        buff += int
      case varInt: VarInt =>
        if(isMetadata) buff.writeVarInt(1)
        buff.writeVarInt(varInt.int)
      case enum: EnumVal =>
        writeFieldValue(buff, ind, enum.value, isMetadata = false)
      case bool: Boolean =>
        if(isMetadata) buff.writeVarInt(6)
        buff += (if(bool) 1.toByte else 0.toByte)
      case byte: Byte =>
        if(isMetadata) buff.writeVarInt(0)
        buff += byte
      case pos: com.scalamc.models.Position => buff += pos.toLong
      case long: VarLong =>
      case long: Long => buff += long
      case double: Double => buff += double
      case float: Float =>
        if(isMetadata) buff.writeVarInt(2)
        buff += float
      case short: Short =>
        buff += short
      case buf: ByteBuffer =>
        buff.writeVarInt(buf.size)
        buff += buf.toArray
      case arr: Array[Byte] =>
        buff += arr
      case uuid: UUID =>
        buff += uuid.getMostSignificantBits
        buff += uuid.getLeastSignificantBits
      case arBuf: ArrayBuffer[Any] =>
        buff.writeVarInt(arBuf.size)
        if(arBuf.nonEmpty) {
          if(rm.reflect(arBuf(0)).symbol.annotations.exists(_.tree.tpe.typeSymbol == typeOf[NotParsable].typeSymbol))
            arBuf.foreach(el => writeFieldValue(buff, ind, el, isMetadata = false))
          else
            arBuf.foreach(el => buff += writeFields(ClassFieldsCache.getFields(el), rm.reflect(el), isMetadata = false).toArray)
        }
      case tag: TagCompound =>
        import java.io.ByteArrayOutputStream
        import java.io.DataOutput
        val boas = new ByteArrayOutputStream()
        val out = new DataOutputStream(boas)
        writeNBT(out)("", tag)
        buff += boas.toByteArray
      case other =>
        buff += writeFields(ClassFieldsCache.getFields(other), rm.reflect(other), other.getClass == classOf[EntityMetadataRaw]).toArray
    }
  }

  def writeField(buff: ByteBuffer, ind: Int, t: Any, acc: scala.reflect.runtime.universe.TermSymbol, im: InstanceMirror, isMetadata: Boolean): Unit ={
    if(isMetadata) buff += ind.toByte
    writeFieldValue(buff, ind, im.reflectField(acc).get, isMetadata)

  }

  def writeFields(fields: List[(Any, scala.reflect.runtime.universe.TermSymbol)], im: InstanceMirror, isMetadata: Boolean): ByteBuffer={
    var buff: ByteBuffer = new ByteBuffer()

    //if(isMetadata) buff.writeVarInt(fields.size)
    for(((t, acc), ind) <- fields.zipWithIndex){
      writeField(buff, ind, t, acc, im, isMetadata)
      //println(javax.xml.bind.DatatypeConverter.printHexBinary(buff.toArray))
    }

    if(isMetadata) buff += 0xFF.toByte

    buff
  }

}
