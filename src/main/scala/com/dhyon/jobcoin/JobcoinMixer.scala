package com.dhyon.jobcoin

import akka.actor.typed.ActorSystem
import akka.stream.Materializer
import com.dhyon.jobcoin.actors.GuardianActor
import com.dhyon.jobcoin.actors.GuardianActor.{Command, MixOnAddress}
import com.typesafe.config.ConfigFactory

import java.util.UUID
import scala.io.StdIn

object JobcoinMixer {

  object CompletedException extends Exception {}

  def main(args: Array[String]): Unit = {

    // Create an Akka Typed actor system
    implicit val actorSystem: ActorSystem[Command] = ActorSystem[Command](GuardianActor.apply, "root")
    implicit val materializer: Materializer = Materializer(actorSystem)

    lazy val config = ConfigFactory.load()
    lazy val client = new JobcoinClient(config)

    try {
      while (true) {
        println(prompt)
        val line = StdIn.readLine()

        if (line == "quit") throw CompletedException

        val addresses = line.split(",")
        if (line == "") {
          println(s"You must specify empty addresses to mix into!\n$helpText")
        } else {
          val depositAddress = UUID.randomUUID()
          actorSystem ! MixOnAddress(depositAddress.toString, addresses.toSeq, client)

          println(s"You may now send Jobcoins to address $depositAddress. They will be mixed and sent to your " +
            s"destination addresses.")
        }
      }
    } catch {
      case CompletedException => println("Quitting...")
    } finally {
      actorSystem.terminate()
    }
  }

  val prompt: String = "Please enter a comma-separated list of new, unused Jobcoin addresses where your mixed " +
    "Jobcoins will be sent."
  val helpText: String =
    """
      |Jobcoin Mixer
      |
      |Takes in at least one return address as parameters (where to send coins after mixing). Returns a deposit
      |address to send coins to.
      |
      |Usage:
      |    run return_addresses...
    """.stripMargin
}
