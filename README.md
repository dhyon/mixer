# Background

Transfer any jobcoins deposited to a specific deposit address into a larger pool of jobcoins ("the house")
which are then distributed at random intervals to preferred destination address(es). This effectively disassociates
original jobcoins from the owner.

### Run

`sbt run`

*note: the user can mix for multiple deposit addresses while this CLI is up and running*

*note: if logs are too verbose, just change `<root level="INFO">` to `<root level="ERROR">` in logback.xml*

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

1. Easier to design and implement. Akka actors make concurrency simple by creating the illusion everything
   is single-threaded. Less stressing about accessing/protecting state is good.
2. In turn, scaling out this solution is simple. An actor is its own light-weight thread. I could conceptually spawn
   thousands of Sentinel actors on decent hardware. Asynchronous, non-blocking, and performant? Check check check.
3. If this service turned out to be a real money maker in production, I'd want some functionality for halting ongoing
   mix operations. Futures can't be cancelled so that was out of the equation. Akka looked good. Using ZIO or
   cats effect seems viable as well though.

# ToDo

Speaking of time, if I had more of it I'd do the following:

- unit tests for actor commands, client methods, and the main class as well
- time window should a user parameter that gets exposed in the CLI
- actually randomizing the deposit amounts to be spread out over the destination addresses - that would make the mixing
  utterly untrackable.
- for fees, take a small percentage off the top before moving from the house to destination addresses
- more robust exception handling

- resilience features: this is a multi-step process with money on the line and if any of it fails along the way, things
  get hairy. It would be good to keep track of state somewhere else in case of catastrophe so that the service can
  recover and resume the operation.






