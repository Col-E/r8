// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

/**
 * Android API level description
 */
public enum AndroidApiLevel {
  O(26),
  N_MR1(25),
  N(24),
  M(23),
  L_MR1(22),
  L(21),
  K_WATCH(20),
  K(19),
  J_MR2(18),
  J_MR1(17),
  J(16),
  I_MR1(15),
  I(14),
  H_MR2(13),
  H_MR1(12),
  H(11),
  G_MR1(10),
  G(9),
  F(8),
  E_MR1(7),
  E_0_1(6),
  E(5),
  D(4),
  C(3),
  B_1_1(2),
  B(1);

  private final int level;

  AndroidApiLevel(int level) {
    this.level = level;
  }

  public int getLevel() {
    return level;
  }

  public String getName() {
    return "Android " + name();
  }

  public static AndroidApiLevel getDefault() {
    return AndroidApiLevel.B;
  }
}
