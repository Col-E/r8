// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.adapter;

public class AndroidVersionTest {

  public static final boolean is24OrAbove = setUp("java.util.StringJoiner");
  public static final boolean is26OrAbove = setUp("java.nio.file.FileSystems");
  public static final boolean isHeadfull = setUp("android.os.Build");

  /**
   * Answers true if the class is present, implying the SDK is at least at the level where the class
   * was introduced.
   */
  private static boolean setUp(String className) {
    try {
      Class.forName(className);
      return true;
    } catch (ClassNotFoundException ignored) {
    }
    return false;
  }
}
