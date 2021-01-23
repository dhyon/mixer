package com.dhyon.jobcoin.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import com.dhyon.jobcoin.JobcoinClient

/* Top-level root actor */
object GuardianActor {

  sealed trait Command

  case class MixOnAddress(depositAddr: String, finalAddrs: Seq[String], client: JobcoinClient) extends Command

  //TODO: command to shutdown a SentinelActor

  def apply: Behavior[Command] =
    Behaviors.setup[Command] { context: ActorContext[Command] =>
      new GuardianActor(context).start
    }
}

class GuardianActor(context: ActorContext[GuardianActor.Command]) {

  import GuardianActor._

  private def start: Behavior[Command] =
    Behaviors.receiveMessage {

      case MixOnAddress(depositAddr, finalAddrs, client) =>
        context.spawn(behavior = SentinelActor(depositAddr, finalAddrs.distinct, client), name = depositAddr)
        context.log.info(s"Watching deposit address $depositAddr for transactions...")
        Behaviors.same
    }
}
