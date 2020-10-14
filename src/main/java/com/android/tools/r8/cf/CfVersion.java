// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import java.util.Comparator;
import org.objectweb.asm.Opcodes;

public final class CfVersion implements Comparable<CfVersion> {

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

  public static CfVersion maxAllowNull(CfVersion v1, CfVersion v2) {
    assert v1 != null || v2 != null;
    if (v1 == null) {
      return v2;
    }
    if (v2 == null) {
      return v1;
    }
    return v1.max(v2);
  }

  public CfVersion max(CfVersion other) {
    return isLessThan(other) ? other : this;
  }

  public boolean isEqual(CfVersion other) {
    return version == other.version;
  }

  public boolean isLessThan(CfVersion other) {
    return compareTo(other) < 0;
  }

  public boolean isLessThanOrEqual(CfVersion other) {
    return compareTo(other) <= 0;
  }

  public boolean isGreaterThan(CfVersion other) {
    return compareTo(other) > 0;
  }

  public boolean isGreaterThanOrEqual(CfVersion other) {
    return compareTo(other) >= 0;
  }

  @Override
  public int compareTo(CfVersion o) {
    return Comparator.comparingInt(CfVersion::major)
        .thenComparingInt(CfVersion::minor)
        .compare(this, o);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CfVersion)) {
      return false;
    }
    return isEqual((CfVersion) o);
  }

  @Override
  public int hashCode() {
    return version;
  }

  @Override
  public String toString() {
    return minor() != 0 ? ("" + major() + "." + minor()) : ("" + major());
  }
}
