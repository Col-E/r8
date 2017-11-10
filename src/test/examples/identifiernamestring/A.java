// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package identifiernamestring;

public class A {
  @IdentifierNameString
  static String foo = "identifiernamestring.B"; // should be renamed

  @IdentifierNameString
  String boo;

  A() {
    boo = "identifiernamestring.B"; // should be renamed
  }

  @IdentifierNameString
  void bar(String s) {
    System.out.println("identifiernamestring.B");
    System.out.println(boo);
    System.out.println(s); // renamed one will be passed
  }

  static String TYPE_A = "identifiernamestring.A"; // should be kept (restored)
  static String TYPE_B = "identifiernamestring.B"; // should be renamed
}
