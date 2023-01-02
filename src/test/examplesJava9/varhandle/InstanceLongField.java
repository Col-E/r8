// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceLongField {

  private long field;

  private static void checkJavaLangInvokeWrongMethodTypeException(RuntimeException e) {
    if (e.getClass().getCanonicalName().equals("java.lang.invoke.WrongMethodTypeException")
        || e.getMessage().equals("java.lang.invoke.WrongMethodTypeException")) {
      return;
    }
    throw e;
  }

  public static void testGet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceLongField instance = new InstanceLongField();
    varHandle.set(instance, 1L);

    System.out.println(varHandle.get(instance));
    System.out.println((Object) varHandle.get(instance));
    System.out.println((long) varHandle.get(instance));
    System.out.println((float) varHandle.get(instance));
    System.out.println((double) varHandle.get(instance));
    try {
      System.out.println((boolean) varHandle.get(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) varHandle.get(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) varHandle.get(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) varHandle.get(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) varHandle.get(instance));
      System.out.println("Unexpected success 5");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) varHandle.get(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testGetVolatile(VarHandle varHandle) {
    System.out.println("testGetVolatile");

    InstanceLongField instance = new InstanceLongField();
    varHandle.set(instance, 1);

    System.out.println(varHandle.getVolatile(instance));
    System.out.println((Object) varHandle.getVolatile(instance));
    System.out.println((long) varHandle.getVolatile(instance));
    System.out.println((float) varHandle.getVolatile(instance));
    System.out.println((double) varHandle.getVolatile(instance));
    try {
      System.out.println((boolean) varHandle.getVolatile(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((byte) varHandle.getVolatile(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((short) varHandle.getVolatile(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((char) varHandle.getVolatile(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((int) varHandle.getVolatile(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
    try {
      System.out.println((String) varHandle.getVolatile(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testSet(VarHandle varHandle) {
    System.out.println("testSet");

    InstanceLongField instance = new InstanceLongField();
    System.out.println((long) varHandle.get(instance));

    // Long value.
    varHandle.set(instance, (long) 1);
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, Long.valueOf(2));
    System.out.println(varHandle.get(instance));

    // Long compatible values.
    varHandle.set(instance, (byte) 3);
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, Byte.valueOf((byte) 4));
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, '0');
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, Character.valueOf('1'));
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, (short) 5);
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, Short.valueOf((short) 6));
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, (int) 7);
    System.out.println((long) varHandle.get(instance));
    varHandle.set(instance, Integer.valueOf(8));
    System.out.println((long) varHandle.get(instance));

    // Long non-compatible values.
    try {
      varHandle.set(instance, true);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.set(instance, "3");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.set(instance, 3.0f);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.set(instance, 3.0);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceLongField instance = new InstanceLongField();

    // Long value.
    varHandle.compareAndSet(instance, 1L, 2L);
    System.out.println((long) varHandle.get(instance));
    varHandle.compareAndSet(instance, 0L, 1L);
    System.out.println((long) varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(1), 2);
    System.out.println((long) varHandle.get(instance));
    varHandle.compareAndSet(instance, 2, Long.valueOf(3));
    System.out.println((long) varHandle.get(instance));
    varHandle.compareAndSet(instance, Long.valueOf(3), Long.valueOf(4));
    System.out.println((long) varHandle.get(instance));

    // Long compatible values.
    varHandle.compareAndSet(instance, (byte) 4, 5);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 5, (byte) 6);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 6, (byte) 7);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Byte.valueOf((byte) 7), (byte) 8);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 8, Byte.valueOf((byte) 9));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Byte.valueOf((byte) 9), Byte.valueOf((byte) 10));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 10, '0');
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, '0', 49);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, '1', '2');
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (byte) 50, '3');
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, '3', (byte) 52);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, '4', '5');
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, '5', (int) 11);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (int) 11, (int) 12);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, (int) 12, Integer.valueOf(13));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(13), Integer.valueOf(14));
    System.out.println(varHandle.get(instance));

    // Long non-compatible values.
    try {
      varHandle.compareAndSet(instance, 6, 7.0f);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, 6.0f, 7);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, 6.0f, 7.0f);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, 6, 7.0);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, 6.0, 7);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, 6.0, 7.0);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }

    try {
      varHandle.compareAndSet(instance, 6, "7");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, "6", 7);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, "6", "7");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
  }

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    VarHandle varHandle =
        MethodHandles.lookup().findVarHandle(InstanceLongField.class, "field", long.class);
    testGet(varHandle);
    testGetVolatile(varHandle);
    testSet(varHandle);
    testCompareAndSet(varHandle);
  }
}
