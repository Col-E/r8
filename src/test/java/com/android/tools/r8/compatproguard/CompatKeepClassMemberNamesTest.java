// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import com.android.tools.r8.NeverInline;

public class CompatKeepClassMemberNamesTest {

  public static class Bar {

    public int i = 42;

    @NeverInline
    public static Bar instance() {
      throw new RuntimeException();
    }
  }

  public static void main(String[] args) throws Exception {
    String junk = args.length > 0 ? "!" : "";
    // Ensure a non-instance reference to class Bar.
    Bar barReference = args.length > 0 ? Bar.instance() : null;
    // Reflectively construct bar and access its field while adding junk to ensure the reflection
    // is not identified.
    Object barObject =
        Class.forName(CompatKeepClassMemberNamesTest.class.getName() + "$B" + junk + "ar")
            .getDeclaredConstructor()
            .newInstance();
    int fieldValue = barObject.getClass().getDeclaredField(junk + "i").getInt(barObject);
    // Make sure the values cannot be eliminated.
    System.out.println(fieldValue);
    System.out.println(barReference);
  }
}
