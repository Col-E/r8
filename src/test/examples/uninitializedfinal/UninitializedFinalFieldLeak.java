// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package uninitializedfinal;

// Test that leaks an instance before its final field has been initialized to a thread that
// reads that field. This tests that redundant field load elimination does not eliminate
// field reads (even of final fields) that cross a monitor operation.
public class UninitializedFinalFieldLeak {

  public static class PollingThread extends Thread {
    public int result = 0;
    UninitializedFinalFieldLeak f;

    PollingThread(UninitializedFinalFieldLeak f) {
      this.f = f;
    }

    // Read the field a number of times. Then lock on the object to await field initialization.
    public void run() {
      result += f.i;
      result += f.i;
      result += f.i;
      f.threadReadsDone = true;
      synchronized (f) {
        result += f.i;
      }
      // The right result is 42. Reading the uninitialized 0 three times and then
      // reading the initialized value. It is safe to remove the two redundant loads
      // before the monitor operation.
      System.out.println(result);
    }
  }

  public final int i;
  public volatile boolean threadReadsDone = false;

  public UninitializedFinalFieldLeak() throws InterruptedException {
    // Leak the object to a thread and start the thread with the lock on the object taken.
    // Then allow the other thread to run and read the uninitialized field.
    // Finally, initialize the field and release the lock.
    PollingThread t = new PollingThread(this);
    synchronized (this) {
      t.start();
      while (!threadReadsDone) {
        Thread.yield();
      }
      i = 42;
    }
    t.join();
  }

  public static void main(String[] args) throws InterruptedException {
    new UninitializedFinalFieldLeak();
  }
}
