// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package loop;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class UdpServer {
  private static final String PREFIX = "RANDOM_DATA_PREFIX_";
  public static void main(String[] args) throws Exception {
    ExecutorService service = Executors.newWorkStealingPool(2);
    Callable c = new Callable() {
      @Override
      public Object call() throws Exception {
        int counter = 0;
        byte[] receiveData = new byte[1024];
        while (true) {
          // Mimic receiving data via socket. (A use of actual socket is IO blocking.)
          receiveData = (PREFIX + counter++).getBytes();
        }
      }
    };
    Future<?> f = service.submit(c);
    try {
      f.get(1, TimeUnit.NANOSECONDS);
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      System.out.println(e);
    } finally {
      f.cancel(true);
      service.shutdownNow();
    }
  }
}
