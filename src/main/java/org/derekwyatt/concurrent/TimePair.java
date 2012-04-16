package org.derekwyatt.concurrent;

import java.util.concurrent.TimeUnit;

public class TimePair {
  public TimePair(long value, TimeUnit unit) {
    this.value = value;
    this.unit = unit;
  }
  public long value;
  public TimeUnit unit;
}
