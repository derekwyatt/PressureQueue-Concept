package org.derekwyatt

import akka.actor.{ ActorContext, ActorSystem }
import akka.dispatch.{ Envelope, MessageQueue, QueueBasedMessageQueue, UnboundedMessageQueueSemantics }
import com.typesafe.config.Config
import org.derekwyatt.concurrent.PressureQueue

case class PressureMailbox() extends akka.dispatch.MailboxType {
  // This constructor signature must exist, it will be called by Akka
  def this(settings: ActorSystem.Settings, config: Config) = this()

  // The create method is called to create the MessageQueue
  final override def create(owner: Option[ActorContext]): MessageQueue =
    new QueueBasedMessageQueue with UnboundedMessageQueueSemantics {
      final val queue = new PressureQueue[Envelope](10, PressureQueue.SQUARED_MILLISECONDS)
    }
}
