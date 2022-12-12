// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceBooleanField {

  private boolean field;

  public static void testSet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceBooleanField instance = new InstanceBooleanField();
    System.out.println(varHandle.get(instance));

    // boolean and Boolean values.
    varHandle.set(instance, true);
    System.out.println((boolean) varHandle.get(instance));
    varHandle.set(instance, Boolean.FALSE);
    System.out.println(varHandle.get(instance));
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceBooleanField instance = new InstanceBooleanField();

    // boolean and Boolean values.
    varHandle.compareAndSet(instance, true, false);
    System.out.println((boolean) varHandle.get(instance));
    varHandle.compareAndSet(instance, false, true);
    System.out.println((boolean) varHandle.get(instance));
    varHandle.compareAndSet(instance, Boolean.TRUE, false);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, false, Boolean.TRUE);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Boolean.TRUE, Boolean.FALSE);
    System.out.println(varHandle.get(instance));
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle;
    try {
      varHandle =
          MethodHandles.lookup().findVarHandle(InstanceBooleanField.class, "field", boolean.class);
    } catch (UnsupportedOperationException e) {
      System.out.println("Got UnsupportedOperationException");
      return;
    }

    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
