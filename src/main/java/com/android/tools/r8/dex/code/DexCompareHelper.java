// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

public class DexCompareHelper {

  // Integer constants to ensure that there is a well order for all DEX instructions including
  // virtual instructions represented in our internal encoding.
  static final int INIT_CLASS_COMPARE_ID;
  static final int DEX_ITEM_CONST_STRING_COMPARE_ID;
  static final int DEX_RECORD_FIELD_VALUES_COMPARE_ID;

  private static final int HIGHEST_DEX_OPCODE = 0xFF;

  static {
    int lastId = HIGHEST_DEX_OPCODE;
    INIT_CLASS_COMPARE_ID = ++lastId;
    DEX_ITEM_CONST_STRING_COMPARE_ID = ++lastId;
    DEX_RECORD_FIELD_VALUES_COMPARE_ID = ++lastId;
  }

  // Helper to signal that the concrete instruction is uniquely determined by its ID/opcode.
  public static int compareIdUniquelyDeterminesEquality(
      DexInstruction instruction1, DexInstruction instruction2) {
    assert instruction1.getClass() == instruction2.getClass();
    assert instruction1.getCompareToId() == instruction2.getCompareToId();
    assert instruction1.toString().equals(instruction2.toString());
    return 0;
  }
}
