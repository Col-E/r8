// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records.differentpackage;

public class PrivateConstClass {

  private static class PrivateClass {}

  public static Class<?> getPrivateConstClass() {
    return PrivateClass.class;
  }
}
