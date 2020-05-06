// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexCode.FAKE_THIS_PREFIX;
import static com.android.tools.r8.graph.DexCode.FAKE_THIS_SUFFIX;
import static org.objectweb.asm.Opcodes.V1_5;
import static org.objectweb.asm.Opcodes.V1_6;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Strings;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
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

    public LocalVariableInfo(int index, DebugLocalInfo local, CfLabel start, CfLabel end) {
      this(index, local, start);
      setEnd(end);
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

    @Override
    public String toString() {
      return "" + index + " => " + local;
    }
  }

  // The original holder is a reference to the holder type that the method was in from input and
  // for which the invokes still refer to. The holder is needed to determine if an invoke-special
  // maps to an invoke-direct or invoke-super.
  // TODO(b/135969130): Make IR building lense aware and avoid caching the holder type.
  private final DexType originalHolder;

  private final int maxStack;
  private int maxLocals;
  public List<CfInstruction> instructions;
  private final List<CfTryCatch> tryCatchRanges;
  private final List<LocalVariableInfo> localVariables;

  public CfCode(
      DexType originalHolder,
      int maxStack,
      int maxLocals,
      List<CfInstruction> instructions,
      List<CfTryCatch> tryCatchRanges,
      List<LocalVariableInfo> localVariables) {
    this.originalHolder = originalHolder;
    this.maxStack = maxStack;
    this.maxLocals = maxLocals;
    this.instructions = instructions;
    this.tryCatchRanges = tryCatchRanges;
    this.localVariables = localVariables;
  }

  public DexType getOriginalHolder() {
    return originalHolder;
  }

  public int getMaxStack() {
    return maxStack;
  }

  public int getMaxLocals() {
    return maxLocals;
  }

  public void setMaxLocals(int newMaxLocals) {
    maxLocals = newMaxLocals;
  }

  public List<CfTryCatch> getTryCatchRanges() {
    return tryCatchRanges;
  }

  public List<CfInstruction> getInstructions() {
    return Collections.unmodifiableList(instructions);
  }

  public List<LocalVariableInfo> getLocalVariables() {
    return Collections.unmodifiableList(localVariables);
  }

  @Override
  public int estimatedSizeForInlining() {
    return countNonStackOperations(Integer.MAX_VALUE);
  }

  @Override
  public boolean estimatedSizeForInliningAtMost(int threshold) {
    return countNonStackOperations(threshold) <= threshold;
  }

  private int countNonStackOperations(int threshold) {
    int result = 0;
    for (CfInstruction instruction : instructions) {
      if (instruction.emitsIR()) {
        result++;
        if (result > threshold) {
          break;
        }
      }
    }
    return result;
  }

  @Override
  public boolean isCfCode() {
    return true;
  }

  @Override
  public CfCode asCfCode() {
    return this;
  }

  private boolean shouldAddParameterNames(DexEncodedMethod method, AppView<?> appView) {
    // Don't add parameter information if the code already has full debug information.
    // Note: This fast path can cause a method to loose its parameter info, if the debug info turned
    // out to be invalid during IR building.
    if (appView.options().debug || appView.isCfByteCodePassThrough(method)) {
      return false;
    }
    assert localVariables.isEmpty();
    if (!method.hasParameterInfo()) {
      return false;
    }
    // If tree shaking, only keep annotations on kept methods.
    if (appView.appInfo().hasLiveness()
        && !appView.appInfo().withLiveness().isPinned(method.method)) {
      return false;
    }
    return true;
  }

  public void write(
      DexEncodedMethod method,
      MethodVisitor visitor,
      NamingLens namingLens,
      AppView<?> appView,
      int classFileVersion) {
    InitClassLens initClassLens = appView.initClassLens();
    InternalOptions options = appView.options();
    CfLabel parameterLabel = null;
    if (shouldAddParameterNames(method, appView)) {
      parameterLabel = new CfLabel();
      parameterLabel.write(visitor, initClassLens, namingLens);
    }
    for (CfInstruction instruction : instructions) {
      if (instruction instanceof CfFrame
          && (classFileVersion <= V1_5
              || (classFileVersion == V1_6 && !options.shouldKeepStackMapTable()))) {
        continue;
      }
      instruction.write(visitor, initClassLens, namingLens);
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
            guard == options.itemFactory.throwableType
                ? null
                : namingLens.lookupInternalName(guard));
      }
    }
    if (parameterLabel != null) {
      assert localVariables.isEmpty();
      for (Entry<Integer, DebugLocalInfo> entry : method.getParameterInfo().entrySet()) {
        writeLocalVariableEntry(
            visitor, namingLens, entry.getValue(), parameterLabel, parameterLabel, entry.getKey());
      }
    } else {
      for (LocalVariableInfo local : localVariables) {
        writeLocalVariableEntry(
            visitor, namingLens, local.local, local.start, local.end, local.index);
      }
    }
  }

  private void writeLocalVariableEntry(
      MethodVisitor visitor,
      NamingLens namingLens,
      DebugLocalInfo info,
      CfLabel start,
      CfLabel end,
      int index) {
    visitor.visitLocalVariable(
        info.name.toString(),
        namingLens.lookupDescriptor(info.type).toString(),
        info.signature == null ? null : info.signature.toString(),
        start.getLabel(),
        end.getLabel(),
        index);
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
  public boolean isEmptyVoidMethod() {
    for (CfInstruction insn : instructions) {
      if (!(insn instanceof CfReturnVoid)
          && !(insn instanceof CfLabel)
          && !(insn instanceof CfPosition)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public IRCode buildIR(ProgramMethod method, AppView<?> appView, Origin origin) {
    return internalBuildPossiblyWithLocals(method, method, appView, null, null, origin, null);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      ValueNumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    assert valueNumberGenerator != null;
    assert callerPosition != null;
    return internalBuildPossiblyWithLocals(
        context, method, appView, valueNumberGenerator, callerPosition, origin, methodProcessor);
  }

  // First build entry. Will either strip locals or build with locals.
  private IRCode internalBuildPossiblyWithLocals(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      ValueNumberGenerator generator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    if (!method.getDefinition().keepLocals(appView.options())) {
      return internalBuild(
          Collections.emptyList(),
          context,
          method,
          appView,
          generator,
          callerPosition,
          origin,
          methodProcessor);
    } else {
      return internalBuildWithLocals(
          context, method, appView, generator, callerPosition, origin, methodProcessor);
    }
  }

  // When building with locals, on invalid debug info, retry build without locals info.
  private IRCode internalBuildWithLocals(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      ValueNumberGenerator generator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    try {
      return internalBuild(
          Collections.unmodifiableList(localVariables),
          context,
          method,
          appView,
          generator,
          callerPosition,
          origin,
          methodProcessor);
    } catch (InvalidDebugInfoException e) {
      appView.options().warningInvalidDebugInfo(method, origin, e);
      return internalBuild(
          Collections.emptyList(),
          context,
          method,
          appView,
          generator,
          callerPosition,
          origin,
          methodProcessor);
    }
  }

  // Inner-most subroutine for building. Must only be called by the two internalBuildXYZ above.
  private IRCode internalBuild(
      List<LocalVariableInfo> localVariables,
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      ValueNumberGenerator generator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    CfSourceCode source =
        new CfSourceCode(
            this,
            localVariables,
            method,
            appView.graphLense().getOriginalMethodSignature(method.getReference()),
            callerPosition,
            origin,
            appView);
    IRBuilder builder =
        methodProcessor == null
            ? IRBuilder.create(method, appView, source, origin)
            : IRBuilder.createForInlining(
                method, appView, source, origin, methodProcessor, generator);
    return builder.build(context);
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    for (CfInstruction instruction : instructions) {
      instruction.registerUse(registry, method);
    }
    tryCatchRanges.forEach(tryCatch -> tryCatch.guards.forEach(registry::registerTypeReference));
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    for (CfInstruction instruction : instructions) {
      instruction.registerUseForDesugaring(registry, method);
    }
    tryCatchRanges.forEach(tryCatch -> tryCatch.guards.forEach(registry::registerTypeReference));
  }

  @Override
  public Int2ReferenceMap<DebugLocalInfo> collectParameterInfo(
      DexEncodedMethod encodedMethod, AppView<?> appView) {
    CfLabel firstLabel = null;
    for (CfInstruction instruction : instructions) {
      if (instruction instanceof CfLabel) {
        firstLabel = (CfLabel) instruction;
        break;
      }
    }
    if (firstLabel == null) {
      return DexEncodedMethod.NO_PARAMETER_INFO;
    }
    if (!appView.options().hasProguardConfiguration()
        || !appView.options().getProguardConfiguration().isKeepParameterNames()) {
      return DexEncodedMethod.NO_PARAMETER_INFO;
    }
    // The enqueuer might build IR to trace reflective behaviour. At that point liveness is not
    // known, so be conservative with collection parameter name information.
    if (appView.appInfo().hasLiveness()
        && !appView.appInfo().withLiveness().isPinned(encodedMethod.method)) {
      return DexEncodedMethod.NO_PARAMETER_INFO;
    }

    // Collect the local slots used for parameters.
    BitSet localSlotsForParameters = new BitSet(0);
    int nextLocalSlotsForParameters = 0;
    if (!encodedMethod.isStatic()) {
      localSlotsForParameters.set(nextLocalSlotsForParameters++);
    }
    for (DexType type : encodedMethod.method.proto.parameters.values) {
      localSlotsForParameters.set(nextLocalSlotsForParameters);
      nextLocalSlotsForParameters += type.isLongType() || type.isDoubleType() ? 2 : 1;
    }
    // Collect the first piece of local variable information for each argument local slot,
    // assuming that that does actually describe the parameter (name, type and possibly
    // signature).
    Int2ReferenceMap<DebugLocalInfo> parameterInfo =
        new Int2ReferenceArrayMap<>(localSlotsForParameters.cardinality());
    for (LocalVariableInfo node : localVariables) {
      if (node.start == firstLabel
          && localSlotsForParameters.get(node.index)
          && !parameterInfo.containsKey(node.index)) {
        parameterInfo.put(
            node.index, new DebugLocalInfo(node.local.name, node.local.type, node.local.signature));
      }
    }
    return parameterInfo;
  }

  @Override
  public void registerArgumentReferences(DexEncodedMethod method, ArgumentUse registry) {
    DexProto proto = method.method.proto;
    boolean isStatic = method.accessFlags.isStatic();
    int argumentCount = proto.parameters.values.length + (isStatic ? 0 : 1);
    Int2IntArrayMap indexToNumber = new Int2IntArrayMap(argumentCount);
    {
      int index = 0;
      int number = 0;
      if (!isStatic) {
        indexToNumber.put(index++, number++);
      }
      for (DexType value : proto.parameters.values) {
        indexToNumber.put(index++, number++);
        if (value.isLongType() || value.isDoubleType()) {
          index++;
        }
      }
    }
    assert indexToNumber.size() == argumentCount;
    for (CfInstruction instruction : instructions) {
      int index = -1;
      if (instruction instanceof CfLoad) {
        index = ((CfLoad) instruction).getLocalIndex();
      } else if (instruction instanceof CfIinc) {
        index = ((CfIinc) instruction).getLocalIndex();
      } else {
        continue;
      }
      if (index >= 0 && indexToNumber.containsKey(index)) {
        registry.register(indexToNumber.get(index));
      }
    }
  }

  @Override
  public String toString() {
    return new CfPrinter(this).toString();
  }

  @Override
  public String toString(DexEncodedMethod method, ClassNameMapper naming) {
    return new CfPrinter(this, method, naming).toString();
  }

  public ConstraintWithTarget computeInliningConstraint(
      ProgramMethod method,
      AppView<AppInfoWithLiveness> appView,
      GraphLense graphLense,
      DexProgramClass context) {
    InliningConstraints inliningConstraints = new InliningConstraints(appView, graphLense);
    if (appView.options().isInterfaceMethodDesugaringEnabled()) {
      // TODO(b/120130831): Conservatively need to say "no" at this point if there are invocations
      // to static interface methods. This should be fixed by making sure that the desugared
      // versions of default and static interface methods are present in the application during
      // IR processing.
      inliningConstraints.disallowStaticInterfaceMethodCalls();
    }

    // Model a synchronized method as having a monitor instruction.
    ConstraintWithTarget constraint =
        method.getDefinition().isSynchronized()
            ? inliningConstraints.forMonitor()
            : ConstraintWithTarget.ALWAYS;

    if (constraint == ConstraintWithTarget.NEVER) {
      return constraint;
    }
    for (CfInstruction insn : instructions) {
      constraint =
          ConstraintWithTarget.meet(
              constraint, insn.inliningConstraint(inliningConstraints, context), appView);
      if (constraint == ConstraintWithTarget.NEVER) {
        return constraint;
      }
    }
    if (!tryCatchRanges.isEmpty()) {
      // Model a try-catch as a move-exception instruction.
      constraint =
          ConstraintWithTarget.meet(constraint, inliningConstraints.forMoveException(), appView);
    }
    return constraint;
  }

  void addFakeThisParameter(DexItemFactory factory) {
    if (localVariables == null || localVariables.isEmpty()) {
      // We have no debugging info in the code.
      return;
    }
    int largestPrefix = 0;
    int existingThisIndex = -1;
    for (int i = 0; i < localVariables.size(); i++) {
      LocalVariableInfo localVariable = localVariables.get(i);
      largestPrefix =
          Math.max(largestPrefix, DexCode.getLargestPrefix(factory, localVariable.local.name));
      if (localVariable.local.name.toString().equals("this")) {
        existingThisIndex = i;
      }
    }
    if (existingThisIndex < 0) {
      return;
    }
    String fakeThisName = Strings.repeat(FAKE_THIS_PREFIX, largestPrefix + 1) + FAKE_THIS_SUFFIX;
    DebugLocalInfo debugLocalInfo =
        new DebugLocalInfo(factory.createString(fakeThisName), this.originalHolder, null);
    LocalVariableInfo thisLocalInfo = localVariables.get(existingThisIndex);
    this.localVariables.set(
        existingThisIndex,
        new LocalVariableInfo(
            thisLocalInfo.index, debugLocalInfo, thisLocalInfo.start, thisLocalInfo.end));
  }
}
