// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class ExceptionTest {
  public static void main(String[] args) {
    // The following will lead to a catch handler for ExceptionA, which is merged into ExceptionB.
    try {
      throw new ExceptionB("Ouch!");
    } catch (ExceptionA exception) {
      System.out.println("Caught exception: " + exception.getMessage());
    }
  }

  // Will be merged into ExceptionB when class merging is enabled.
  public static class ExceptionA extends Exception {
    public ExceptionA(String message) {
      super(message);
    }
  }

  public static class ExceptionB extends ExceptionA {
    public ExceptionB(String message) {
      super(message);
    }
  }
}
