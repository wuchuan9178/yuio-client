package dev.sherii.yuio.utils;

public final class IntervalledCounter {
   private static final int INITIAL_SIZE = 8;
   protected long[] times = new long[8];
   protected long[] counts = new long[8];
   protected final long interval;
   protected long minTime;
   protected long sum;
   protected int head;
   protected int tail;

   public IntervalledCounter(long interval) {
      this.interval = interval;
   }

   public void updateCurrentTime() {
      this.updateCurrentTime(System.nanoTime());
   }

   public void updateCurrentTime(long currentTime) {
      long sum = this.sum;
      int head = this.head;
      int tail = this.tail;
      long minTime = currentTime - this.interval;
      int arrayLen = this.times.length;

      while (head != tail && this.times[head] - minTime < 0L) {
         sum -= this.counts[head];
         this.counts[head] = 0L;
         if (++head >= arrayLen) {
            head = 0;
         }
      }

      this.sum = sum;
      this.head = head;
      this.minTime = minTime;
   }

   public void addTime(long currTime) {
      this.addTime(currTime, 1L);
   }

   public void addTime(long currTime, long count) {
      if (currTime - this.minTime >= 0L) {
         int nextTail = (this.tail + 1) % this.times.length;
         if (nextTail == this.head) {
            this.resize();
            nextTail = (this.tail + 1) % this.times.length;
         }

         this.times[this.tail] = currTime;
         this.counts[this.tail] = this.counts[this.tail] + count;
         this.sum += count;
         this.tail = nextTail;
      }
   }

   public void updateAndAdd(long count) {
      long currTime = System.nanoTime();
      this.updateCurrentTime(currTime);
      this.addTime(currTime, count);
   }

   public void updateAndAdd(long count, long currTime) {
      this.updateCurrentTime(currTime);
      this.addTime(currTime, count);
   }

   private void resize() {
      long[] oldElements = this.times;
      long[] oldCounts = this.counts;
      long[] newElements = new long[this.times.length * 2];
      long[] newCounts = new long[this.times.length * 2];
      this.times = newElements;
      this.counts = newCounts;
      int head = this.head;
      int tail = this.tail;
      int size = tail >= head ? tail - head : tail + (oldElements.length - head);
      this.head = 0;
      this.tail = size;
      if (tail >= head) {
         System.arraycopy(oldElements, head, newElements, 0, size);
         System.arraycopy(oldCounts, head, newCounts, 0, size);
      } else {
         System.arraycopy(oldElements, head, newElements, 0, oldElements.length - head);
         System.arraycopy(oldElements, 0, newElements, oldElements.length - head, tail);
         System.arraycopy(oldCounts, head, newCounts, 0, oldCounts.length - head);
         System.arraycopy(oldCounts, 0, newCounts, oldCounts.length - head, tail);
      }
   }

   public double getRate() {
      return this.sum / (this.interval * 1.0E-9);
   }

   public long getInterval() {
      return this.interval;
   }

   public long getSum() {
      return this.sum;
   }

   public int totalDataPoints() {
      return this.tail >= this.head ? this.tail - this.head : this.tail + (this.counts.length - this.head);
   }
}
