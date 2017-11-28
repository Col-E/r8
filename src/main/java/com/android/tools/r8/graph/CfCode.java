// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CfCode extends Code {

  public static class LocalVariableInfo {
    private final int index;
    private final DebugLocalInfo local;
    private final CfLabel start;
    private CfLabel end;

    public LocalVariableInfo(int index, DebugLocalInfo local, CfLabel start) {
      this.index = index;
      this.local = local;
      this.start = start;
    }

    public void setEnd(CfLabel end) {
      assert this.end == null;
      assert end != null;
      this.end = end;
    }

    public int getIndex() {
      return index;
    }

    public DebugLocalInfo getLocal() {
      return local;
    }

    public CfLabel getStart() {
      return start;
    }

    public CfLabel getEnd() {
      return end;
    }
  }

  private final DexMethod method;
  private final int maxStack;
  private final int maxLocals;
  private final List<CfInstruction> instructions;
  private final List<CfTryCatch> tryCatchRanges;
  private final List<LocalVariableInfo> localVariables;

  public CfCode(
      DexMethod method,
      int maxStack,
      int maxLocals,
      List<CfInstruction> instructions,
      List<CfTryCatch> tryCatchRanges,
      List<LocalVariableInfo> localVariables) {
    this.method = method;
    this.maxStack = maxStack;
    this.maxLocals = maxLocals;
    this.instructions = instructions;
    this.tryCatchRanges = tryCatchRanges;
    this.localVariables = localVariables;
  }

  public DexMethod getMethod() {
    return method;
  }

  public int getMaxStack() {
    return maxStack;
  }

  public int getMaxLocals() {
    return maxLocals;
  }

  public List<CfInstruction> getInstructions() {
    return Collections.unmodifiableList(instructions);
  }

  public List<LocalVariableInfo> getLocalVariables() {
    return Collections.unmodifiableList(localVariables);
  }

  @Override
  public boolean isCfCode() {
    return true;
  }

  @Override
  public CfCode asCfCode() {
    return this;
  }

  public void write(MethodVisitor visitor) {
    for (CfInstruction instruction : instructions) {
      instruction.write(visitor);
    }
    visitor.visitEnd();
    visitor.visitMaxs(maxStack, maxLocals);
    for (CfTryCatch tryCatch : tryCatchRanges) {
      Label start = tryCatch.start.getLabel();
      Label end = tryCatch.end.getLabel();
      for (int i = 0; i < tryCatch.guards.size(); i++) {
        DexType guard = tryCatch.guards.get(i);
        Label target = tryCatch.targets.get(i).getLabel();
        visitor.visitTryCatchBlock(
            start,
            end,
            target,
            guard == DexItemFactory.catchAllType ? null : guard.getInternalName());
      }
    }
    for (LocalVariableInfo localVariable : localVariables) {
      DebugLocalInfo info = localVariable.local;
      visitor.visitLocalVariable(
          info.name.toString(),
          info.type.toDescriptorString(),
          info.signature == null ? null : info.signature.toString(),
          localVariable.start.getLabel(),
          localVariable.end.getLabel(),
          localVariable.index);
    }
  }

  @Override
  protected int computeHashCode() {
    throw new Unimplemented();
  }

  @Override
  protected boolean computeEquals(Object other) {
    throw new Unimplemented();
  }

  @Override
  public IRCode buildIR(DexEncodedMethod encodedMethod, InternalOptions options)
      throws ApiLevelException {
    throw new Unimplemented("Converting Java class- file bytecode to IR not yet supported");
  }

  @Override
  public void registerReachableDefinitions(UseRegistry registry) {
    throw new Unimplemented("Inspecting Java class-file bytecode not yet supported");
  }

  @Override
  public String toString() {
    return new CfPrinter(this).toString();
  }

  @Override
  public String toString(DexEncodedMethod method, ClassNameMapper naming) {
    return null;
  }
}
