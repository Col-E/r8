// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceDoubleField {

  private double field;

  public static void testSet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceDoubleField instance = new InstanceDoubleField();
    System.out.println(varHandle.get(instance));

    // double and Double values.
    varHandle.set(instance, 1.0);
    System.out.println((double) varHandle.get(instance));
    varHandle.set(instance, Double.valueOf(2));
    System.out.println(varHandle.get(instance));
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceDoubleField instance = new InstanceDoubleField();

    // double and Double values.
    varHandle.compareAndSet(instance, 1.0, 2.0);
    System.out.println((double) varHandle.get(instance));
    varHandle.compareAndSet(instance, 0.0, 1.0);
    System.out.println((double) varHandle.get(instance));
    varHandle.compareAndSet(instance, Double.valueOf(1), 2.0);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 2.0, Double.valueOf(3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Double.valueOf(3), Double.valueOf(4));
    System.out.println(varHandle.get(instance));
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle;
    try {
      varHandle =
          MethodHandles.lookup().findVarHandle(InstanceDoubleField.class, "field", double.class);
    } catch (UnsupportedOperationException e) {
      System.out.println("Got UnsupportedOperationException");
      return;
    }

    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
