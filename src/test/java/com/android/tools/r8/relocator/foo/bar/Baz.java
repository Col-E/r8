// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator.foo.bar;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.V1_8;

import org.objectweb.asm.ClassWriter;

public interface Baz {

  // This is dump of the empty interface Baz.
  static byte[] dump() {

    ClassWriter classWriter = new ClassWriter(0);

    classWriter.visit(
        V1_8,
        ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE,
        "foo/bar/Baz",
        null,
        "java/lang/Object",
        null);

    classWriter.visitSource("Baz.java", null);

    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
