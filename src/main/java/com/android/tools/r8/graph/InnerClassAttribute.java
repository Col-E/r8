// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import org.objectweb.asm.ClassWriter;

/** Representation of an entry in the Java InnerClasses attribute table. */
public class InnerClassAttribute {

  // Access flags to the inner class as declared in the program source.
  private final int access;

  // Type of the inner class.
  private final DexType inner;

  // Type of the enclosing class, null if the inner class is top-level, local or anonymous.
  private final DexType outer;

  // Short name of the inner class, null if the inner class is anonymous.
  private final DexString innerName;

  // Create a named inner-class attribute, but with some arbitrary/unknown name.
  // This is needed to partially map back to the Java attribute structures when reading DEX inputs.
  public static InnerClassAttribute createUnknownNamedInnerClass(DexType inner, DexType outer) {
    return new InnerClassAttribute(0, inner, outer, DexItemFactory.unknownTypeName);
  }

  public InnerClassAttribute(int access, DexType inner, DexType outer, DexString innerName) {
    assert inner != null;
    this.access = access;
    this.inner = inner;
    this.outer = outer;
    this.innerName = innerName;
  }

  public boolean isNamed() {
    return innerName != null;
  }

  public boolean isAnonymous() {
    return innerName == null;
  }

  public int getAccess() {
    return access;
  }

  public DexType getInner() {
    return inner;
  }

  public DexType getOuter() {
    return outer;
  }

  public DexString getInnerName() {
    return innerName;
  }

  public void write(ClassWriter writer) {
    writer.visitInnerClass(
        inner.getInternalName(),
        outer == null ? null : outer.getInternalName(),
        innerName == null ? null : innerName.toString(),
        access);
  }
}
