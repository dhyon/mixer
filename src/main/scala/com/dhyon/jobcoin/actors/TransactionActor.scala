package com.dhyon.jobcoin.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}
import com.dhyon.jobcoin.JobcoinClient
import com.dhyon.jobcoin.JobcoinClient.Transaction
import com.dhyon.jobcoin.actors.SentinelActor.UpdateMixResult

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.util.Random.nextInt
import scala.util.{Failure, Success}

/* TransactionActor handles all the dirty work of moving funds to the house and posting randomized transactions
 * to the final deposit addresses over time window.
 */
object TransactionActor {

  private final val HouseAddress: String = "HOUSE" // TODO: pull from config
  private final val MoveToHouseTaskId: String = "MOVE_TO_HOUSE_TASK_ID"

  sealed trait Command

  case class StartMix(transactions: Seq[Transaction]) extends Command

  case class MoveToHouse(transactions: Seq[Transaction]) extends Command

  case class DistributeTasks(totalAmount: String) extends Command

  case class HouseToAddr(taskId: String, amount: String, toAddr: String) extends Command

  case class UpdateTaskResult(taskId: String, success: Boolean, ex: Option[Throwable] = None) extends Command

  def apply(
    depositAddr: String,
    finalAddrs: Seq[String],
    client: JobcoinClient,
    sentinelRef: ActorRef[SentinelActor.Command]
  ): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        new TransactionActor(
          depositAddr = depositAddr,
          finalAddrs = finalAddrs,
          client = client,
          parentRef = sentinelRef,
          context = context,
          timers = timers
        ).active(Set.empty[String], Set.empty[UpdateTaskResult])
      }
    }
}

class TransactionActor(
  depositAddr: String,
  finalAddrs: Seq[String],
  client: JobcoinClient,
  parentRef: ActorRef[SentinelActor.Command],
  context: ActorContext[TransactionActor.Command],
  timers: TimerScheduler[TransactionActor.Command]
) {

  import TransactionActor._

  private def active(
    taskIdSet: Set[String],
    resultSet: Set[UpdateTaskResult]
  ): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {

      case StartMix(transactions) =>
        context.log.info(s"Starting mix operation for $depositAddr")
        context.self ! MoveToHouse(transactions)
        Behaviors.same

      case MoveToHouse(transactions) =>
        val totalAmount = transactions.foldLeft(0.0)(_ + _.amount.toDouble).toString // sum up the total amount to mix
        val futureResult = client.postTransaction(totalAmount, depositAddr, HouseAddress)
        context.pipeToSelf(futureResult) {
          case Success(_) => DistributeTasks(totalAmount)
          case Failure(ex) => UpdateTaskResult(MoveToHouseTaskId, success = false, Some(ex))
        }
        Behaviors.same

      case DistributeTasks(totalAmount) =>
        val timeWindowInSeconds = 10 // TODO: time window should be a user input parameter
        val numDepositsPerAddr = nextInt(3) + 2 // randomize 2-4 deposits per address

        val totalNumDeposits = finalAddrs.size * numDepositsPerAddr
        val randomDepositAmounts = getRandomDepositAmounts(totalAmount, totalNumDeposits)
        val destinationAddrs = Seq.fill(numDepositsPerAddr)(finalAddrs).flatten

        val newTaskIdSet = (randomDepositAmounts zip destinationAddrs) map { // zip to create amount,addr tuples
          case (amount, destinationAddr) =>
            val taskId = UUID.randomUUID().toString
            timers.startSingleTimer(
              key = taskId,
              msg = HouseToAddr(amount, destinationAddr, taskId),
              delay = nextInt(timeWindowInSeconds).seconds
            )
            taskId
        }
        active(taskIdSet ++ newTaskIdSet, resultSet) // update state of active tasks

      case HouseToAddr(amount, toAddr, taskId) =>
        val futureResult = client.postTransaction(amount, HouseAddress, toAddr)
        context.pipeToSelf(futureResult) { // thread-safe actor to future call
          case Success(_) => UpdateTaskResult(taskId, success = true, None)
          case Failure(ex) => UpdateTaskResult(taskId, success = false, Some(ex))
        }
        Behaviors.same

      case UpdateTaskResult(taskId, success, ex) =>
        val newTaskIdSet = taskIdSet - taskId
        val newResultSet = resultSet + UpdateTaskResult(taskId, success, ex)
        if (taskId == MoveToHouseTaskId || newTaskIdSet.isEmpty) {
          parentRef ! UpdateMixResult(newResultSet)
          Behaviors.stopped // shut down after all tasks are finished or house op fails
        } else {
          active(newTaskIdSet, newResultSet) // update state
        }
    }
  }

  /* Returns a list of randomized deposit amounts that add up to total amount (minus fees) */
  private def getRandomDepositAmounts(totalAmount: String, totalNumDeposits: Int): Seq[String] = {
    /* TODO: Randomize these deposit amounts for real. For inspiration:
     *  https://stackoverflow.com/questions/8064629/random-numbers-that-add-to-100-matlab/8068956
     * TODO: Fees - take a little slice off the top by withholding a % from these distributions
     */
    val superRandomAmount = (totalAmount.toDouble / totalNumDeposits).toString
    Seq.fill(totalNumDeposits)(superRandomAmount)
  }

}
