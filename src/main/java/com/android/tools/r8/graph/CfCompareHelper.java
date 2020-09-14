// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.utils.ComparatorUtils;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.Comparator;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class CfCompareHelper {

  // Integer constants to ensure that there is a well order for all CF instructions including
  // virtual instructions represented in our internal encoding.
  public static final int CONST_CLASS_COMPARE_ID;
  public static final int CONST_STRING_COMPARE_ID;
  public static final int CONST_STRING_DEX_ITEM_COMPARE_ID;
  public static final int CONST_NUMBER_COMPARE_ID;
  public static final int CONST_METHOD_TYPE_COMPARE_ID;
  public static final int CONST_METHOD_HANDLE_COMPARE_ID;
  public static final int FRAME_COMPARE_ID;
  public static final int INIT_CLASS_COMPARE_ID;
  public static final int LABEL_COMPARE_ID;
  public static final int POSITION_COMPARE_ID;

  static {
    int lastId = Opcodes.IFNONNULL;
    CONST_CLASS_COMPARE_ID = ++lastId;
    CONST_STRING_COMPARE_ID = ++lastId;
    CONST_STRING_DEX_ITEM_COMPARE_ID = ++lastId;
    CONST_NUMBER_COMPARE_ID = ++lastId;
    CONST_METHOD_TYPE_COMPARE_ID = ++lastId;
    CONST_METHOD_HANDLE_COMPARE_ID = ++lastId;
    FRAME_COMPARE_ID = ++lastId;
    INIT_CLASS_COMPARE_ID = ++lastId;
    LABEL_COMPARE_ID = ++lastId;
    POSITION_COMPARE_ID = ++lastId;
  }

  // Helper to signal that the concrete instruction is uniquely determined by its ID/opcode.
  public static int compareIdUniquelyDeterminesEquality(
      CfInstruction instruction1, CfInstruction instruction2) {
    assert instruction1.getClass() == instruction2.getClass();
    assert instruction1.getCompareToId() == instruction2.getCompareToId();
    assert instruction1.toString().equals(instruction2.toString());
    return 0;
  }

  private final Reference2IntMap<CfLabel> labels1;
  private final Reference2IntMap<CfLabel> labels2;

  public CfCompareHelper(Reference2IntMap<CfLabel> labels1, Reference2IntMap<CfLabel> labels2) {
    this.labels1 = labels1;
    this.labels2 = labels2;
  }

  public int compareLabels(CfLabel label1, CfLabel label2) {
    return labels1.getInt(label1) - labels2.getInt(label2);
  }

  public Comparator<List<CfInstruction>> instructionComparator() {
    return ComparatorUtils.listComparator((x, y) -> x.compareTo(y, this));
  }

  public Comparator<List<CfTryCatch>> tryCatchRangesComparator() {
    return ComparatorUtils.listComparator((x, y) -> x.compareTo(y, this));
  }

  public Comparator<List<CfCode.LocalVariableInfo>> localVariablesComparator() {
    return ComparatorUtils.listComparator((x, y) -> x.compareTo(y, this));
  }
}
