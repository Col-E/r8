// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class ExceptionTest {
  public static void main(String[] args) {
    // The following will lead to a catch handler for ExceptionA, which is merged into ExceptionB.
    try {
      doSomethingThatMightThrowExceptionB();
      doSomethingThatMightThrowException2();
    } catch (ExceptionB exception) {
      System.out.println("Caught exception: " + exception.getMessage());
    } catch (ExceptionA exception) {
      System.out.println("Caught exception: " + exception.getMessage());
    } catch (Exception2 exception) {
      System.out.println("Caught exception: " + exception.getMessage());
    } catch (Exception1 exception) {
      System.out.println("Caught exception: " + exception.getMessage());
    }
  }

  private static void doSomethingThatMightThrowExceptionB() throws ExceptionB {
    throw new ExceptionB("Ouch!");
  }

  private static void doSomethingThatMightThrowException2() throws Exception2 {
    throw new Exception2("Ouch!");
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

  public static class Exception1 extends Exception {
    public Exception1(String message) {
      super(message);
    }
  }

  public static class Exception2 extends Exception1 {
    public Exception2(String message) {
      super(message);
    }
  }
}
