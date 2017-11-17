// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package identifiernamestring;

public class B {
  static String foo = "identifiernamestring.B";
  static final String TYPO_A = "identifiernamestring.A";
  static final String TYPO_B = "identifiernamestring.B";

  static String boo(A a) {
    return a.boo;
  }
}
