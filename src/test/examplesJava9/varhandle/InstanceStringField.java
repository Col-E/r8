// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceStringField {

  private Object field;

  private static void println(String s) {
    System.out.println(s);
  }

  public static void testGet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceStringField instance = new InstanceStringField();

    // Then polymorphic invoke will remove the cast and make that as the return type of the get.
    System.out.println((String) varHandle.get(instance));
    varHandle.set(instance, "1");
    System.out.println(varHandle.get(instance));
    System.out.println((Object) varHandle.get(instance));
    System.out.println((String) varHandle.get(instance));
    System.out.println((CharSequence) varHandle.get(instance));
    try {
      System.out.println((Byte) varHandle.get(instance));
      System.out.println("Unexpected success");
    } catch (ClassCastException e) {
    }
  }

  public static void testSet(VarHandle varHandle) {
    System.out.println("testSet");

    InstanceStringField instance = new InstanceStringField();

    // Then polymorphic invoke will remove the cast and make that as the return type of the get.
    println((String) varHandle.get(instance));
    varHandle.set(instance, "1");
    println((String) varHandle.get(instance));
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceStringField instance = new InstanceStringField();

    varHandle.compareAndSet(instance, 0, "1");
    println((String) varHandle.get(instance));
    varHandle.compareAndSet(instance, null, "1");
    println((String) varHandle.get(instance));
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle =
        MethodHandles.lookup().findVarHandle(InstanceStringField.class, "field", Object.class);
    testGet(varHandle);
    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
