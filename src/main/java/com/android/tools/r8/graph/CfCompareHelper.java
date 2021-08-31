// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralAcceptor;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
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
  public static final int CONST_DYNAMIC_COMPARE_ID;
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
    CONST_DYNAMIC_COMPARE_ID = ++lastId;
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

  private static Reference2IntMap<CfLabel> getLabelOrdering(CfCode code) {
    Reference2IntMap<CfLabel> ordering = new Reference2IntOpenHashMap<>();
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isLabel()) {
        ordering.put(instruction.asLabel(), ordering.size());
      }
    }
    return ordering;
  }

  private final CfCode code1;
  private final CfCode code2;
  private StructuralAcceptor<CfLabel> lazyLabelAcceptor = null;

  public CfCompareHelper(CfCode code1, CfCode code2) {
    this.code1 = code1;
    this.code2 = code2;
  }

  public int compareLabels(CfLabel label1, CfLabel label2, CompareToVisitor visitor) {
    return labelAcceptor().acceptCompareTo(label1, label2, visitor);
  }

  public StructuralAcceptor<CfLabel> labelAcceptor() {
    if (lazyLabelAcceptor == null) {
      lazyLabelAcceptor =
          new StructuralAcceptor<CfLabel>() {
            private final Reference2IntMap<CfLabel> labels1 = getLabelOrdering(code1);
            private final Reference2IntMap<CfLabel> labels2 = getLabelOrdering(code2);

            @Override
            public int acceptCompareTo(CfLabel item1, CfLabel item2, CompareToVisitor visitor) {
              return visitor.visitInt(labels1.getInt(item1), labels2.getInt(item2));
            }

            @Override
            public void acceptHashing(CfLabel item, HashingVisitor visitor) {
              throw new Unimplemented();
            }
          };
    }
    return lazyLabelAcceptor;
  }

  public StructuralAcceptor<CfInstruction> instructionAcceptor() {
    CfCompareHelper helper = this;
    return new StructuralAcceptor<CfInstruction>() {
      @Override
      public int acceptCompareTo(
          CfInstruction item1, CfInstruction item2, CompareToVisitor visitor) {
        return item1.acceptCompareTo(item2, visitor, helper);
      }

      @Override
      public void acceptHashing(CfInstruction item, HashingVisitor visitor) {
        throw new Unimplemented();
      }
    };
  }

  public StructuralAcceptor<CfTryCatch> tryCatchRangeAcceptor() {
    CfCompareHelper helper = this;
    return new StructuralAcceptor<CfTryCatch>() {
      @Override
      public int acceptCompareTo(CfTryCatch item1, CfTryCatch item2, CompareToVisitor visitor) {
        return item1.acceptCompareTo(item2, visitor, helper);
      }

      @Override
      public void acceptHashing(CfTryCatch item, HashingVisitor visitor) {
        throw new Unimplemented();
      }
    };
  }

  public StructuralAcceptor<LocalVariableInfo> localVariableAcceptor() {
    CfCompareHelper helper = this;
    return new StructuralAcceptor<LocalVariableInfo>() {
      @Override
      public int acceptCompareTo(
          LocalVariableInfo item1, LocalVariableInfo item2, CompareToVisitor visitor) {
        return item1.acceptCompareTo(item2, visitor, helper);
      }

      @Override
      public void acceptHashing(LocalVariableInfo item, HashingVisitor visitor) {
        throw new Unimplemented();
      }
    };
  }
}
