== A Concept for a Pressure-Based Queue ==

This is just a concept.  I read a long while back about some guys that were
using "pressure" to naturally throttle their systems, although I have no idea
who did it... my memory has failed me.  The idea is to make the act of queueing
work take longer as the work backlog fills up.

Concurrency and asynchrony is awesome, except when the guys submitting the work
can submit way faster than the guys doing the work can do it.  For example,
let's say your DB gets a huge amount of asynchronous requests and that makes
your work slow down a bit.  How do you slow down your clients?

The idea here is to take huge spikes in workload and smooth them out over time
to allow for a more natural degradation of performance than brick walls of pain.

The source for the queue comes from Doug Lea's LinkedBoundedQueue and has been
modified from there.  This then gets used in a specific Akka dispatcher and we
run an "eye test" to watch it throttle the input to the Actor.
