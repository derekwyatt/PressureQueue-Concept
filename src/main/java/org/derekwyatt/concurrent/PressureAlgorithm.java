package org.derekwyatt.concurrent;

public interface PressureAlgorithm {
  public TimePair timeToWait(int threshold, int currentSize);
}
