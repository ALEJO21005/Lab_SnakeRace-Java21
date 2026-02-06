package co.eci.snake.concurrency;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PauseController {
  private final Lock lock = new ReentrantLock();
  private final Condition unpauseCondition = lock.newCondition();
  private final AtomicBoolean paused = new AtomicBoolean(false);
  private volatile CountDownLatch pauseLatch = null;
  private final int totalThreads;

  public PauseController(int totalThreads) {
    this.totalThreads = totalThreads;
  }

  public boolean requestPause() {
    if (paused.compareAndSet(false, true)) {
      pauseLatch = new CountDownLatch(totalThreads);
      try {
        
        return pauseLatch.await(5, java.util.concurrent.TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  public void resume() {
    if (paused.compareAndSet(true, false)) {
      lock.lock();
      try {
        unpauseCondition.signalAll();
      } finally {
        lock.unlock();
      }
    }
  }

  
  public boolean isPaused() {
    return paused.get();
  }

  
  public void checkPause() {
    if (paused.get()) {
      
      if (pauseLatch != null) {
        pauseLatch.countDown();
      }
      
      lock.lock();
      try {
        while (paused.get()) {
          unpauseCondition.await();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        lock.unlock();
      }
    }
  }
}