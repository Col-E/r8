// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceShortField {

  private short field;

  public static void testSet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceShortField instance = new InstanceShortField();
    System.out.println(varHandle.get(instance));

    // short and Short values.
    varHandle.set(instance, (short) 1);
    System.out.println((short) varHandle.get(instance));
    varHandle.set(instance, Short.valueOf((short) 2));
    System.out.println(varHandle.get(instance));
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceShortField instance = new InstanceShortField();

    // short and Short values.
    varHandle.compareAndSet(instance, (short) 1, (short) 2);
    System.out.println((short) varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 0, (short) 1);
    System.out.println((short) varHandle.get(instance));
    varHandle.compareAndSet(instance, Short.valueOf((short) 1), (short) 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (short) 2, Short.valueOf((short) 3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Short.valueOf((short) 3), Short.valueOf((short) 4));
    System.out.println(varHandle.get(instance));
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle;
    try {
      varHandle =
          MethodHandles.lookup().findVarHandle(InstanceShortField.class, "field", short.class);
    } catch (UnsupportedOperationException e) {
      System.out.println("Got UnsupportedOperationException");
      return;
    }

    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
