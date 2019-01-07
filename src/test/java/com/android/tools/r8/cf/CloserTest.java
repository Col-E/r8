// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import com.android.tools.r8.NeverInline;
import java.io.IOException;
import java.io.OutputStream;

public class CloserTest {

  @NeverInline
  public static CloserTest create() {
    return new CloserTest();
  }

  OutputStream register() {
    return System.out;
  }

  public void doSomething(String message) throws IOException {
    System.out.println(message);
  }

  public static void main(String... args) throws IOException {
    CloserTest closer = CloserTest.create();
    try {
      OutputStream out = closer.register();
      out.write(args[0].getBytes());
    } catch (Throwable e) {
      closer.doSomething(e.getMessage());
    } finally {
      closer.doSomething("FINISHED");
    }
  }
}
