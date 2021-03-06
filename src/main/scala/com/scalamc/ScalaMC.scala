package com.scalamc

import akka.actor.Status.Success
import akka.actor.{ActorSystem, Props}

import scala.concurrent.duration._
import com.scalamc.actors._
import com.scalamc.actors.world.{World, WorldController}
import com.scalamc.actors.world.generators.FlatGenerator
import com.scalamc.models.inventory.{InventoryItem, Items}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await



/**
  * Created by MapLegend on 13.06.2017.
  */
object ScalaMC extends App{
  implicit val timeout = Timeout(5 seconds)

  implicit val actorSystem = ActorSystem()

  var protocolVersionManager = actorSystem.actorOf(Props[ProtocolVersionManager], "protocolManager")
  val a = Await.result(protocolVersionManager ? ProtocolVersionManager.LoadProtocols("protocols"), timeout.duration)

  if(a!=Success) println("ERROR while loading protocols")

  val port = Option(System.getenv("PORT")).map(_.toInt).getOrElse(25565)
  val mainActor = actorSystem.actorOf(CServer.props("localhost", port))
 // val statsActor = actorSystem.actorOf(Props[ServerStatsHandler], "stats")
  val eventController = actorSystem.actorOf(Props[EventController], "eventController")

  val worldController = actorSystem.actorOf(WorldController.props(), "worldController")//actorSystem.actorOf(World.props(actorSystem.actorOf(Props(classOf[FlatGenerator]))), "defaultWorld")
  worldController ! WorldController.CreateWorld(Some("defaultWorld"))

  val chatHandler = actorSystem.actorOf(ChatHandler.props(), "chatHandler")
  val commandHandler = actorSystem.actorOf(CommandsHandler.props(), "commandHandler")
  val entityIdManager = actorSystem.actorOf(EntityIdManager.props(), "entityIdManager")

  val pluginController = actorSystem.actorOf(PluginController.props(), "pluginController")
  pluginController ! PluginController.LoadPluginsFromDir("plugins")
}
