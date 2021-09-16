// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import java.lang.reflect.Method;

public class UnusedRecordReflection {

  Record instanceField;

  Record method(int i, Record unused, int j) {
    return null;
  }

  Object reflectiveGetField() {
    try {
      return this.getClass().getDeclaredField("instanceField").get(this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  Object reflectiveCallMethod() {
    try {
      for (Method declaredMethod : this.getClass().getDeclaredMethods()) {
        if (declaredMethod.getName().equals("method")) {
          return declaredMethod.invoke(this, 0, null, 1);
        }
      }
      throw new RuntimeException("Unreachable");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    System.out.println(new UnusedRecordReflection().reflectiveGetField());
    System.out.println(new UnusedRecordReflection().reflectiveCallMethod());
  }
}
