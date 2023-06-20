// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexCode.FAKE_THIS_PREFIX;
import static com.android.tools.r8.graph.DexCode.FAKE_THIS_SUFFIX;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfFrameVerifier;
import com.android.tools.r8.cf.code.CfFrameVerifier.StackMapStatus;
import com.android.tools.r8.cf.code.CfFrameVerifierEventConsumer;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfMonitor;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.dex.code.DexBase5Format;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadata;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.ThrowingMethodConversionOptions;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CfCode extends Code implements CfWritableCode, StructuralItem<CfCode> {

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

    public int acceptCompareTo(
        LocalVariableInfo other, CompareToVisitor visitor, CfCompareHelper helper) {
      return visitor.visit(
          this,
          other,
          spec ->
              spec.withInt(LocalVariableInfo::getIndex)
                  .withCustomItem(LocalVariableInfo::getStart, helper.labelAcceptor())
                  .withCustomItem(LocalVariableInfo::getEnd, helper.labelAcceptor())
                  .withItem(LocalVariableInfo::getLocal));
    }

    @Override
    public String toString() {
      return "" + index + " => " + local;
    }
  }

  // The original holder is a reference to the holder type that the method was in from input and
  // for which the invokes still refer to. The holder is needed to determine if an invoke-special
  // maps to an invoke-direct or invoke-super.
  // TODO(b/135969130): Make IR building lens aware and avoid caching the holder type.
  private final DexType originalHolder;

  private int maxLocals;
  private int maxStack;
  private List<CfInstruction> instructions;
  private final List<CfTryCatch> tryCatchRanges;
  private final List<LocalVariableInfo> localVariables;
  private StackMapStatus stackMapStatus = CfFrameVerifier.StackMapStatus.NOT_VERIFIED;
  private final com.android.tools.r8.position.Position diagnosticPosition;
  private final BytecodeMetadata<CfInstruction> metadata;

  public CfCode(
      DexType originalHolder, int maxStack, int maxLocals, List<CfInstruction> instructions) {
    this(
        originalHolder,
        maxStack,
        maxLocals,
        instructions,
        Collections.emptyList(),
        Collections.emptyList());
  }

  public CfCode(
      DexType originalHolder,
      int maxStack,
      int maxLocals,
      List<CfInstruction> instructions,
      List<CfTryCatch> tryCatchRanges,
      List<LocalVariableInfo> localVariables) {
    this(
        originalHolder,
        maxStack,
        maxLocals,
        instructions,
        tryCatchRanges,
        localVariables,
        com.android.tools.r8.position.Position.UNKNOWN);
  }

  public CfCode(
      DexType originalHolder,
      int maxStack,
      int maxLocals,
      List<CfInstruction> instructions,
      List<CfTryCatch> tryCatchRanges,
      List<LocalVariableInfo> localVariables,
      com.android.tools.r8.position.Position diagnosticPosition) {
    this(
        originalHolder,
        maxStack,
        maxLocals,
        instructions,
        tryCatchRanges,
        localVariables,
        diagnosticPosition,
        BytecodeMetadata.empty());
  }

  public CfCode(
      DexType originalHolder,
      int maxStack,
      int maxLocals,
      List<CfInstruction> instructions,
      List<CfTryCatch> tryCatchRanges,
      List<LocalVariableInfo> localVariables,
      com.android.tools.r8.position.Position diagnosticPosition,
      BytecodeMetadata<CfInstruction> metadata) {
    this.originalHolder = originalHolder;
    this.maxStack = maxStack;
    this.maxLocals = maxLocals;
    this.instructions = instructions;
    this.tryCatchRanges = tryCatchRanges;
    this.localVariables = localVariables;
    this.diagnosticPosition = diagnosticPosition;
    this.metadata = metadata;
  }

  @Override
  public CfCode self() {
    return this;
  }

  @Override
  public CfWritableCodeKind getCfWritableCodeKind() {
    return CfWritableCodeKind.DEFAULT;
  }

  @Override
  public BytecodeMetadata<CfInstruction> getMetadata() {
    return metadata;
  }

  @Override
  public BytecodeInstructionMetadata getMetadata(CfOrDexInstruction instruction) {
    return getMetadata(instruction.asCfInstruction());
  }

  public BytecodeInstructionMetadata getMetadata(CfInstruction instruction) {
    return metadata.getMetadata(instruction);
  }

  @Override
  public StructuralMapping<CfCode> getStructuralMapping() {
    throw new Unreachable();
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

  public StackMapStatus getStackMapStatus() {
    assert stackMapStatus != CfFrameVerifier.StackMapStatus.NOT_VERIFIED;
    return stackMapStatus;
  }

  public void setStackMapStatus(StackMapStatus stackMapStatus) {
    this.stackMapStatus = stackMapStatus;
  }

  public com.android.tools.r8.position.Position getDiagnosticPosition() {
    return diagnosticPosition;
  }

  public void setMaxLocals(int newMaxLocals) {
    maxLocals = newMaxLocals;
  }

  public void setMaxStack(int newMaxStack) {
    maxStack = newMaxStack;
  }

  public List<CfTryCatch> getTryCatchRanges() {
    return tryCatchRanges;
  }

  public Set<CfLabel> getTryCatchRangeLabels() {
    Set<CfLabel> tryCatchRangeLabels = Sets.newIdentityHashSet();
    for (CfTryCatch tryCatchRange : getTryCatchRanges()) {
      tryCatchRangeLabels.add(tryCatchRange.start);
      tryCatchRangeLabels.add(tryCatchRange.end);
    }
    return tryCatchRangeLabels;
  }

  public CfInstruction getInstruction(int index) {
    return instructions.get(index);
  }

  public List<CfInstruction> getInstructions() {
    return Collections.unmodifiableList(instructions);
  }

  public void setInstructions(List<CfInstruction> instructions) {
    this.instructions = instructions;
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

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    return estimatedSizeForInlining() * DexBase5Format.SIZE;
  }

  public int bytecodeSizeUpperBound() {
    int result = 0;
    for (CfInstruction instruction : instructions) {
      int delta = instruction.bytecodeSizeUpperBound();
      assert delta > 0 || !instruction.emitsIR();
      result += delta;
    }
    return result;
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
  public boolean isCfWritableCode() {
    return true;
  }

  @Override
  public CfCode asCfCode() {
    return this;
  }

  @Override
  public CfWritableCode asCfWritableCode() {
    return this;
  }

  @Override
  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitInt(instructions.size());
    visitor.visitInt(tryCatchRanges.size());
    visitor.visitInt(localVariables.size());
    instructions.forEach(i -> i.acceptHashing(visitor));
  }

  @Override
  public int acceptCompareTo(CfCode other, CompareToVisitor visitor) {
    CfCompareHelper helper = new CfCompareHelper(this, other);
    return visitor.visit(
        this,
        other,
        spec ->
            spec.withCustomItemCollection(c -> c.instructions, helper.instructionAcceptor())
                .withCustomItemCollection(c -> c.tryCatchRanges, helper.tryCatchRangeAcceptor())
                .withCustomItemCollection(c -> c.localVariables, helper.localVariableAcceptor()));
  }

  private boolean shouldAddParameterNames(DexEncodedMethod method, AppView<?> appView) {
    // In cf to cf desugar we do pass through of code and don't move around methods.
    // TODO(b/169115389): Remove when we have a way to determine if we need parameter names per
    // method.
    if (appView.options().isCfDesugaring()) {
      return false;
    }

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
    if (appView.appInfo().hasLiveness() && !appView.appInfo().withLiveness().isPinned(method)) {
      return false;
    }
    return true;
  }

  public Position getPreamblePosition() {
    Position preamble = null;
    for (CfInstruction instruction : instructions) {
      if (instruction.isLabel()) {
        continue;
      }
      if (instruction.isPosition()) {
        Position candidate = instruction.asPosition().getPosition();
        if (candidate.getLine() == 0) {
          preamble = candidate;
        }
      }
      break;
    }
    return preamble;
  }

  @Override
  public void writeCf(
      ProgramMethod method,
      CfVersion classFileVersion,
      AppView<?> appView,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    GraphLens graphLens = appView.graphLens();
    assert getOrComputeStackMapStatus(method, appView).isValidOrNotPresent()
        : "Could not validate stack map frames";
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    InitClassLens initClassLens = appView.initClassLens();
    InternalOptions options = appView.options();
    CfLabel parameterLabel = null;
    if (shouldAddParameterNames(method.getDefinition(), appView)) {
      parameterLabel = new CfLabel();
      parameterLabel.write(
          appView, method, dexItemFactory, graphLens, initClassLens, namingLens, rewriter, visitor);
    }
    boolean discardFrames =
        classFileVersion.isLessThan(CfVersion.V1_6)
            || (appView.enableWholeProgramOptimizations()
                && classFileVersion.isEqualTo(CfVersion.V1_6)
                && !options.shouldKeepStackMapTable());
    Position preamblePosition = getPreamblePosition();
    boolean discardPreamble = preamblePosition != null && preamblePosition.isSyntheticPosition();
    for (CfInstruction instruction : instructions) {
      if (discardFrames && instruction.isFrame()) {
        continue;
      }
      if (discardPreamble
          && instruction.isPosition()
          && instruction.asPosition().getPosition().equals(preamblePosition)) {
        discardPreamble = false;
        continue;
      }
      instruction.write(
          appView, method, dexItemFactory, graphLens, initClassLens, namingLens, rewriter, visitor);
    }
    visitor.visitEnd();
    visitor.visitMaxs(maxStack, maxLocals);
    for (CfTryCatch tryCatch : tryCatchRanges) {
      Label start = tryCatch.start.getLabel();
      Label end = tryCatch.end.getLabel();
      for (int i = 0; i < tryCatch.guards.size(); i++) {
        DexType guard = tryCatch.guards.get(i);
        DexType rewrittenGuard = graphLens.lookupType(guard);
        Label target = tryCatch.targets.get(i).getLabel();
        visitor.visitTryCatchBlock(
            start,
            end,
            target,
            rewrittenGuard == options.itemFactory.throwableType
                ? null
                : namingLens.lookupInternalName(rewrittenGuard));
      }
    }
    if (parameterLabel != null) {
      assert localVariables.isEmpty();
      Int2ReferenceMap<DebugLocalInfo> parameterInfo = method.getDefinition().getParameterInfo();
      for (Int2ReferenceMap.Entry<DebugLocalInfo> entry : parameterInfo.int2ReferenceEntrySet()) {
        writeLocalVariableEntry(
            visitor,
            graphLens,
            namingLens,
            entry.getValue(),
            parameterLabel,
            parameterLabel,
            entry.getIntKey());
      }
    } else {
      for (LocalVariableInfo local : localVariables) {
        writeLocalVariableEntry(
            visitor, graphLens, namingLens, local.local, local.start, local.end, local.index);
      }
    }
  }

  private void writeLocalVariableEntry(
      MethodVisitor visitor,
      GraphLens graphLens,
      NamingLens namingLens,
      DebugLocalInfo info,
      CfLabel start,
      CfLabel end,
      int index) {
    DexType rewrittenType = graphLens.lookupType(info.type);
    visitor.visitLocalVariable(
        info.name.toString(),
        namingLens.lookupDescriptor(rewrittenType).toString(),
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
  public boolean hasMonitorInstructions() {
    for (CfInstruction instruction : getInstructions()) {
      if (instruction instanceof CfMonitor) {
        return true;
      }
    }
    return false;
  }

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      Origin origin,
      MutableMethodConversionOptions conversionOptions) {
    verifyFramesOrRemove(method, appView, getCodeLens(appView));
    return internalBuildPossiblyWithLocals(
        method, method, appView, appView.codeLens(), null, null, origin, null, conversionOptions);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges) {
    assert valueNumberGenerator != null;
    assert callerPosition != null;
    assert protoChanges != null;
    verifyFramesOrRemove(method, appView, codeLens);
    return internalBuildPossiblyWithLocals(
        context,
        method,
        appView,
        codeLens,
        valueNumberGenerator,
        callerPosition,
        origin,
        protoChanges,
        new ThrowingMethodConversionOptions(appView.options()));
  }

  private void verifyFramesOrRemove(ProgramMethod method, AppView<?> appView, GraphLens codeLens) {
    stackMapStatus = getOrComputeStackMapStatus(method, appView, codeLens);
    if (!stackMapStatus.isValidOrNotPresent()) {
      ArrayList<CfInstruction> copy = new ArrayList<>(instructions);
      copy.removeIf(CfInstruction::isFrame);
      setInstructions(copy);
    }
  }

  // First build entry. Will either strip locals or build with locals.
  private IRCode internalBuildPossiblyWithLocals(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges,
      MutableMethodConversionOptions conversionOptions) {
    if (!method.keepLocals(appView)) {
      return internalBuild(
          Collections.emptyList(),
          context,
          method,
          appView,
          codeLens,
          valueNumberGenerator,
          callerPosition,
          origin,
          protoChanges,
          conversionOptions);
    } else {
      return internalBuildWithLocals(
          context,
          method,
          appView,
          codeLens,
          valueNumberGenerator,
          callerPosition,
          origin,
          protoChanges,
          conversionOptions);
    }
  }

  // When building with locals, on invalid debug info, retry build without locals info.
  private IRCode internalBuildWithLocals(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges,
      MutableMethodConversionOptions conversionOptions) {
    try {
      return internalBuild(
          Collections.unmodifiableList(localVariables),
          context,
          method,
          appView,
          codeLens,
          valueNumberGenerator,
          callerPosition,
          origin,
          protoChanges,
          conversionOptions);
    } catch (InvalidDebugInfoException e) {
      appView.options().warningInvalidDebugInfo(method, origin, e);
      return internalBuild(
          Collections.emptyList(),
          context,
          method,
          appView,
          codeLens,
          valueNumberGenerator,
          callerPosition,
          origin,
          protoChanges,
          conversionOptions);
    }
  }

  // Inner-most subroutine for building. Must only be called by the two internalBuildXYZ above.
  private IRCode internalBuild(
      List<LocalVariableInfo> localVariables,
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges,
      MutableMethodConversionOptions conversionOptions) {
    CfSourceCode source =
        new CfSourceCode(
            this,
            localVariables,
            method,
            appView.graphLens().getOriginalMethodSignature(method.getReference()),
            callerPosition,
            origin,
            appView);
    IRBuilder builder;
    if (valueNumberGenerator == null) {
      assert protoChanges == null;
      builder = IRBuilder.create(method, appView, source, origin);
    } else {
      builder =
          IRBuilder.createForInlining(
              method, appView, codeLens, source, origin, valueNumberGenerator, protoChanges);
    }
    return builder.build(context, conversionOptions);
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    assert registry.getTraversalContinuation().shouldContinue();
    ListIterator<CfInstruction> iterator = instructions.listIterator();
    while (iterator.hasNext()) {
      CfInstruction instruction = iterator.next();
      instruction.registerUse(registry, method, iterator);
      if (registry.getTraversalContinuation().shouldBreak()) {
        return;
      }
    }
    for (CfTryCatch tryCatch : tryCatchRanges) {
      tryCatch.internalRegisterUse(registry, method);
      if (registry.getTraversalContinuation().shouldBreak()) {
        return;
      }
    }
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    ListIterator<CfInstruction> iterator = instructions.listIterator();
    while (iterator.hasNext()) {
      CfInstruction instruction = iterator.next();
      instruction.registerUseForDesugaring(registry, method, iterator);
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
        && !appView.appInfo().withLiveness().isPinned(encodedMethod)) {
      return DexEncodedMethod.NO_PARAMETER_INFO;
    }

    // Collect the local slots used for parameters.
    BitSet localSlotsForParameters = new BitSet(0);
    int nextLocalSlotsForParameters = 0;
    if (!encodedMethod.isStatic()) {
      localSlotsForParameters.set(nextLocalSlotsForParameters++);
    }
    for (DexType type : encodedMethod.getReference().proto.parameters.values) {
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
    DexProto proto = method.getReference().proto;
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
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return new CfPrinter(this, method, retracer).toString();
  }

  public ConstraintWithTarget computeInliningConstraint(
      ProgramMethod method,
      AppView<AppInfoWithLiveness> appView,
      GraphLens graphLens,
      ProgramMethod context) {
    InliningConstraints inliningConstraints = new InliningConstraints(appView, graphLens);
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
              constraint, insn.inliningConstraint(inliningConstraints, this, context), appView);
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
    String fakeThisName = FAKE_THIS_PREFIX.repeat(largestPrefix + 1) + FAKE_THIS_SUFFIX;
    DebugLocalInfo debugLocalInfo =
        new DebugLocalInfo(factory.createString(fakeThisName), this.originalHolder, null);
    LocalVariableInfo thisLocalInfo = localVariables.get(existingThisIndex);
    this.localVariables.set(
        existingThisIndex,
        new LocalVariableInfo(
            thisLocalInfo.index, debugLocalInfo, thisLocalInfo.start, thisLocalInfo.end));
  }

  @Override
  public Code getCodeAsInlining(
      DexMethod caller, DexMethod callee, DexItemFactory factory, boolean isCalleeD8R8Synthesized) {
    Position callerPosition = SyntheticPosition.builder().setLine(0).setMethod(caller).build();
    List<CfInstruction> newInstructions = new ArrayList<>(instructions.size() + 2);
    CfLabel firstLabel;
    if (instructions.get(0).isLabel()) {
      firstLabel = instructions.get(0).asLabel();
    } else {
      firstLabel = new CfLabel();
      newInstructions.add(firstLabel);
    }
    boolean seenPosition = false;
    for (CfInstruction instruction : instructions) {
      if (instruction.isPosition()) {
        seenPosition = true;
        CfPosition oldPosition = instruction.asPosition();
        newInstructions.add(
            new CfPosition(
                oldPosition.getLabel(),
                newInlineePosition(
                    callerPosition, oldPosition.getPosition(), isCalleeD8R8Synthesized)));
      } else {
        if (!instruction.isLabel() && !seenPosition) {
          Position preamblePosition =
              isCalleeD8R8Synthesized
                  ? callerPosition
                  : SyntheticPosition.builder()
                      .setMethod(callee)
                      .setCallerPosition(callerPosition)
                      .setLine(0)
                      .build();
          newInstructions.add(new CfPosition(firstLabel, preamblePosition));
          seenPosition = true;
        }
        newInstructions.add(instruction);
      }
    }
    return new CfCode(
        originalHolder, maxStack, maxLocals, newInstructions, tryCatchRanges, localVariables);
  }

  public StackMapStatus getOrComputeStackMapStatus(ProgramMethod method, AppView<?> appView) {
    return getOrComputeStackMapStatus(method, appView, getCodeLens(appView));
  }

  public StackMapStatus getOrComputeStackMapStatus(
      ProgramMethod method, AppView<?> appView, GraphLens codeLens) {
    if (stackMapStatus.isNotVerified()) {
      setStackMapStatus(computeStackMapStatus(method, appView, codeLens));
    }
    return stackMapStatus;
  }

  private StackMapStatus computeStackMapStatus(
      ProgramMethod method, AppView<?> appView, GraphLens codeLens) {
    CfFrameVerifierEventConsumer eventConsumer =
        new CfFrameVerifierEventConsumer() {

          @Override
          public void acceptError(CfCodeDiagnostics diagnostics) {
            // Stack maps was required from version V1_6 (50), but the JVM gave a grace-period and
            // only started enforcing stack maps from 51 in JVM 8. As a consequence, we have
            // different android libraries that has V1_7 code but has no stack maps. To not fail on
            // compilations we only report a warning.
            appView.options().reporter.warning(diagnostics);
          }
        };
    CfFrameVerifier helper =
        CfFrameVerifier.builder(appView, this, method)
            .setCodeLens(codeLens)
            .setEventConsumer(eventConsumer)
            .build();
    return helper.run();
  }

  @Override
  public void forEachPosition(DexMethod method, Consumer<Position> positionConsumer) {
    for (CfInstruction instruction : getInstructions()) {
      if (instruction.isPosition()) {
        positionConsumer.accept(instruction.asPosition().getPosition());
      }
    }
  }
}
