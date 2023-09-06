// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.structural.Ordered;
import java.util.Optional;

/** Android dex version */
public enum DexVersion implements Ordered<DexVersion> {
  V35(35, new byte[] {'0', '3', '5'}, Layout.SINGLE_DEX),
  V37(37, new byte[] {'0', '3', '7'}, Layout.SINGLE_DEX),
  V38(38, new byte[] {'0', '3', '8'}, Layout.SINGLE_DEX),
  V39(39, new byte[] {'0', '3', '9'}, Layout.SINGLE_DEX),
  V40(40, new byte[] {'0', '4', '0'}, Layout.SINGLE_DEX),
  V41(41, new byte[] {'0', '4', '1'}, Layout.CONTAINER_DEX);

  public enum Layout {
    SINGLE_DEX,
    CONTAINER_DEX;

    public boolean isContainer() {
      return this == CONTAINER_DEX;
    }

    public int getHeaderSize() {
      return isContainer() ? Constants.TYPE_HEADER_ITEM_SIZE_V41 : Constants.TYPE_HEADER_ITEM_SIZE;
    }
  }

  private final int dexVersion;
  private final byte[] dexVersionBytes;
  private final Layout layout;

  DexVersion(int dexVersion, byte[] dexVersionBytes, Layout layout) {
    this.dexVersion = dexVersion;
    this.dexVersionBytes = dexVersionBytes;
    this.layout = layout;
  }

  public Layout getLayout() {
    return layout;
  }

  public boolean isContainerDex() {
    return getLayout() == Layout.CONTAINER_DEX;
  }

  public int getIntValue() {
    return dexVersion;
  }

  public byte[] getBytes() {
    return dexVersionBytes;
  }

  public boolean matchesApiLevel(AndroidApiLevel androidApiLevel) {
    return getDexVersion(androidApiLevel).dexVersion >= dexVersion;
  }

  public static DexVersion getDexVersion(AndroidApiLevel androidApiLevel) {
    switch (androidApiLevel) {
        // MASTER is an unknown higher api version we therefore choose the highest known version.
      case MASTER:
      case U:
      case T:
      case Sv2:
      case S:
      case R:
        // Dex version should have been V40 starting from API level 30, see b/269089718.
        // return DexVersion.V40;
      case Q:
      case P:
        return DexVersion.V39;
      case O_MR1:
      case O:
        return DexVersion.V38;
      case N_MR1:
      case N:
        return DexVersion.V37;
      case B:
      case B_1_1:
      case C:
      case D:
      case E:
      case E_0_1:
      case E_MR1:
      case F:
      case G:
      case G_MR1:
      case H:
      case H_MR1:
      case H_MR2:
      case I:
      case I_MR1:
      case J:
      case J_MR1:
      case J_MR2:
      case K:
      case K_WATCH:
      case L:
      case L_MR1:
      case M:
        return DexVersion.V35;
      default:
        throw new Unreachable("Unsupported api level " + androidApiLevel);
    }
  }

  public static Optional<DexVersion> getDexVersion(int intValue) {
    switch (intValue) {
      case 35:
        return Optional.of(V35);
      case 37:
        return Optional.of(V37);
      case 38:
        return Optional.of(V38);
      case 39:
        return Optional.of(V39);
      case 40:
        return Optional.of(V40);
      case 41:
        return Optional.of(V41);
      default:
        return Optional.empty();
    }
  }

  public static Optional<DexVersion> getDexVersion(char b0, char b1, char b2) {
    if (b0 != '0') {
      return Optional.empty();
    }
    for (DexVersion candidate : DexVersion.values()) {
      assert candidate.getBytes()[0] == '0';
      if (candidate.getBytes()[2] == b2 && candidate.getBytes()[1] == b1) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
