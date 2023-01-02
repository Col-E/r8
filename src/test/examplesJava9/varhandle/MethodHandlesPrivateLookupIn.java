// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package varhandle;

import java.lang.invoke.MethodHandles;
import varhandle.util.WithPrivateFields;

public class MethodHandlesPrivateLookupIn {

  public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {
    MethodHandles.Lookup lookup = MethodHandles.lookup();

    WithPrivateFields withPrivateFields = new WithPrivateFields();
    try {
      lookup.findVarHandle(WithPrivateFields.class, "intField", int.class).get(withPrivateFields);
      System.out.println("Unexpected success");
    } catch (IllegalAccessException e) {
    }
    System.out.println(
        MethodHandles.privateLookupIn(WithPrivateFields.class, lookup)
            .findVarHandle(WithPrivateFields.class, "intField", int.class)
            .get(withPrivateFields));

    try {
      lookup.findVarHandle(WithPrivateFields.class, "longField", long.class).get(withPrivateFields);
      System.out.println("Unexpected success");
    } catch (IllegalAccessException e) {
    }
    System.out.println(
        MethodHandles.privateLookupIn(WithPrivateFields.class, lookup)
            .findVarHandle(WithPrivateFields.class, "longField", long.class)
            .get(withPrivateFields));

    try {
      lookup
          .findVarHandle(WithPrivateFields.class, "referenceField", Object.class)
          .get(withPrivateFields);
      System.out.println("Unexpected success");
    } catch (IllegalAccessException e) {
    }
    System.out.println(
        MethodHandles.privateLookupIn(WithPrivateFields.class, lookup)
            .findVarHandle(WithPrivateFields.class, "referenceField", Object.class)
            .get(withPrivateFields));
  }
}
