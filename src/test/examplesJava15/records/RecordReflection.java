// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package records;

import java.util.Arrays;

public class RecordReflection {

  record Empty(){}

  record Person(String name, int age) {}

  record PersonGeneric <S extends CharSequence>(S name, int age) {}

  public static void main(String[] args) {
    System.out.println(Empty.class.isRecord());
    System.out.println(Arrays.toString(Empty.class.getRecordComponents()));
    System.out.println(Person.class.isRecord());
    System.out.println(Arrays.toString(Person.class.getRecordComponents()));
    System.out.println(PersonGeneric.class.isRecord());
    System.out.println(Arrays.toString(PersonGeneric.class.getRecordComponents()));
    System.out.println(Arrays.toString(PersonGeneric.class.getTypeParameters()));
    System.out.println(Object.class.isRecord());
    System.out.println(Arrays.toString(Object.class.getRecordComponents()));
  }

}
