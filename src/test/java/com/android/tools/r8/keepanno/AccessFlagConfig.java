// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.keepanno.ast.AnnotationConstants.MemberAccess;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class AccessFlagConfig {

  public static List<AccessFlagConfig> MEMBER_CONFIGS =
      ImmutableList.of(
          // Member patterns.
          new AccessFlagConfig(MemberAccess.PUBLIC, Opcodes.ACC_PUBLIC),
          new AccessFlagConfig(MemberAccess.PROTECTED, Opcodes.ACC_PROTECTED),
          new AccessFlagConfig(MemberAccess.PRIVATE, Opcodes.ACC_PRIVATE),
          new AccessFlagConfig(MemberAccess.PACKAGE_PRIVATE, 0x0, Opcodes.ACC_PUBLIC),
          new AccessFlagConfig(MemberAccess.STATIC, Opcodes.ACC_STATIC),
          new AccessFlagConfig(MemberAccess.FINAL, Opcodes.ACC_FINAL),
          new AccessFlagConfig(MemberAccess.SYNTHETIC, Opcodes.ACC_SYNTHETIC),
          new AccessFlagConfig(MemberAccess.SYNTHETIC, Opcodes.ACC_SYNTHETIC));

  final String enumValue;
  final int positive;
  final int negative;

  public AccessFlagConfig(String enumValue, int access) {
    this.enumValue = enumValue;
    positive = access;
    negative = 0x0;
  }

  public AccessFlagConfig(String enumValue, int positive, int negative) {
    this.enumValue = enumValue;
    this.positive = positive;
    this.negative = negative;
  }

  @Override
  public String toString() {
    return enumValue;
  }

  public AccessFlagConfig invert() {
    assertFalse(enumValue.startsWith(MemberAccess.NEGATION_PREFIX));
    return new AccessFlagConfig(MemberAccess.NEGATION_PREFIX + enumValue, negative, positive);
  }
}
