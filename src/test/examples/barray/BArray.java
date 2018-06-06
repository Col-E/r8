// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package barray;

public class BArray {

  public static void main(String[] args) {
    System.out.println("null boolean: " + readNullBooleanArray());
    System.out.println("null byte: " + readNullByteArray());
    System.out.println("boolean: " + readBooleanArray(writeBooleanArray(args)));
    System.out.println("byte: " + readByteArray(writeByteArray(args)));
  }

  public static boolean readNullBooleanArray() {
    boolean[] boolArray = null;
    try {
      return boolArray[0] || boolArray[1];
    } catch (Throwable e) {
      return true;
    }
  }

  public static byte readNullByteArray() {
    byte[] byteArray = null;
    try {
      return byteArray[0];
    } catch (Throwable e) {
      return 42;
    }
  }

  public static boolean[] writeBooleanArray(String[] args) {
    boolean[] array = new boolean[args.length];
    for (int i = 0; i < args.length; i++) {
      array[i] = args[i].length() == 42;
    }
    return array;
  }

  public static byte[] writeByteArray(String[] args) {
    byte[] array = new byte[args.length];
    for (int i = 0; i < args.length; i++) {
      array[i] = (byte) args[i].length();
    }
    return array;
  }

  public static boolean readBooleanArray(boolean[] boolArray) {
    try {
      return boolArray[0] || boolArray[1];
    } catch (Throwable e) {
      return true;
    }
  }

  public static byte readByteArray(byte[] byteArray) {
    try {
      return byteArray[0];
    } catch (Throwable e) {
      return 42;
    }
  }
}
