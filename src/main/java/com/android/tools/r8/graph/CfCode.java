// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexCode.FAKE_THIS_PREFIX;
import static com.android.tools.r8.graph.DexCode.FAKE_THIS_SUFFIX;
import static com.android.tools.r8.ir.conversion.CfSourceCode.canThrowHelper;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfFrameVerificationHelper;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.code.Base5Format;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfo;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
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
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiPredicate;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

public class CfCode extends Code implements Comparable<CfCode> {

  public enum StackMapStatus {
    NOT_VERIFIED,
    INVALID_OR_NOT_PRESENT,
    VALID
  }

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

    public int compareTo(LocalVariableInfo other, CfCompareHelper helper) {
      return Comparator.comparingInt(LocalVariableInfo::getIndex)
          .thenComparing(LocalVariableInfo::getStart, helper::compareLabels)
          .thenComparing(LocalVariableInfo::getEnd, helper::compareLabels)
          .thenComparing(LocalVariableInfo::getLocal)
          .compare(this, other);
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

  private final int maxStack;
  private int maxLocals;
  private List<CfInstruction> instructions;
  private final List<CfTryCatch> tryCatchRanges;
  private final List<LocalVariableInfo> localVariables;
  private StackMapStatus stackMapStatus = StackMapStatus.NOT_VERIFIED;

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

  public StackMapStatus getStackMapStatus() {
    assert stackMapStatus != StackMapStatus.NOT_VERIFIED;
    return stackMapStatus;
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
    return estimatedSizeForInlining() * Base5Format.SIZE;
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

  @Override
  public int compareTo(CfCode o) {
    // Fast path by checking sizes.
    int sizeDiff =
        Comparator.comparingInt((CfCode c) -> c.instructions.size())
            .thenComparingInt(c -> c.tryCatchRanges.size())
            .thenComparingInt(c -> localVariables.size())
            .compare(this, o);
    if (sizeDiff != 0) {
      return sizeDiff;
    }
    // In the slow case, compute label maps and compare collections in full.
    Reference2IntMap<CfLabel> labels1 = getLabelOrdering(instructions);
    Reference2IntMap<CfLabel> labels2 = getLabelOrdering(o.instructions);
    int labelDiff = labels1.size() - labels2.size();
    if (labelDiff != 0) {
      return labelDiff;
    }
    CfCompareHelper helper = new CfCompareHelper(labels1, labels2);
    return Comparator.comparing((CfCode c) -> c.instructions, helper.instructionComparator())
        .thenComparing(c -> c.tryCatchRanges, helper.tryCatchRangesComparator())
        .thenComparing(c -> c.localVariables, helper.localVariablesComparator())
        .compare(this, o);
  }

  private static Reference2IntMap<CfLabel> getLabelOrdering(List<CfInstruction> instructions) {
    Reference2IntMap<CfLabel> ordering = new Reference2IntOpenHashMap<>();
    for (CfInstruction instruction : instructions) {
      if (instruction.isLabel()) {
        ordering.put(instruction.asLabel(), ordering.size());
      }
    }
    return ordering;
  }

  private boolean shouldAddParameterNames(DexEncodedMethod method, AppView<?> appView) {
    // In cf to cf desugar we do pass through of code and don't move around methods.
    // TODO(b/169115389): Remove when we have a way to determine if we need parameter names per
    // method.
    if (appView.options().cfToCfDesugar) {
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
    if (appView.appInfo().hasLiveness()
        && !appView.appInfo().withLiveness().isPinned(method.method)) {
      return false;
    }
    return true;
  }

  public void write(
      ProgramMethod method,
      CfVersion classFileVersion,
      AppView<?> appView,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    GraphLens graphLens = appView.graphLens();
    // TODO(b/170073151): Handle unapplied code rewritings.
    assert graphLens.hasCodeRewritings()
        || verifyFrames(method.getDefinition(), appView, null, false);
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    InitClassLens initClassLens = appView.initClassLens();
    InternalOptions options = appView.options();
    CfLabel parameterLabel = null;
    if (shouldAddParameterNames(method.getDefinition(), appView)) {
      parameterLabel = new CfLabel();
      parameterLabel.write(
          appView, method, dexItemFactory, graphLens, initClassLens, namingLens, rewriter, visitor);
    }
    for (CfInstruction instruction : instructions) {
      if (instruction instanceof CfFrame
          && (classFileVersion.isLessThan(CfVersion.V1_6)
              || (classFileVersion.isEqual(CfVersion.V1_6)
                  && !options.shouldKeepStackMapTable()))) {
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
      Map<Integer, DebugLocalInfo> parameterInfo = method.getDefinition().getParameterInfo();
      for (Entry<Integer, DebugLocalInfo> entry : parameterInfo.entrySet()) {
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
    verifyFramesOrRemove(method.getDefinition(), appView, origin, true);
    return internalBuildPossiblyWithLocals(method, method, appView, null, null, origin, null);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    assert valueNumberGenerator != null;
    assert callerPosition != null;
    verifyFramesOrRemove(
        method.getDefinition(), appView, origin, methodProcessor.shouldApplyCodeRewritings(method));
    return internalBuildPossiblyWithLocals(
        context, method, appView, valueNumberGenerator, callerPosition, origin, methodProcessor);
  }

  private void verifyFramesOrRemove(
      DexEncodedMethod method,
      AppView<?> appView,
      Origin origin,
      boolean shouldApplyCodeRewritings) {
    if (!verifyFrames(method, appView, origin, shouldApplyCodeRewritings)) {
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
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    if (!method.getDefinition().keepLocals(appView.options())) {
      return internalBuild(
          Collections.emptyList(),
          context,
          method,
          appView,
          valueNumberGenerator,
          callerPosition,
          origin,
          methodProcessor);
    } else {
      return internalBuildWithLocals(
          context, method, appView, valueNumberGenerator, callerPosition, origin, methodProcessor);
    }
  }

  // When building with locals, on invalid debug info, retry build without locals info.
  private IRCode internalBuildWithLocals(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    try {
      return internalBuild(
          Collections.unmodifiableList(localVariables),
          context,
          method,
          appView,
          valueNumberGenerator,
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
          valueNumberGenerator,
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
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      MethodProcessor methodProcessor) {
    CfSourceCode source =
        new CfSourceCode(
            this,
            localVariables,
            method,
            appView.graphLens().getOriginalMethodSignature(method.getReference()),
            callerPosition,
            origin,
            appView);
    IRBuilder builder =
        methodProcessor == null
            ? IRBuilder.create(method, appView, source, origin)
            : IRBuilder.createForInlining(
                method, appView, source, origin, methodProcessor, valueNumberGenerator);
    return builder.build(context);
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    for (CfInstruction instruction : instructions) {
      instruction.registerUse(registry, method);
    }
    tryCatchRanges.forEach(tryCatch -> tryCatch.internalRegisterUse(registry, method));
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

  public boolean verifyFrames(
      DexEncodedMethod method, AppView<?> appView, Origin origin, boolean applyProtoTypeChanges) {
    if (!appView.options().testing.readInputStackMaps
        || appView.options().testing.disableStackMapVerification) {
      stackMapStatus = StackMapStatus.INVALID_OR_NOT_PRESENT;
      return true;
    }
    if (method.hasClassFileVersion() && method.getClassFileVersion().isLessThan(CfVersion.V1_7)) {
      stackMapStatus = StackMapStatus.INVALID_OR_NOT_PRESENT;
      return true;
    }
    if (!method.isInstanceInitializer()
        && appView
            .graphLens()
            .getOriginalMethodSignature(method.method)
            .isInstanceInitializer(appView.dexItemFactory())) {
      // We cannot verify instance initializers if they are moved.
      stackMapStatus = StackMapStatus.INVALID_OR_NOT_PRESENT;
      return true;
    }
    // Build a map from labels to frames.
    Map<CfLabel, CfFrame> stateMap = new IdentityHashMap<>();
    List<CfLabel> labels = new ArrayList<>();
    boolean requireStackMapFrame = !tryCatchRanges.isEmpty();
    for (CfInstruction instruction : instructions) {
      if (instruction.isFrame()) {
        CfFrame frame = instruction.asFrame();
        if (!labels.isEmpty()) {
          for (CfLabel label : labels) {
            if (stateMap.containsKey(label)) {
              return reportStackMapError(
                  CfCodeStackMapValidatingException.multipleFramesForLabel(
                      origin,
                      appView.graphLens().getOriginalMethodSignature(method.method),
                      appView),
                  appView);
            }
            stateMap.put(label, frame);
          }
        } else if (instruction != instructions.get(0)) {
          // From b/168212806, it is possible that the first instruction is a frame.
          return reportStackMapError(
              CfCodeStackMapValidatingException.unexpectedStackMapFrame(
                  origin, appView.graphLens().getOriginalMethodSignature(method.method), appView),
              appView);
        }
      }
      // We are trying to map a frame to a label, but we can have positions in between, so skip
      // those.
      if (instruction.isPosition()) {
        continue;
      } else if (instruction.isLabel()) {
        labels.add(instruction.asLabel());
      } else {
        labels.clear();
      }
      if (!requireStackMapFrame) {
        requireStackMapFrame = instruction.isJump() && !finalAndExitInstruction(instruction);
      }
    }
    // If there are no frames but we have seen a jump instruction, we cannot verify the stack map.
    if (requireStackMapFrame && stateMap.isEmpty()) {
      return reportStackMapError(
          CfCodeStackMapValidatingException.noFramesForMethodWithJumps(
              origin, appView.graphLens().getOriginalMethodSignature(method.method), appView),
          appView);
    }
    DexType context = appView.graphLens().lookupType(method.holder());
    DexType returnType = appView.graphLens().lookupType(method.method.getReturnType());
    RewrittenPrototypeDescription rewrittenDescription = RewrittenPrototypeDescription.none();
    if (applyProtoTypeChanges) {
      rewrittenDescription =
          appView.graphLens().lookupPrototypeChangesForMethodDefinition(method.method);
      if (!rewrittenDescription.isEmpty()
          && rewrittenDescription.getRewrittenReturnInfo() != null) {
        returnType = rewrittenDescription.getRewrittenReturnInfo().getOldType();
      }
    }
    CfFrameVerificationHelper builder =
        new CfFrameVerificationHelper(
            context,
            stateMap,
            tryCatchRanges,
            isAssignablePredicate(appView),
            appView.dexItemFactory());
    if (stateMap.containsKey(null)) {
      assert !shouldComputeInitialFrame();
      builder.verifyFrameAndSet(stateMap.get(null));
    } else if (shouldComputeInitialFrame()) {
      builder.verifyFrameAndSet(
          new CfFrame(
              computeInitialLocals(context, method, rewrittenDescription), new ArrayDeque<>()));
    }
    for (int i = 0; i < instructions.size(); i++) {
      CfInstruction instruction = instructions.get(i);
      try {
        // Check the exceptional edge prior to evaluating the instruction. The local state is stable
        // at this point as store operations are not throwing and the current stack does not
        // affect the exceptional transfer (the exception edge is always a singleton stack).
        if (canThrowHelper(instruction, appView.options().isGeneratingClassFiles())) {
          assert !instruction.isStore();
          builder.verifyExceptionEdges();
        }
        instruction.evaluate(
            builder, context, returnType, appView.dexItemFactory(), appView.initClassLens());
      } catch (CfCodeStackMapValidatingException ex) {
        return reportStackMapError(
            CfCodeStackMapValidatingException.toDiagnostics(
                origin,
                appView.graphLens().getOriginalMethodSignature(method.method),
                i,
                instruction,
                ex.getMessage(),
                appView),
            appView);
      }
    }
    stackMapStatus = StackMapStatus.VALID;
    return true;
  }

  private boolean reportStackMapError(CfCodeDiagnostics diagnostics, AppView<?> appView) {
    // Stack maps was required from version V1_6 (50), but the JVM gave a grace-period and only
    // started enforcing stack maps from 51 in JVM 8. As a consequence, we have different android
    // libraries that has V1_7 code but has no stack maps. To not fail on compilations we only
    // report a warning.
    stackMapStatus = StackMapStatus.INVALID_OR_NOT_PRESENT;
    appView.options().reporter.warning(diagnostics);
    return false;
  }

  private boolean finalAndExitInstruction(CfInstruction instruction) {
    boolean isReturnOrThrow = instruction.isThrow() || instruction.isReturn();
    if (!isReturnOrThrow) {
      return false;
    }
    for (int i = instructions.size() - 1; i >= 0; i--) {
      CfInstruction instr = instructions.get(i);
      if (instr == instruction) {
        return true;
      }
      if (instr.isPosition() || instr.isLabel()) {
        continue;
      }
      return false;
    }
    throw new Unreachable("Instruction " + instruction + " should be in instructions");
  }

  private boolean shouldComputeInitialFrame() {
    for (CfInstruction instruction : instructions) {
      if (instruction.isFrame()) {
        return false;
      } else if (!instruction.isLabel() && !instruction.isPosition()) {
        return true;
      }
    }
    // We should never see a method with only labels and positions.
    assert false;
    return true;
  }

  private Int2ReferenceSortedMap<FrameType> computeInitialLocals(
      DexType context, DexEncodedMethod method, RewrittenPrototypeDescription protoTypeChanges) {
    int accessFlags =
        protoTypeChanges.isEmpty()
            ? method.accessFlags.modifiedFlags
            : method.accessFlags.originalFlags;
    Int2ReferenceSortedMap<FrameType> initialLocals = new Int2ReferenceAVLTreeMap<>();
    int index = 0;
    if (method.isInstanceInitializer()) {
      initialLocals.put(index++, FrameType.uninitializedThis());
    } else if (!MethodAccessFlags.isSet(ACC_STATIC, accessFlags)) {
      initialLocals.put(index++, FrameType.initialized(context));
    }
    ArgumentInfoCollection argumentsInfo = protoTypeChanges.getArgumentInfoCollection();
    DexType[] parameters = method.method.proto.parameters.values;
    int originalNumberOfArguments =
        parameters.length
            + argumentsInfo.numberOfRemovedArguments()
            + initialLocals.size()
            - protoTypeChanges.numberOfExtraParameters();
    int argumentIndex = index;
    int usedArgumentIndex = 0;
    while (argumentIndex < originalNumberOfArguments) {
      ArgumentInfo argumentInfo = argumentsInfo.getArgumentInfo(argumentIndex++);
      DexType localType;
      if (argumentInfo.isRemovedArgumentInfo()) {
        localType = argumentInfo.asRemovedArgumentInfo().getType();
      } else {
        if (argumentInfo.isRewrittenTypeInfo()) {
          assert parameters[usedArgumentIndex] == argumentInfo.asRewrittenTypeInfo().getNewType();
          localType = argumentInfo.asRewrittenTypeInfo().getOldType();
        } else {
          localType = parameters[usedArgumentIndex];
        }
        usedArgumentIndex++;
      }
      FrameType frameType = FrameType.initialized(localType);
      initialLocals.put(index++, frameType);
      if (localType.isWideType()) {
        initialLocals.put(index++, frameType);
      }
    }
    return initialLocals;
  }

  private BiPredicate<DexType, DexType> isAssignablePredicate(AppView<?> appView) {
    return (source, target) -> isAssignable(source, target, appView);
  }

  // Rules found at https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.2
  private boolean isAssignable(DexType source, DexType target, AppView<?> appView) {
    DexItemFactory factory = appView.dexItemFactory();
    source = byteCharShortOrBooleanToInt(source, factory);
    target = byteCharShortOrBooleanToInt(target, factory);
    if (source == target) {
      return true;
    }
    if (source.isPrimitiveType() || target.isPrimitiveType()) {
      return false;
    }
    // Both are now references - everything is assignable to object.
    if (target == factory.objectType) {
      return true;
    }
    // isAssignable(null, class(_, _)).
    // isAssignable(null, arrayOf(_)).
    if (source == DexItemFactory.nullValueType) {
      return true;
    }
    if (target.isArrayType() != target.isArrayType()) {
      return false;
    }
    if (target.isArrayType()) {
      return isAssignable(
          target.toArrayElementType(factory), target.toArrayElementType(factory), appView);
    }
    // TODO(b/166570659): Do a sub-type check that allows for missing classes in hierarchy.
    return MemberType.fromDexType(source) == MemberType.fromDexType(target);
  }

  private DexType byteCharShortOrBooleanToInt(DexType type, DexItemFactory factory) {
    // byte, char, short and boolean has verification type int.
    if (type.isByteType() || type.isCharType() || type.isShortType() || type.isBooleanType()) {
      return factory.intType;
    }
    return type;
  }
}
