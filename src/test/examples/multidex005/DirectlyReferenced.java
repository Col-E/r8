// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package multidex005;

public class DirectlyReferenced extends SuperClass implements Interface1, Interface2 {

  private IndirectlyReferenced reference = new IndirectlyReferenced();

  public static Object field = null;

  public static Object get() {
    return null;
  }
}
