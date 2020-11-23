// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.HashCodeVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import org.objectweb.asm.Opcodes;

public final class CfVersion implements StructuralItem<CfVersion> {

  public static final CfVersion V1_1 = new CfVersion(Opcodes.V1_1);
  public static final CfVersion V1_2 = new CfVersion(Opcodes.V1_2);
  public static final CfVersion V1_3 = new CfVersion(Opcodes.V1_3);
  public static final CfVersion V1_4 = new CfVersion(Opcodes.V1_4);
  public static final CfVersion V1_5 = new CfVersion(Opcodes.V1_5);
  public static final CfVersion V1_6 = new CfVersion(Opcodes.V1_6);
  public static final CfVersion V1_7 = new CfVersion(Opcodes.V1_7);
  public static final CfVersion V1_8 = new CfVersion(Opcodes.V1_8);
  public static final CfVersion V9 = new CfVersion(Opcodes.V9);
  public static final CfVersion V10 = new CfVersion(Opcodes.V10);
  public static final CfVersion V11 = new CfVersion(Opcodes.V11);

  private final int version;

  // Private constructor in case we want to canonicalize versions.
  private CfVersion(int version) {
    this.version = version;
  }

  public static CfVersion fromRaw(int rawVersion) {
    return new CfVersion(rawVersion);
  }

  public int major() {
    return version & 0xFFFF;
  }

  public int minor() {
    return version >> 16;
  }

  public int raw() {
    return version;
  }

  private static void specify(StructuralSpecification<CfVersion, ?> spec) {
    spec.withInt(CfVersion::major).withInt(CfVersion::minor);
  }

  @Override
  public CfVersion self() {
    return this;
  }

  @Override
  public StructuralMapping<CfVersion> getStructuralMapping() {
    return CfVersion::specify;
  }

  @Override
  public boolean equals(Object o) {
    return Equatable.equalsImpl(this, o);
  }

  @Override
  public int hashCode() {
    return HashCodeVisitor.run(this, CfVersion::specify);
  }

  @Override
  public String toString() {
    return minor() != 0 ? ("" + major() + "." + minor()) : ("" + major());
  }
}
