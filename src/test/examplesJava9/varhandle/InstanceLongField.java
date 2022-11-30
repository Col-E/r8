// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceLongField {

  private long field;

  public static void testSet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceLongField instance = new InstanceLongField();

    System.out.println(varHandle.get(instance));
    varHandle.set(instance, 1);
    System.out.println(varHandle.get(instance));
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceLongField instance = new InstanceLongField();

    varHandle.compareAndSet(instance, 1, 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 0, 1);
    System.out.println(varHandle.get(instance));
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle =
        MethodHandles.lookup().findVarHandle(InstanceLongField.class, "field", long.class);
    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
