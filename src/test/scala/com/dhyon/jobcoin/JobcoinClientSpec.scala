package com.dhyon.jobcoin

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.dhyon.jobcoin.JobcoinClient._
import com.typesafe.config.{Config, ConfigFactory}
import org.f100ded.play.fakews._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpecLike
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Json
import play.api.libs.ws.JsonBodyWritables._

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class JobcoinClientSpec extends AnyFunSpecLike with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = Materializer(system)

  import system.dispatcher

  val config: Config = ConfigFactory.load()
  val getAddressURL: String = config.getString("jobcoin.apiAddressesUrl")

  describe("getAddressInfo") {
    it("should return 200 on normal flow") {
      val testAddressInfo = AddressInfo("1", Seq.empty[Transaction])
      val testAddress = "Jill"

      val testWs = StandaloneFakeWSClient {
        case request@GET(url"$getAddressURL/$addr") =>
          addr shouldBe testAddress
          Ok(Json.toJson(testAddressInfo))
      }
      val client = new JobcoinClient(config, Some(testWs))
      client.getAddressInfo(testAddress) map { result =>
        result shouldBe testAddressInfo
      }
    }
    // TODO: negative flows for getAddressInfo
  }
  // TODO: tests for postTransaction

  override def afterAll: Unit = {
    Await.result(system.terminate(), Duration.Inf)
  }
}
