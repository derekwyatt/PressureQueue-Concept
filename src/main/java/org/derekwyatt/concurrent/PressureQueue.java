package org.derekwyatt.concurrent;

/**
 * This code is ripped off from Doug Lea.  It was derived from
 * java.util.concurrent.LinkedBlockingQueue
 */

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.*;

public class PressureQueue<E> extends AbstractQueue<E> {
  static class Node<E> {
    /** The item, volatile to ensure barrier separating write and read */
    volatile E item;
    Node<E> next;
    Node(E x) { item = x; }
  }

  /** The threshold at which the push throttling kicks in. */
  private final int threshold;

  /** The algorithm that calculates the amount of time to wait on a push. */
  private final PressureAlgorithm algo;

  /** Current number of elements */
  private final AtomicInteger count = new AtomicInteger(0);

  /** Head of linked list */
  private transient Node<E> head;

  /** Tail of linked list */
  private transient Node<E> last;

  /** Lock held by take, poll, etc */
  private final ReentrantLock takeLock = new ReentrantLock();

  /** Wait queue for waiting takes */
  private final Condition putHappened = takeLock.newCondition();

  /** Lock held by put, offer, etc */
  private final ReentrantLock putLock = new ReentrantLock();

  /** Wait queue for waiting puts */
  private final Condition takeHappened = putLock.newCondition();

  private static class SquaredAlgorithm implements PressureAlgorithm {
    private final TimeUnit unit;
    public SquaredAlgorithm(TimeUnit unit) { this.unit = unit; }
    public TimePair timeToWait(int threshold, int currentSize) {
      int diff = currentSize - threshold;
      if (diff > 0)
        return new TimePair(diff * diff, unit);
      else
        return new TimePair(0, unit);
    }
  }
  public static final PressureAlgorithm SQUARED_NANOSECONDS = new SquaredAlgorithm(TimeUnit.NANOSECONDS);
  public static final PressureAlgorithm SQUARED_MICROSECONDS = new SquaredAlgorithm(TimeUnit.MICROSECONDS);
  public static final PressureAlgorithm SQUARED_MILLISECONDS = new SquaredAlgorithm(TimeUnit.MILLISECONDS);
  public static final PressureAlgorithm SQUARED_SECONDS = new SquaredAlgorithm(TimeUnit.SECONDS);
  private static class LinearAlgorithm implements PressureAlgorithm {
    private final TimeUnit unit;
    public LinearAlgorithm(TimeUnit unit) { this.unit = unit; }
    public TimePair timeToWait(int threshold, int currentSize) {
      int diff = currentSize - threshold;
      return new TimePair(diff, unit);
    }
  }
  public static final PressureAlgorithm LINEAR_NANOSECONDS = new LinearAlgorithm(TimeUnit.NANOSECONDS);
  public static final PressureAlgorithm LINEAR_MICROSECONDS = new LinearAlgorithm(TimeUnit.MICROSECONDS);
  public static final PressureAlgorithm LINEAR_MILLISECONDS = new LinearAlgorithm(TimeUnit.MILLISECONDS);
  public static final PressureAlgorithm LINEAR_SECONDS = new LinearAlgorithm(TimeUnit.SECONDS);

  private void signalPutHappened() {
    final ReentrantLock takeLock = this.takeLock;
    takeLock.lock();
    try {
      putHappened.signal();
    } finally {
      takeLock.unlock();
    }
  }

  private void signalTakeHappened() {
    final ReentrantLock putLock = this.putLock;
    putLock.lock();
    try {
      takeHappened.signal();
    } finally {
      putLock.unlock();
    }
  }

  private void insert(E x) {
    last = last.next = new Node<E>(x);
  }

  private E extract() {
    Node<E> first = head.next;
    head = first;
    E x = first.item;
    first.item = null;
    return x;
  }

  private void fullyLock() {
    putLock.lock();
    takeLock.lock();
  }

  private void fullyUnlock() {
    takeLock.unlock();
    putLock.unlock();
  }


  public PressureQueue() {
    this(Integer.MAX_VALUE, SQUARED_MICROSECONDS);
  }

  public PressureQueue(int threshold) {
    this(threshold, SQUARED_MICROSECONDS);
  }

  public PressureQueue(int threshold, PressureAlgorithm algorithm) {
    this.algo = algorithm;
    this.threshold = threshold;
    last = head = new Node<E>(null);
  }

  public PressureQueue(Collection<? extends E> c) {
    this();
    for (E e : c)
      add(e);
  }


  public int size() {
    return count.get();
  }

  public void put(E e) throws InterruptedException {
    if (e == null) throw new NullPointerException();
    final ReentrantLock putLock = this.putLock;
    final AtomicInteger count = this.count;
    try {
      putLock.lockInterruptibly();
      if (count.get() >= threshold) {
        long nanosWaited = 0L; 
        for (;;) {
          TimePair timeToWait = algo.timeToWait(threshold, count.get());
          long nanosToWait = timeToWait.unit.toNanos(timeToWait.value) - nanosWaited;
          if (nanosToWait > 0)
            nanosWaited += nanosToWait - takeHappened.awaitNanos(nanosToWait);
          else
            break;
        }
      }
      insert(e);
      count.getAndIncrement();
      signalPutHappened();
    } catch (InterruptedException ie) {
      takeHappened.signal(); // propagate to a non-interrupted thread
      throw ie;
    } finally {
      putLock.unlock();
    }
  }

  //public boolean offer(E e, long timeout, TimeUnit unit)
  //  throws InterruptedException {

  //  //if (e == null) throw new NullPointerException();
  //  //long nanos = unit.toNanos(timeout);
  //  //int c = -1;
  //  //final ReentrantLock putLock = this.putLock;
  //  //final AtomicInteger count = this.count;
  //  //putLock.lockInterruptibly();
  //  //try {
  //  //  for (;;) {
  //  //    if (count.get() < capacity) {
  //  //      insert(e);
  //  //      c = count.getAndIncrement();
  //  //      if (c + 1 < capacity)
  //  //        takeHappened.signal();
  //  //      break;
  //  //    }
  //  //    if (nanos <= 0)
  //  //      return false;
  //  //    try {
  //  //      nanos = takeHappened.awaitNanos(nanos);
  //  //    } catch (InterruptedException ie) {
  //  //      takeHappened.signal(); // propagate to a non-interrupted thread
  //  //      throw ie;
  //  //    }
  //  //  }
  //  //} finally {
  //  //  putLock.unlock();
  //  //}
  //  //if (c == 0)
  //  //  signalPutHappened();
  //  return true;
  //}

  public boolean offer(E e) {
    try {
      put(e);
      return true;
    } catch (Throwable x) {
      return false;
    }
  }

  public E take() throws InterruptedException {
    E x;
    int c = -1;
    final AtomicInteger count = this.count;
    final ReentrantLock takeLock = this.takeLock;
    takeLock.lockInterruptibly();
    try {
      try {
        while (count.get() == 0)
          putHappened.await();
      } catch (InterruptedException ie) {
        putHappened.signal(); // propagate to a non-interrupted thread
        throw ie;
      }

      x = extract();
      c = count.getAndDecrement();
      signalTakeHappened();
      if (c > 1)
        putHappened.signal();
    } finally {
      takeLock.unlock();
    }
    return x;
  }

  public E poll(long timeout, TimeUnit unit) throws InterruptedException {
    E x = null;
    int c = -1;
    long nanos = unit.toNanos(timeout);
    final AtomicInteger count = this.count;
    final ReentrantLock takeLock = this.takeLock;
    takeLock.lockInterruptibly();
    try {
      for (;;) {
        if (count.get() > 0) {
          x = extract();
          c = count.getAndDecrement();
          if (c > 1)
            putHappened.signal();
          break;
        }
        if (nanos <= 0)
          return null;
        try {
          nanos = putHappened.awaitNanos(nanos);
        } catch (InterruptedException ie) {
          putHappened.signal(); // propagate to a non-interrupted thread
          throw ie;
        }
      }
    } finally {
      takeLock.unlock();
    }
    signalTakeHappened();
    return x;
  }

  public E poll() {
    final AtomicInteger count = this.count;
    if (count.get() == 0)
      return null;
    E x = null;
    int c = -1;
    final ReentrantLock takeLock = this.takeLock;
    takeLock.lock();
    try {
      if (count.get() > 0) {
        x = extract();
        c = count.getAndDecrement();
        if (c > 1)
          putHappened.signal();
      }
    } finally {
      takeLock.unlock();
    }
    signalTakeHappened();
    return x;
  }


  public E peek() {
    if (count.get() == 0)
      return null;
    final ReentrantLock takeLock = this.takeLock;
    takeLock.lock();
    try {
      Node<E> first = head.next;
      if (first == null)
        return null;
      else
        return first.item;
    } finally {
      takeLock.unlock();
    }
  }

  public boolean remove(Object o) {
    if (o == null) return false;
    boolean removed = false;
    fullyLock();
    try {
      Node<E> trail = head;
      Node<E> p = head.next;
      while (p != null) {
        if (o.equals(p.item)) {
          removed = true;
          break;
        }
        trail = p;
        p = p.next;
      }
      if (removed) {
        p.item = null;
        trail.next = p.next;
        if (last == p)
          last = trail;
        takeHappened.signalAll();
      }
    } finally {
      fullyUnlock();
    }
    return removed;
  }

  public Object[] toArray() {
    fullyLock();
    try {
      int size = count.get();
      Object[] a = new Object[size];
      int k = 0;
      for (Node<E> p = head.next; p != null; p = p.next)
        a[k++] = p.item;
      return a;
    } finally {
      fullyUnlock();
    }
  }

  public <T> T[] toArray(T[] a) {
    fullyLock();
    try {
      int size = count.get();
      if (a.length < size)
        a = (T[])java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size);

      int k = 0;
      for (Node p = head.next; p != null; p = p.next)
        a[k++] = (T)p.item;
      if (a.length > k)
        a[k] = null;
      return a;
    } finally {
      fullyUnlock();
    }
  }

  public String toString() {
    fullyLock();
    try {
      return super.toString();
    } finally {
      fullyUnlock();
    }
  }

  public void clear() {
    fullyLock();
    try {
      head.next = null;
      assert head.item == null;
      last = head;
      count.getAndSet(0);
      takeHappened.signalAll();
    } finally {
      fullyUnlock();
    }
  }

  public int drainTo(Collection<? super E> c) {
    if (c == null)
      throw new NullPointerException();
    if (c == this)
      throw new IllegalArgumentException();
    Node<E> first;
    fullyLock();
    try {
      first = head.next;
      head.next = null;
      assert head.item == null;
      last = head;
      count.getAndSet(0);
      takeHappened.signalAll();
    } finally {
      fullyUnlock();
    }
    // Transfer the elements outside of locks
    int n = 0;
    for (Node<E> p = first; p != null; p = p.next) {
      c.add(p.item);
      p.item = null;
      ++n;
    }
    return n;
  }

  public int drainTo(Collection<? super E> c, int maxElements) {
    if (c == null)
      throw new NullPointerException();
    if (c == this)
      throw new IllegalArgumentException();
    fullyLock();
    try {
      int n = 0;
      Node<E> p = head.next;
      while (p != null && n < maxElements) {
        c.add(p.item);
        p.item = null;
        p = p.next;
        ++n;
      }
      if (n != 0) {
        head.next = p;
        assert head.item == null;
        if (p == null)
          last = head;
        count.getAndAdd(-n);
        takeHappened.signalAll();
      }
      return n;
    } finally {
      fullyUnlock();
    }
  }

  public Iterator<E> iterator() {
    return new Itr();
  }

  private class Itr implements Iterator<E> {
    /*
     * Basic weak-consistent iterator.  At all times hold the next
     * item to hand out so that if hasNext() reports true, we will
     * still have it to return even if lost race with a take etc.
     */
    private Node<E> current;
    private Node<E> lastRet;
    private E currentElement;

    Itr() {
      final ReentrantLock putLock = PressureQueue.this.putLock;
      final ReentrantLock takeLock = PressureQueue.this.takeLock;
      putLock.lock();
      takeLock.lock();
      try {
        current = head.next;
        if (current != null)
          currentElement = current.item;
      } finally {
        takeLock.unlock();
        putLock.unlock();
      }
    }

    public boolean hasNext() {
      return current != null;
    }

    public E next() {
      final ReentrantLock putLock = PressureQueue.this.putLock;
      final ReentrantLock takeLock = PressureQueue.this.takeLock;
      putLock.lock();
      takeLock.lock();
      try {
        if (current == null)
          throw new NoSuchElementException();
        E x = currentElement;
        lastRet = current;
        current = current.next;
        if (current != null)
          currentElement = current.item;
        return x;
      } finally {
        takeLock.unlock();
        putLock.unlock();
      }
    }

    public void remove() {
      if (lastRet == null)
        throw new IllegalStateException();
      final ReentrantLock putLock = PressureQueue.this.putLock;
      final ReentrantLock takeLock = PressureQueue.this.takeLock;
      putLock.lock();
      takeLock.lock();
      try {
        Node<E> node = lastRet;
        lastRet = null;
        Node<E> trail = head;
        Node<E> p = head.next;
        while (p != null && p != node) {
          trail = p;
          p = p.next;
        }
        if (p == node) {
          p.item = null;
          trail.next = p.next;
          if (last == p)
            last = trail;
          int c = count.getAndDecrement();
          takeHappened.signalAll();
        }
      } finally {
        takeLock.unlock();
        putLock.unlock();
      }
    }
  }

  private void writeObject(java.io.ObjectOutputStream s)
    throws java.io.IOException {

    fullyLock();
    try {
      s.defaultWriteObject();

      // Write out all elements in the proper order.
      for (Node<E> p = head.next; p != null; p = p.next)
        s.writeObject(p.item);

      // Use trailing null as sentinel
      s.writeObject(null);
    } finally {
      fullyUnlock();
    }
  }

  private void readObject(java.io.ObjectInputStream s)
    throws java.io.IOException, ClassNotFoundException {
    // Read in threshold, and any hidden stuff
    s.defaultReadObject();

    count.set(0);
    last = head = new Node<E>(null);

    // Read in all elements and place in queue
    for (;;) {
      E item = (E)s.readObject();
      if (item == null)
        break;
      add(item);
    }
  }

  public static void main(String[] args) throws Exception {
    PressureQueue<Integer> q = new PressureQueue<Integer>(2000, SQUARED_MICROSECONDS);
    for (int i = 0; i < 50000; i++) {
      System.out.println("Put " + i);
      q.put(1);
    }
  }
}

