// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.attributes;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class DataClassDumps implements Opcodes {

  public static byte[] dump18 () {

    ClassWriter classWriter = new ClassWriter(0);

    classWriter.visit(
        V1_8,
        ACC_SUPER | ACC_SYNTHETIC,
        "com/android/tools/r8/shaking/attributes/DataClass$1", null, "java/lang/Object", null);

    classWriter.visitSource("MissingEnclosingMethodTest.java", null);

    classWriter.visitOuterClass(
        "com/android/tools/r8/shaking/attributes/DataClass", null, null);

    classWriter.visitInnerClass(
        // Inner
        "com/android/tools/r8/shaking/attributes/DataClass$1",
        // Outer, intentionally changed from null.
        "com/android/tools/r8/shaking/attributes/DataClass",
        // Inner-name
        null,
        ACC_STATIC | ACC_SYNTHETIC);

    classWriter.visitEnd();

    return classWriter.toByteArray();
  }

  public static byte[] dump16 () {

    ClassWriter classWriter = new ClassWriter(0);

    classWriter.visit(
        V1_6,
        ACC_SUPER | ACC_SYNTHETIC,
        "com/android/tools/r8/shaking/attributes/DataClass$1", null, "java/lang/Object", null);

    classWriter.visitSource("MissingEnclosingMethodTest.java", null);

    classWriter.visitInnerClass(
        // Inner
        "com/android/tools/r8/shaking/attributes/DataClass$1",
        // Outer
        "com/android/tools/r8/shaking/attributes/DataClass",
        // Inner-name
        null,
        ACC_STATIC | ACC_SYNTHETIC);

    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
