package com.dhyon.jobcoin

import akka.stream.Materializer
import com.dhyon.jobcoin.JobcoinClient._
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.f100ded.play.fakews.StandaloneFakeWSClient
import play.api.libs.json._
import play.api.libs.ws.JsonBodyReadables._
import play.api.libs.ws.JsonBodyWritables._
import play.api.libs.ws.ahc._

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/* This is a pretty bare bones external client. If I had more time I'd probably leverage a library like
 * https://github.com/jhalterman/failsafe to add Retry, Timeout, and CircuitBreaker policies on network requests.
 */
class JobcoinClient(config: Config, fakeWsClient: Option[StandaloneFakeWSClient] = None)
  (implicit materializer: Materializer) extends LazyLogging {

  private lazy val apiAddressesUrl: String = config.getString("jobcoin.apiAddressesUrl")
  private lazy val apiTransactionsUrl: String = config.getString("jobcoin.apiTransactionsUrl")

  private val client = fakeWsClient.getOrElse(StandaloneAhcWSClient())

  // Docs:
  // https://github.com/playframework/play-ws
  // https://www.playframework.com/documentation/2.6.x/ScalaJsonCombinators

  def getAddressInfo(address: String): Future[AddressInfo] = {
    val url = s"$apiAddressesUrl/$address"
    client
      .url(url)
      .get
      .map { response =>
        val logStr = s"Called $url and got code=${response.status} with body=${response.body}"
        response.status match {
          case 200 =>
            response.body[JsValue].validate[AddressInfo] match {
              case success: JsSuccess[AddressInfo] =>
                logger.debug(logStr)
                success.get
              case _: JsError =>
                logger.error(logStr)
                throw new RuntimeException(s"Failed to parse response with body=${response.body} into AddressInfo")
            }
          case _ =>
            logger.error(logStr)
            throw new RuntimeException(logStr)
        }
      }
  }

  def postTransaction(amount: String, fromAddr: String, toAddr: String): Future[Boolean] = {
    val data = Json.obj("fromAddress" -> fromAddr, "toAddress" -> toAddr, "amount" -> amount)
    client
      .url(apiTransactionsUrl)
      .post(data)
      .map { response =>
        val logStr = s"Called $apiTransactionsUrl with data=$data and got code=${response.status} with " +
          s"body=${response.body}"
        response.status match {
          case 200 =>
            logger.info(logStr)
            true
          case _ =>
            logger.error(logStr)
            throw new RuntimeException(logStr)
        }
      }
  }

}

object JobcoinClient {

  case class Transaction(
    amount: String, // it's a string to preserve precision
    toAddress: String,
    fromAddress: Option[String] = None,
    timestamp: Instant,
  )

  object Transaction {
    implicit val format: Format[Transaction] = Json.format[Transaction]
  }

  case class AddressInfo(
    balance: String, // it's a string to preserve precision
    transactions: Seq[Transaction]
  )

  object AddressInfo {
    implicit val format: Format[AddressInfo] = Json.format[AddressInfo]
  }

}
