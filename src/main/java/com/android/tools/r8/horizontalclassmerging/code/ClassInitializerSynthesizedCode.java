// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import static com.android.tools.r8.utils.ConsumerUtils.apply;
import static java.lang.Integer.max;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.CfVersionUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassInitializerSynthesizedCode {
  private final List<DexEncodedMethod> staticClassInitializers;
  private int maxStack = 0;
  private int maxLocals = 0;

  private ClassInitializerSynthesizedCode(List<DexEncodedMethod> staticClassInitializers) {
    this.staticClassInitializers = staticClassInitializers;
  }

  public boolean isEmpty() {
    return staticClassInitializers.isEmpty();
  }

  private void addCfCode(List<CfInstruction> newInstructions, DexEncodedMethod method) {
    CfCode code = method.getCode().asCfCode();
    maxStack = max(maxStack, code.getMaxStack());
    maxLocals = max(maxLocals, code.getMaxLocals());

    CfLabel endLabel = new CfLabel();
    boolean requiresLabel = false;
    int index = 1;
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isReturn()) {
        if (code.getInstructions().size() != index) {
          newInstructions.add(new CfGoto(endLabel));
          requiresLabel = true;
        }
      } else {
        newInstructions.add(instruction);
      }

      index++;
    }
    if (requiresLabel) {
      newInstructions.add(endLabel);
    }
  }

  public Code getOrCreateCode(DexType originalHolder) {
    assert !staticClassInitializers.isEmpty();

    if (staticClassInitializers.size() == 1) {
      return staticClassInitializers.get(0).getCode();
    }

    // Building the instructions will adjust maxStack and maxLocals. Build it here before invoking
    // the CfCode constructor to ensure that the value passed in is the updated values.
    List<CfInstruction> instructions = buildInstructions();
    return new CfCode(
        originalHolder,
        maxStack,
        maxLocals,
        instructions,
        Collections.emptyList(),
        Collections.emptyList());
  }

  private List<CfInstruction> buildInstructions() {
    List<CfInstruction> newInstructions = new ArrayList<>();
    staticClassInitializers.forEach(apply(this::addCfCode, newInstructions));
    newInstructions.add(new CfReturnVoid());
    return newInstructions;
  }

  public DexEncodedMethod getFirst() {
    return staticClassInitializers.iterator().next();
  }

  public CfVersion getCfVersion() {
    if (staticClassInitializers.size() == 1) {
      DexEncodedMethod method = staticClassInitializers.get(0);
      return method.hasClassFileVersion() ? method.getClassFileVersion() : null;
    }
    assert staticClassInitializers.stream().allMatch(method -> method.getCode().isCfCode());
    return CfVersionUtils.max(staticClassInitializers);
  }

  public static class Builder {
    private final List<DexEncodedMethod> staticClassInitializers = new ArrayList<>();

    public void add(DexEncodedMethod method) {
      assert method.isClassInitializer();
      assert method.hasCode();
      staticClassInitializers.add(method);
    }

    public ClassInitializerSynthesizedCode build() {
      return new ClassInitializerSynthesizedCode(staticClassInitializers);
    }
  }
}
