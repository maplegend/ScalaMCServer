package com.scalamc.models

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.io.Tcp.Write
import com.scalamc.packets.login.LoginStartPacket



object Session{
  def props(connect: ActorRef, name: String = "") = Props(
    new Session(connect, name)
  )
}

class Session(connect: ActorRef, var name: String = "") extends Actor with ActorLogging {
  override def receive = {
    case p: LoginStartPacket =>{
      name = p.name
      println(name)
      println(sender())
      //sender() ! Write()
    }
  }


}
