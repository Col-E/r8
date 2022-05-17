// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexPackedSwitch;
import com.android.tools.r8.dex.code.DexSparseSwitch;
import com.android.tools.r8.ir.conversion.SwitchPayloadResolver;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;

public class SwitchDexInstructionSubject extends DexInstructionSubject
    implements SwitchInstructionSubject {

  private final SwitchPayloadResolver switchPayloadResolver;

  public SwitchDexInstructionSubject(
      DexInstruction instruction,
      MethodSubject method,
      SwitchPayloadResolver switchPayloadResolver) {
    super(instruction, method);
    assert isSwitch();
    assert instruction.isIntSwitch();
    assert switchPayloadResolver != null;
    this.switchPayloadResolver = switchPayloadResolver;
  }

  @Override
  public List<Integer> getKeys() {
    if (instruction instanceof DexPackedSwitch) {
      assert switchPayloadResolver.getKeys(instruction.getOffset() + instruction.getPayloadOffset())
              .length
          == 1;
      int firstKey =
          switchPayloadResolver
              .getKeys(instruction.getOffset() + instruction.getPayloadOffset())[0];
      int numberOfKeys =
          switchPayloadResolver.absoluteTargets(
                  instruction.getOffset() + instruction.getPayloadOffset())
              .length;
      List<Integer> keys = new IntArrayList(numberOfKeys);
      for (int i = 0; i < numberOfKeys; i++) {
        keys.add(firstKey + i);
      }
      return keys;
    } else {
      assert instruction instanceof DexSparseSwitch;
      return new IntArrayList(
          switchPayloadResolver.getKeys(instruction.getOffset() + instruction.getPayloadOffset()));
    }
  }

  @Override
  public SwitchInstructionSubject asSwitch() {
    return this;
  }
}
