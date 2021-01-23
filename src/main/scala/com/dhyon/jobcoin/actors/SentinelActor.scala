package com.dhyon.jobcoin.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import com.dhyon.jobcoin.JobcoinClient
import com.dhyon.jobcoin.JobcoinClient.Transaction
import com.dhyon.jobcoin.actors.TransactionActor.{StartMix, UpdateTaskResult}

import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

/* A SentinelActor is in charge of watching a deposit address and polling it for incoming transactions */
object SentinelActor {

  sealed trait Command

  case object Poll extends Command

  case class Loop(knownTransactions: Seq[Transaction]) extends Command

  case class UpdateMixResult(resultSet: Set[UpdateTaskResult]) extends Command

  def apply(depositAddr: String, finalAddrs: Seq[String], client: JobcoinClient): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new SentinelActor(
          depositAddr = depositAddr,
          finalAddrs = finalAddrs,
          client = client,
          context = context,
          timers = timers
        ).idle
      }
    }
}

class SentinelActor(
  depositAddr: String,
  finalAddrs: Seq[String],
  client: JobcoinClient,
  context: ActorContext[SentinelActor.Command],
  timers: TimerScheduler[SentinelActor.Command]
) {

  import SentinelActor._

  private def idle: Behavior[Command] =
    Behaviors.setup[Command] { _ =>
      timers.startTimerWithFixedDelay(key = depositAddr, msg = Poll, delay = 5.seconds) // TODO: pull interval from config
      active(Seq.empty[Transaction])
    }

  private def active(knownTransactions: Seq[Transaction]): Behavior[Command] =
    Behaviors.receiveMessage[Command] {

      case Loop(knownTransactions) =>
        active(knownTransactions)

      case Poll =>
        val futureResult = client.getAddressInfo(depositAddr)
        context.pipeToSelf(futureResult) { // thread-safe actor to future call
          case Success(info) =>
            val newDeposits = info.transactions.filter(_.toAddress == depositAddr) diff knownTransactions
            if (newDeposits.nonEmpty) {
              context.log.info(s"Detected new deposit(s) to $depositAddr : $newDeposits")
              val transactionActorRef = context.spawn(
                behavior = TransactionActor(depositAddr, finalAddrs, client, context.self),
                name = depositAddr
              )
              transactionActorRef ! StartMix(newDeposits)
              // TODO: watch child for unhandled exceptions
            }
            Loop(knownTransactions ++ newDeposits) // maintaining state
          case Failure(_) =>
            Loop(knownTransactions)
        }
        Behaviors.same

      case UpdateMixResult(results) =>
        if (results.exists(!_.success))
          context.log.error(s"FAILURE: Something went wrong during a mix operation from $depositAddr to $finalAddrs")
        else
          context.log.info(s"SUCCESS: Mix operation from $depositAddr to $finalAddrs was successful!")
        Behaviors.same
    }
}
