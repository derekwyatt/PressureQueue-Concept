package org.derekwyatt

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.TestKit
import org.scalatest.{WordSpec, BeforeAndAfterAll}
import org.scalatest.matchers.MustMatchers

class WorkingActor extends Actor {
  def receive = {
    case i: Int if i < 20 => Thread.sleep(300)
    case i: Int if i < 300 => Thread.sleep(20)
    case i: Int if i < 500 => Thread.sleep(80)
    case i: Int if i < 600 => Thread.sleep(10)
    case i: Int if i < 800 => Thread.sleep(20)
    case i: Int if i > 950 => Thread.sleep(30)
    case _ => 
  }
}

class PressureMailboxSpec(_system: ActorSystem) extends TestKit(_system) with WordSpec
                                                                         with BeforeAndAfterAll
                                                                         with MustMatchers {
  import com.typesafe.config.ConfigFactory
  val customConf = ConfigFactory.parseString("""
    pressure-dispatcher {
      type = Dispatcher
      executor = "fork-join-executor"
      mailbox-type = "org.derekwyatt.PressureMailbox"
      fork-join-executor {
        parallelism-min = 2
        parallelism-factor = 2.0
        parallelism-max = 10
      }
      throughput = 1
    }""")

  def this() = this(ActorSystem("PressureMailboxSpec"))
  override def afterAll() {
    system.shutdown()
  }

  "PressureMailbox" should { //{1
    val sys = ActorSystem("PressureSystem", ConfigFactory.load(customConf))
    val a = sys.actorOf(Props[WorkingActor].withDispatcher("pressure-dispatcher"), "WorkingActor")
      (1 to 1000) foreach { i =>
        println(i)
        a ! i
      }
  } //}1
                                                                         }
