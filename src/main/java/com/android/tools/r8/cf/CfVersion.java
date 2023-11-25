// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.HashCodeVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Arrays;
import java.util.stream.Collectors;
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
  public static final CfVersion V11_PREVIEW = new CfVersion(Opcodes.V11 | Opcodes.V_PREVIEW);
  public static final CfVersion V12 = new CfVersion(Opcodes.V12);
  public static final CfVersion V12_PREVIEW = new CfVersion(Opcodes.V12 | Opcodes.V_PREVIEW);
  public static final CfVersion V13 = new CfVersion(Opcodes.V13);
  public static final CfVersion V13_PREVIEW = new CfVersion(Opcodes.V13 | Opcodes.V_PREVIEW);
  public static final CfVersion V14 = new CfVersion(Opcodes.V14);
  public static final CfVersion V14_PREVIEW = new CfVersion(Opcodes.V14 | Opcodes.V_PREVIEW);
  public static final CfVersion V15 = new CfVersion(Opcodes.V15);
  public static final CfVersion V15_PREVIEW = new CfVersion(Opcodes.V15 | Opcodes.V_PREVIEW);
  public static final CfVersion V16 = new CfVersion(Opcodes.V16);
  public static final CfVersion V16_PREVIEW = new CfVersion(Opcodes.V16 | Opcodes.V_PREVIEW);
  public static final CfVersion V17 = new CfVersion(Opcodes.V17);
  public static final CfVersion V17_PREVIEW = new CfVersion(Opcodes.V17 | Opcodes.V_PREVIEW);
  public static final CfVersion V18 = new CfVersion(Opcodes.V18);
  public static final CfVersion V18_PREVIEW = new CfVersion(Opcodes.V18 | Opcodes.V_PREVIEW);
  public static final CfVersion V19 = new CfVersion(Opcodes.V19);
  public static final CfVersion V19_PREVIEW = new CfVersion(Opcodes.V19 | Opcodes.V_PREVIEW);
  public static final CfVersion V20 = new CfVersion(Opcodes.V20);
  public static final CfVersion V20_PREVIEW = new CfVersion(Opcodes.V20 | Opcodes.V_PREVIEW);
  public static final CfVersion V21 = new CfVersion(Opcodes.V21);
  public static final CfVersion V21_PREVIEW = new CfVersion(Opcodes.V21 | Opcodes.V_PREVIEW);
  public static final CfVersion V22 = new CfVersion(Opcodes.V22);
  public static final CfVersion V22_PREVIEW = new CfVersion(Opcodes.V22 | Opcodes.V_PREVIEW);

  private final int version;

  private static CfVersion[] versions = {
    CfVersion.V1_1,
    CfVersion.V1_2,
    CfVersion.V1_3,
    CfVersion.V1_4,
    CfVersion.V1_5,
    CfVersion.V1_6,
    CfVersion.V1_7,
    CfVersion.V1_8,
    CfVersion.V9,
    CfVersion.V10,
    CfVersion.V11,
    CfVersion.V12,
    CfVersion.V13,
    CfVersion.V14,
    CfVersion.V15,
    CfVersion.V16,
    CfVersion.V17,
    CfVersion.V18,
    CfVersion.V19,
    CfVersion.V20,
    CfVersion.V21,
    CfVersion.V22
  };

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
    return version >>> 16;
  }

  public int raw() {
    return version;
  }

  public boolean isPreview() {
    return minor() == Opcodes.V_PREVIEW >>> 16;
  }

  private static void specify(StructuralSpecification<CfVersion, ?> spec) {
    spec.withInt(CfVersion::major).withInt(CfVersion::minor);
  }

  public static Iterable<CfVersion> all() {
    return rangeInclusive(versions[0], versions[versions.length - 1]);
  }

  public static Iterable<CfVersion> rangeInclusive(CfVersion from, CfVersion to) {
    assert from.isLessThanOrEqualTo(to);
    assert !from.isPreview() : "This method does not handle preview versions";
    assert !to.isPreview() : "This method does not handle preview versions";
    return Arrays.stream(versions)
        .filter(version -> version.isGreaterThanOrEqualTo(from))
        .filter(version -> version.isLessThanOrEqualTo(to))
        .collect(Collectors.toList());
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
    return HashCodeVisitor.run(this);
  }

  @Override
  public String toString() {
    return minor() != 0 ? ("" + major() + "." + minor()) : ("" + major());
  }
}
