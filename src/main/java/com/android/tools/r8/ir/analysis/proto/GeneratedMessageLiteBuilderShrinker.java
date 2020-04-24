// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfo;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.controlflow.SwitchCaseAnalyzer;
import com.android.tools.r8.ir.optimize.enums.EnumValueOptimizer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.inliner.FixedInliningReasonStrategy;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;

public class GeneratedMessageLiteBuilderShrinker {

  private final AppView<? extends AppInfoWithSubtyping> appView;
  private final ProtoReferences references;

  private final Map<DexProgramClass, DexEncodedMethod> builders = new IdentityHashMap<>();

  GeneratedMessageLiteBuilderShrinker(
      AppView<? extends AppInfoWithSubtyping> appView, ProtoReferences references) {
    this.appView = appView;
    this.references = references;
  }

  /** Returns true if an action was deferred. */
  public boolean deferDeadProtoBuilders(
      DexProgramClass clazz, DexEncodedMethod context, BooleanSupplier register) {
    if (references.isDynamicMethod(context) && references.isGeneratedMessageLiteBuilder(clazz)) {
      if (register.getAsBoolean()) {
        assert builders.getOrDefault(clazz, context) == context;
        builders.put(clazz, context);
        return true;
      }
    }
    return false;
  }

  /**
   * Reprocesses each dynamicMethod() that references a dead builder to remove the dead builder
   * references.
   */
  public void removeDeadBuilderReferencesFromDynamicMethods(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("Remove dead builder references");
    AppInfoWithLiveness appInfo = appView.appInfo();
    IRConverter converter = new IRConverter(appView, Timing.empty());
    CodeRewriter codeRewriter = new CodeRewriter(appView, converter);
    MethodToInvokeSwitchCaseAnalyzer switchCaseAnalyzer = new MethodToInvokeSwitchCaseAnalyzer();
    if (switchCaseAnalyzer.isInitialized()) {
      ThreadUtils.processItems(
          builders.entrySet(),
          entry -> {
            if (!appInfo.isLiveProgramClass(entry.getKey())) {
              removeDeadBuilderReferencesFromDynamicMethod(
                  appView, entry.getValue(), converter, codeRewriter, switchCaseAnalyzer);
            }
          },
          executorService);
    }
    builders.clear();
    timing.end(); // Remove dead builder references
  }

  private void removeDeadBuilderReferencesFromDynamicMethod(
      AppView<AppInfoWithLiveness> appView,
      DexEncodedMethod dynamicMethod,
      IRConverter converter,
      CodeRewriter codeRewriter,
      SwitchCaseAnalyzer switchCaseAnalyzer) {
    Origin origin = appView.appInfo().originFor(dynamicMethod.holder());
    IRCode code = dynamicMethod.buildIR(appView, origin);
    codeRewriter.rewriteSwitch(code, switchCaseAnalyzer);
    converter.removeDeadCodeAndFinalizeIR(
        dynamicMethod, code, OptimizationFeedbackSimple.getInstance(), Timing.empty());
  }

  public static void addInliningHeuristicsForBuilderInlining(
      AppView<? extends AppInfoWithSubtyping> appView,
      SubtypingInfo subtypingInfo,
      PredicateSet<DexType> alwaysClassInline,
      Set<DexType> neverMerge,
      Set<DexMethod> alwaysInline,
      Set<DexMethod> bypassClinitforInlining) {
    new RootSetExtension(
            appView, alwaysClassInline, neverMerge, alwaysInline, bypassClinitforInlining)
        .extend(subtypingInfo);
  }

  public void preprocessCallGraphBeforeCycleElimination(Map<DexMethod, Node> nodes) {
    Node node = nodes.get(references.generatedMessageLiteBuilderMethods.constructorMethod);
    if (node != null) {
      List<Node> calleesToBeRemoved = new ArrayList<>();
      for (Node callee : node.getCalleesWithDeterministicOrder()) {
        if (references.isDynamicMethodBridge(callee.method)) {
          calleesToBeRemoved.add(callee);
        }
      }
      for (Node callee : calleesToBeRemoved) {
        callee.removeCaller(node);
      }
    }
  }

  public void inlineCallsToDynamicMethod(
      DexEncodedMethod method,
      IRCode code,
      EnumValueOptimizer enumValueOptimizer,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      Inliner inliner) {
    strengthenCheckCastInstructions(code);

    ProtoInliningReasonStrategy inliningReasonStrategy =
        new ProtoInliningReasonStrategy(appView, new FixedInliningReasonStrategy(Reason.NEVER));
    inliner.performInlining(method, code, feedback, methodProcessor, inliningReasonStrategy);

    // Run the enum optimization to optimize all Enum.ordinal() invocations. This is required to
    // get rid of the enum switch in dynamicMethod().
    if (enumValueOptimizer != null) {
      enumValueOptimizer.rewriteConstantEnumMethodCalls(code);
    }
  }

  /**
   * This method tries to strengthen the type of check-cast instructions that cast a value to
   * GeneratedMessageLite.
   *
   * <p>New proto messages are created by calling dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE)
   * and casting the result to GeneratedMessageLite.
   *
   * <p>If we encounter the following pattern, then we cannot inline the second call to
   * dynamicMethod, because we don't have a precise receiver type.
   *
   * <pre>
   *   GeneratedMessageLite msg =
   *       (GeneratedMessageLite)
   *           Message.DEFAULT_INSTANCE.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
   *   GeneratedMessageLite msg2 =
   *       (GeneratedMessageLite) msg.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
   * </pre>
   *
   * <p>This method therefore optimizes the code above into:
   *
   * <pre>
   *   Message msg =
   *       (Message) Message.DEFAULT_INSTANCE.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
   *   Message msg2 = (Message) msg.dynamicMethod(MethodToInvoke.NEW_MUTABLE_INSTANCE);
   * </pre>
   *
   * <p>This is assuming that calling dynamicMethod() on a proto message with
   * MethodToInvoke.NEW_MUTABLE_INSTANCE will create an instance of the enclosing class.
   */
  private void strengthenCheckCastInstructions(IRCode code) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    InstructionListIterator instructionIterator = code.instructionListIterator();
    CheckCast checkCast;
    while ((checkCast = instructionIterator.nextUntil(Instruction::isCheckCast)) != null) {
      if (checkCast.getType() != references.generatedMessageLiteType) {
        continue;
      }
      Value root = checkCast.object().getAliasedValue();
      if (root.isPhi() || !root.definition.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = root.definition.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!references.isDynamicMethod(invokedMethod)
          && !references.isDynamicMethodBridge(invokedMethod)) {
        continue;
      }
      assert invokedMethod.proto.parameters.values[0] == references.methodToInvokeType;
      Value methodToInvokeValue = invoke.arguments().get(1);
      if (!references.methodToInvokeMembers.isNewMutableInstanceEnum(methodToInvokeValue)) {
        continue;
      }
      ClassTypeElement receiverType =
          invoke.getReceiver().getDynamicUpperBoundType(appView).asClassType();
      if (receiverType != null) {
        AppInfoWithClassHierarchy appInfo = appView.appInfo();
        DexType rawReceiverType = receiverType.getClassType();
        if (appInfo.isStrictSubtypeOf(rawReceiverType, references.generatedMessageLiteType)) {
          Value dest = code.createValue(receiverType.asMaybeNull(), checkCast.getLocalInfo());
          CheckCast replacement = new CheckCast(dest, checkCast.object(), rawReceiverType);
          instructionIterator.replaceCurrentInstruction(replacement, affectedValues);
        }
      }
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
  }

  private class MethodToInvokeSwitchCaseAnalyzer extends SwitchCaseAnalyzer {

    private final int newBuilderOrdinal;

    private MethodToInvokeSwitchCaseAnalyzer() {
      EnumValueInfoMap enumValueInfoMap =
          appView.appInfo().withLiveness().getEnumValueInfoMap(references.methodToInvokeType);
      if (enumValueInfoMap != null) {
        EnumValueInfo newBuilderValueInfo =
            enumValueInfoMap.getEnumValueInfo(references.methodToInvokeMembers.newBuilderField);
        if (newBuilderValueInfo != null) {
          newBuilderOrdinal = newBuilderValueInfo.ordinal;
          return;
        }
      }
      newBuilderOrdinal = -1;
    }

    public boolean isInitialized() {
      return newBuilderOrdinal >= 0;
    }

    @Override
    public boolean switchCaseIsUnreachable(Switch theSwitch, int index) {
      if (theSwitch.isStringSwitch()) {
        assert false : "Unexpected string-switch instruction in dynamicMethod()";
        return false;
      }
      if (index != newBuilderOrdinal) {
        return false;
      }
      Value switchValue = theSwitch.value();
      if (!switchValue.isDefinedByInstructionSatisfying(Instruction::isInvokeVirtual)) {
        return false;
      }
      InvokeVirtual definition = switchValue.definition.asInvokeVirtual();
      if (definition.getInvokedMethod() != appView.dexItemFactory().enumMethods.ordinal) {
        return false;
      }
      TypeElement enumType = definition.getReceiver().getType();
      return enumType.isClassType()
          && enumType.asClassType().getClassType() == references.methodToInvokeType;
    }
  }

  private static class RootSetExtension {

    private final AppView<? extends AppInfoWithSubtyping> appView;
    private final ProtoReferences references;

    private final PredicateSet<DexType> alwaysClassInline;
    private final Set<DexType> neverMerge;

    private final Set<DexMethod> alwaysInline;
    private final Set<DexMethod> bypassClinitforInlining;

    RootSetExtension(
        AppView<? extends AppInfoWithSubtyping> appView,
        PredicateSet<DexType> alwaysClassInline,
        Set<DexType> neverMerge,
        Set<DexMethod> alwaysInline,
        Set<DexMethod> bypassClinitforInlining) {
      this.appView = appView;
      this.references = appView.protoShrinker().references;
      this.alwaysClassInline = alwaysClassInline;
      this.neverMerge = neverMerge;
      this.alwaysInline = alwaysInline;
      this.bypassClinitforInlining = bypassClinitforInlining;
    }

    void extend(SubtypingInfo subtypingInfo) {
      alwaysClassInlineGeneratedMessageLiteBuilders();

      // GeneratedMessageLite heuristics.
      alwaysInlineCreateBuilderFromGeneratedMessageLite();

      // * extends GeneratedMessageLite heuristics.
      bypassClinitforInliningNewBuilderMethods(subtypingInfo);

      // GeneratedMessageLite$Builder heuristics.
      alwaysInlineBuildPartialFromGeneratedMessageLiteExtendableBuilder();
      neverMergeGeneratedMessageLiteBuilder();
    }

    private void alwaysClassInlineGeneratedMessageLiteBuilders() {
      alwaysClassInline.addPredicate(
          type ->
              appView
                  .appInfo()
                  .isStrictSubtypeOf(type, references.generatedMessageLiteBuilderType));
    }

    private void bypassClinitforInliningNewBuilderMethods(SubtypingInfo subtypingInfo) {
      for (DexType type : subtypingInfo.subtypes(references.generatedMessageLiteType)) {
        DexProgramClass clazz = appView.definitionFor(type).asProgramClass();
        if (clazz != null) {
          DexEncodedMethod newBuilderMethod =
              clazz.lookupDirectMethod(
                  method -> method.method.name == references.newBuilderMethodName);
          if (newBuilderMethod != null) {
            bypassClinitforInlining.add(newBuilderMethod.method);
          }
        }
      }
    }

    private void alwaysInlineBuildPartialFromGeneratedMessageLiteExtendableBuilder() {
      alwaysInline.add(references.generatedMessageLiteExtendableBuilderMethods.buildPartialMethod);
    }

    private void alwaysInlineCreateBuilderFromGeneratedMessageLite() {
      alwaysInline.add(references.generatedMessageLiteMethods.createBuilderMethod);
    }

    private void neverMergeGeneratedMessageLiteBuilder() {
      // For consistency, never merge the GeneratedMessageLite builders. These will only have a
      // unique subtype if the application has a single proto message, which mostly happens during
      // testing.
      neverMerge.add(references.generatedMessageLiteBuilderType);
      neverMerge.add(references.generatedMessageLiteExtendableBuilderType);
    }
  }
}
