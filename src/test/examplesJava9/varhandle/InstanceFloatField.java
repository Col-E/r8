// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceFloatField {

  private float field;

  public static void testSet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceFloatField instance = new InstanceFloatField();
    System.out.println(varHandle.get(instance));

    // float and Float values.
    varHandle.set(instance, 1.0f);
    System.out.println((float) varHandle.get(instance));
    varHandle.set(instance, Float.valueOf(2));
    System.out.println(varHandle.get(instance));
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceFloatField instance = new InstanceFloatField();

    // float and Float values.
    varHandle.compareAndSet(instance, 1.0f, 2.0f);
    System.out.println((float) varHandle.get(instance));
    varHandle.compareAndSet(instance, 0.0f, 1.0f);
    System.out.println((float) varHandle.get(instance));
    varHandle.compareAndSet(instance, Float.valueOf(1), 2.0f);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 2.0f, Float.valueOf(3f));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Float.valueOf(3), Float.valueOf(4));
    System.out.println(varHandle.get(instance));
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle;
    try {
      varHandle =
          MethodHandles.lookup().findVarHandle(InstanceFloatField.class, "field", float.class);
    } catch (UnsupportedOperationException e) {
      System.out.println("Got UnsupportedOperationException");
      return;
    }

    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
