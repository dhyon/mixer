# Background

Jobcoin transactions are pseudonymous - meaning they don't directly reveal the identities of the sender/receiver.
However, these identities can still be revealed with some analysis of the publicly available transaction ledger.

A mixer solution is one way to solve the privacy issue presented here. This jobcoin mixer moves any jobcoins deposited
to a deposit address into a larger "house" address where it is mixed with other jobcoins. Then the mixer distributes the
jobcoins in smaller discrete amounts, over a period of time, to a set of final destination addresses. This effectively
disassociates original jobcoins from the owner.

### Run

`sbt run`

*note: the user can mix for multiple deposit addresses while this CLI is up and running*

*note: if logs are too verbose, adjust levels in logback.xml*

### Test

`sbt test`

# Design considerations

**Guardian actor** - initialized when program starts up. It's the root level actor that's in charge of spawning and
managing Sentinels when a new deposit address is generated.

**Sentinel actor** - effectively watches a single deposit address for incoming transactions. If new deposits are found,
it spawns a Transaction actor to do the mixing. In its current iteration, a Sentinel polls the jobcoin API.

**Transaction actor** - handles all the dirty work of moving jobcoins to the house and posting transactions to the final
destination addresses. Transactions are made at random intervals over a given time window. The # of transactions per
destination address is also randomized.

I chose to implement a solution to this problem with akka-typed for a couple reasons:

1. Easier to design and implement. Akka actors make concurrency simple by creating the illusion everything is
   single-threaded. Less stressing about accessing/protecting state is good.
2. In turn, scaling out this solution is simple. An actor is its own light-weight thread. I could conceptually spawn
   thousands of Sentinel actors on decent hardware. Asynchronous, non-blocking, and performant? Check check check.
3. If this service turned out to be a real money maker in production, I'd want some functionality for halting ongoing
   mix operations. Futures are difficult to cancel so that was out of the equation. Akka can do this easily. Using ZIO
   or cats effect seems viable as well though.

# ToDo

Speaking of time, if I had more of it I'd do the following:

- unit tests for actor commands, client methods, and the main class as well
- time window should a user parameter that gets exposed in the CLI
- actually randomizing the deposit amounts to be spread out over the destination addresses
- for fees, take a small percentage off the top before moving from the house to destination addresses
- more robust exception handling
- dark mode with no logging
- resilience: mixing is a multi-step process with money on the line. If any step fails along the way, there should a way
  to recover and resume the operation.






