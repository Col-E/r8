// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.structural.Ordered;
import java.util.Arrays;
import java.util.List;

/** Android API level description */
public enum AndroidApiLevel implements Ordered<AndroidApiLevel> {
  B(1),
  B_1_1(2),
  C(3),
  D(4),
  E(5),
  E_0_1(6),
  E_MR1(7),
  F(8),
  G(9),
  G_MR1(10),
  H(11),
  H_MR1(12),
  H_MR2(13),
  I(14),
  I_MR1(15),
  J(16),
  J_MR1(17),
  J_MR2(18),
  K(19),
  K_WATCH(20),
  L(21),
  L_MR1(22),
  M(23),
  N(24),
  N_MR1(25),
  O(26),
  O_MR1(27),
  P(28),
  Q(29),
  R(30),
  S(31),
  Sv2(32),
  T(33),
  U(34),
  MASTER(35), // API level for master is tentative.
  ANDROID_PLATFORM(10000);

  // When updating LATEST and a new version goes public, add a new api-versions.xml to third_party
  // and update the version and generated jar in AndroidApiDatabaseBuilderGeneratorTest. Together
  // with that update third_party/android_jar/libcore_latest/core-oj.jar and run
  // GenerateCovariantReturnTypeMethodsTest.
  public static final AndroidApiLevel LATEST = U;

  public static final AndroidApiLevel API_DATABASE_LEVEL = LATEST;

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

  public AndroidApiLevel max(AndroidApiLevel other) {
    return Ordered.max(this, other);
  }

  public DexVersion getDexVersion() {
    return DexVersion.getDexVersion(this);
  }

  public AndroidApiLevel next() {
    return getAndroidApiLevel(getLevel() + 1);
  }

  public static List<AndroidApiLevel> getAndroidApiLevelsSorted() {
    return Arrays.asList(AndroidApiLevel.values());
  }

  public static AndroidApiLevel getMinAndroidApiLevel(DexVersion dexVersion) {
    switch (dexVersion) {
      case V35:
        return AndroidApiLevel.B;
      case V37:
        return AndroidApiLevel.N;
      case V38:
        return AndroidApiLevel.O;
      case V39:
        return AndroidApiLevel.P;
      case V40:
        return AndroidApiLevel.R;
      case V41:
        return AndroidApiLevel.ANDROID_PLATFORM;
      default:
        throw new Unreachable();
    }
  }

  public static AndroidApiLevel getAndroidApiLevel(int apiLevel) {
    assert apiLevel > 0;
    assert U == LATEST; // This has to be updated when we add new api levels.
    assert ANDROID_PLATFORM.isGreaterThan(LATEST);
    switch (apiLevel) {
      case 1:
        return B;
      case 2:
        return B_1_1;
      case 3:
        return C;
      case 4:
        return D;
      case 5:
        return E;
      case 6:
        return E_0_1;
      case 7:
        return E_MR1;
      case 8:
        return F;
      case 9:
        return G;
      case 10:
        return G_MR1;
      case 11:
        return H;
      case 12:
        return H_MR1;
      case 13:
        return H_MR2;
      case 14:
        return I;
      case 15:
        return I_MR1;
      case 16:
        return J;
      case 17:
        return J_MR1;
      case 18:
        return J_MR2;
      case 19:
        return K;
      case 20:
        return K_WATCH;
      case 21:
        return L;
      case 22:
        return L_MR1;
      case 23:
        return M;
      case 24:
        return N;
      case 25:
        return N_MR1;
      case 26:
        return O;
      case 27:
        return O_MR1;
      case 28:
        return P;
      case 29:
        return Q;
      case 30:
        return R;
      case 31:
        return S;
      case 32:
        return Sv2;
      case 33:
        return T;
      case 34:
        return U;
      case 35:
        return MASTER;
      case 10000:
        return ANDROID_PLATFORM;
      default:
        return LATEST;
    }
  }
}
