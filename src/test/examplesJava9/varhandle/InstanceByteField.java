// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceByteField {

  private byte field;

  public static void testSet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceByteField instance = new InstanceByteField();
    System.out.println(varHandle.get(instance));

    // byte and Byte values.
    varHandle.set(instance, (byte) 1);
    System.out.println((byte) varHandle.get(instance));
    varHandle.set(instance, Byte.valueOf((byte) 2));
    System.out.println(varHandle.get(instance));
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceByteField instance = new InstanceByteField();

    // byte and Byte values.
    varHandle.compareAndSet(instance, (byte) 1, (byte) 2);
    System.out.println((byte) varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 0, (byte) 1);
    System.out.println((byte) varHandle.get(instance));
    varHandle.compareAndSet(instance, Byte.valueOf((byte) 1), (byte) 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 2, Byte.valueOf((byte) 3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Byte.valueOf((byte) 3), Byte.valueOf((byte) 4));
    System.out.println(varHandle.get(instance));
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle;
    try {
      varHandle =
          MethodHandles.lookup().findVarHandle(InstanceByteField.class, "field", byte.class);
    } catch (UnsupportedOperationException e) {
      System.out.println("Got UnsupportedOperationException");
      return;
    }

    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
