// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class InstanceIntField {

  private int field;

  private static void checkJavaLangInvokeWrongMethodTypeException(RuntimeException e) {
    if (e.getClass().getCanonicalName().equals("java.lang.invoke.WrongMethodTypeException")
        || e.getMessage().equals("java.lang.invoke.WrongMethodTypeException")) {
      return;
    }
    throw e;
  }

  public static void testGet(VarHandle varHandle) {
    System.out.println("testGet");

    InstanceIntField instance = new InstanceIntField();
    varHandle.set(instance, 1);

    System.out.println(varHandle.get(instance));
    System.out.println((Object) varHandle.get(instance));
    System.out.println((int) varHandle.get(instance));
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
      System.out.println((String) varHandle.get(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testGetVolatile(VarHandle varHandle) {
    System.out.println("testGetVolatile");

    InstanceIntField instance = new InstanceIntField();
    varHandle.set(instance, 1);

    System.out.println(varHandle.getVolatile(instance));
    System.out.println((Object) varHandle.getVolatile(instance));
    System.out.println((int) varHandle.getVolatile(instance));
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
      System.out.println((String) varHandle.getVolatile(instance));
      System.out.println("Unexpected success");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
    }
  }

  public static void testSet(VarHandle varHandle) {
    System.out.println("testSet");

    InstanceIntField instance = new InstanceIntField();
    System.out.println((int) varHandle.get(instance));

    // int and Integer values.
    varHandle.set(instance, (int) 1);
    System.out.println((int) varHandle.get(instance));
    varHandle.set(instance, Integer.valueOf(2));
    System.out.println(varHandle.get(instance));

    // int and Integer compatible values.
    varHandle.set(instance, (byte) 3);
    System.out.println((int) varHandle.get(instance));
    varHandle.set(instance, Byte.valueOf((byte) 4));
    System.out.println((int) varHandle.get(instance));
    varHandle.set(instance, '0');
    System.out.println((int) varHandle.get(instance));
    varHandle.set(instance, Character.valueOf('1'));
    System.out.println((int) varHandle.get(instance));
    varHandle.set(instance, (short) 5);
    System.out.println((int) varHandle.get(instance));
    varHandle.set(instance, Short.valueOf((short) 6));
    System.out.println((int) varHandle.get(instance));

    // int and Integer non-compatible values.
    try {
      varHandle.set(instance, true);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.set(instance, 3L);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.set(instance, Long.valueOf(3));
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

  public static void testSetVolatile(VarHandle varHandle) {
    System.out.println("testSetVolatile");

    InstanceIntField instance = new InstanceIntField();
    System.out.println((int) varHandle.get(instance));

    // int and Integer values.
    varHandle.setVolatile(instance, (int) 1);
    System.out.println((int) varHandle.get(instance));
    varHandle.setVolatile(instance, Integer.valueOf(2));
    System.out.println(varHandle.get(instance));

    // int and Integer compatible values.
    varHandle.setVolatile(instance, (byte) 3);
    System.out.println((int) varHandle.get(instance));
    varHandle.setVolatile(instance, Byte.valueOf((byte) 4));
    System.out.println((int) varHandle.get(instance));
    varHandle.setVolatile(instance, '0');
    System.out.println((int) varHandle.get(instance));
    varHandle.setVolatile(instance, Character.valueOf('1'));
    System.out.println((int) varHandle.get(instance));
    varHandle.setVolatile(instance, (short) 5);
    System.out.println((int) varHandle.get(instance));
    varHandle.setVolatile(instance, Short.valueOf((short) 6));
    System.out.println((int) varHandle.get(instance));

    // int and Integer non-compatible values.
    try {
      varHandle.setVolatile(instance, true);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.setVolatile(instance, 3L);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.setVolatile(instance, Long.valueOf(3));
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.setVolatile(instance, "3");
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.setVolatile(instance, 3.0f);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.setVolatile(instance, 3.0);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
  }

  public static void testCompareAndSet(VarHandle varHandle) {
    System.out.println("testCompareAndSet");

    InstanceIntField instance = new InstanceIntField();

    // int and Integer values.
    varHandle.compareAndSet(instance, 1, 2);
    System.out.println((int) varHandle.get(instance));
    varHandle.compareAndSet(instance, 0, 1);
    System.out.println((int) varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(1), 2);
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, 2, Integer.valueOf(3));
    System.out.println(varHandle.get(instance));
    varHandle.compareAndSet(instance, Integer.valueOf(3), Integer.valueOf(4));
    System.out.println(varHandle.get(instance));

    // int and Integer compatible values.
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

    // int and Integer non-compatible values.
    try {
      varHandle.compareAndSet(instance, 6L, 7L);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, 6L, 7);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, 6, Long.valueOf(7));
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, Long.valueOf(6), 7);
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
    try {
      varHandle.compareAndSet(instance, Long.valueOf(6), Long.valueOf(7));
    } catch (RuntimeException e) {
      checkJavaLangInvokeWrongMethodTypeException(e);
      System.out.println(varHandle.get(instance));
    }
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
        MethodHandles.lookup().findVarHandle(InstanceIntField.class, "field", int.class);
    testGet(varHandle);
    testGetVolatile(varHandle);
    testSet(varHandle);
    testSetVolatile(varHandle);
    testCompareAndSet(varHandle);
  }
}
