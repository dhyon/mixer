package com.dhyon.jobcoin

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.dhyon.jobcoin.JobcoinClient.Transaction
import com.dhyon.jobcoin.actors.TransactionActor.MoveToHouse
import com.dhyon.jobcoin.actors.{SentinelActor, TransactionActor}
import org.mockito.MockitoSugar
import org.scalatest.funspec.AnyFunSpecLike

import java.time.Instant
import scala.concurrent.Future

class TransactionActorSpec extends ScalaTestWithActorTestKit with AnyFunSpecLike with MockitoSugar {

  val testClient: JobcoinClient = mock[JobcoinClient]
  val depositAddr: String = "depositAddr"
  val houseAddr: String = "HOUSE"

  describe("MoveToHouse command") {
    it("should continue on to DistributeTasks after successful House transaction") {
      // TODO: happy path
    }
    it("should send back a UpdateMixResult after unsuccessful House transaction") {
      val parentActor = createTestProbe[SentinelActor.Command]
      val testActor = spawn(TransactionActor(depositAddr, Seq("x","y"), testClient, parentActor.ref))
      val testTransactions: Seq[Transaction] = Seq(
        Transaction("1", depositAddr, Some("x"), Instant.now),
        Transaction("2", depositAddr, Some("y"), Instant.now),
        Transaction("3", depositAddr, Some("z"), Instant.now)
      )

      when(testClient.postTransaction("6.0", depositAddr, houseAddr))
        .thenReturn(Future.failed(new RuntimeException("oops")))
      testActor ! MoveToHouse(testTransactions)
      parentActor.expectMessageType[SentinelActor.UpdateMixResult]
    }
  }

  //TODO: tests for the other commands
}
