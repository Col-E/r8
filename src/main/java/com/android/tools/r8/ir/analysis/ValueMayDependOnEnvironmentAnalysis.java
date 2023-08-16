// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.environmentdependence.ValueGraph;
import com.android.tools.r8.ir.analysis.environmentdependence.ValueGraph.Node;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.LogicalBinop;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An analysis that is used to determine if a value may depend on the runtime environment. The
 * primary use case of this analysis is to determine if a class initializer can safely be postponed:
 *
 * <p>If a class initializer does not have any other side effects than the field assignments to the
 * static fields of the enclosing class, and all the values that are being stored into the static
 * fields do not depend on the runtime environment (i.e., the heap, which can be accessed via
 * static-get instructions), then the class initializer can safely be postponed.
 *
 * <p>All compile time constants are independent of the runtime environment. Furthermore, all newly
 * instantiated arrays and objects are independent of the environment, as long as their content
 * (i.e., array elements and field values) does not depend on the runtime environment.
 *
 * <p>Example: Values that are considered to be independent of the runtime environment:
 *
 * <ul>
 *   <li>{@code true}
 *   <li>{@code 42}
 *   <li>{@code "Hello world!"}
 *   <li>{@code new Object()}
 *   <li>{@code new MyClassWithTrivialInitializer("ABC")}
 *   <li>{@code new MyStandardEnum(42, "A")}
 *   <li>{@code new int[] {0, 1, 2}}
 *   <li>{@code new Object[] {new Object(), new MyClassWithTrivialInitializer()}}
 * </ul>
 *
 * <p>Example: Values that are considered to be dependent on the runtime environment:
 *
 * <ul>
 *   <li>{@code argX} (for methods with arguments)
 *   <li>{@code OtherClass.STATIC_FIELD} (reads a value from the runtime environment)
 *   <li>{@code new MyClassWithTrivialInitializer(OtherClass.FIELD)}
 * </ul>
 */
public class ValueMayDependOnEnvironmentAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final ProgramMethod context;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  public ValueMayDependOnEnvironmentAnalysis(AppView<AppInfoWithLiveness> appView, IRCode code) {
    this.appView = appView;
    this.context = code.context();
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
  }

  public boolean anyValueMayDependOnEnvironment(Iterable<Value> values) {
    ValueGraph graph = new ValueGraph();
    Set<Instruction> consumedInstructions = Sets.newIdentityHashSet();
    Set<Value> mutableValues = Sets.newIdentityHashSet();
    WorkList<Value> worklist = WorkList.newIdentityWorkList(values);
    while (worklist.hasNext()) {
      Value value = worklist.next();
      Value root = value.getAliasedValue();
      Node node = graph.createNodeIfAbsent(root);
      if (root != value) {
        // An alias depends on the environment if the aliased value depends on the environment, thus
        // an edge is added from the alias to the aliased value.
        graph.addDirectedEdge(graph.createNodeIfAbsent(value), node);
      }
      if (!addValueToValueGraph(root, node, graph, consumedInstructions, mutableValues, worklist)) {
        return true;
      }
    }

    // At this point, the graph has been populated with a node for each value of interest, and edges
    // have been added to reflect the dependency. We now attempt to prove that no values depend on
    // environment, starting from the leaves of the graph.
    //
    // First we collapse strongly connected components in the graph. By doing so we will attempt to
    // prove that all values in a strongly connected component are independent of the environment at
    // once.
    graph.mergeStronglyConnectedComponents();

    Set<Node> nodesDependentOnEnvironment = SetUtils.newIdentityHashSet(graph.getNodes());
    while (!nodesDependentOnEnvironment.isEmpty()) {
      Set<Node> newNodesIndependentOfEnvironment = Sets.newIdentityHashSet();
      for (Node node : nodesDependentOnEnvironment) {
        boolean isDependentOfEnvironment =
            node.hasSuccessorThatMatches(
                successor ->
                    nodesDependentOnEnvironment.contains(successor)
                        && !newNodesIndependentOfEnvironment.contains(successor));
        if (!isDependentOfEnvironment) {
          newNodesIndependentOfEnvironment.add(node);
        }
      }
      if (newNodesIndependentOfEnvironment.isEmpty()) {
        return true;
      }
      nodesDependentOnEnvironment.removeAll(newNodesIndependentOfEnvironment);
    }

    // At this point, we have proved that all values in the graph are independent on the
    // environment. However, we still need to prove that they are not mutated between the point
    // where they are defined and all normal exits.
    return anyValueMayBeMutatedBeforeMethodExit(mutableValues, consumedInstructions);
  }

  private boolean addValueToValueGraph(
      Value value,
      Node node,
      ValueGraph graph,
      Set<Instruction> consumedInstructions,
      Set<Value> mutableValues,
      WorkList<Value> worklist) {
    return addConstantValueToValueGraph(value)
        || addArrayValueToValueGraph(
            value, node, graph, consumedInstructions, mutableValues, worklist)
        || addInvokeVirtualValueToValueGraph(
            value, node, graph, consumedInstructions, mutableValues, worklist)
        || addLogicalBinopValueToValueGraph(
            value, node, graph, consumedInstructions, mutableValues, worklist)
        || addNewInstanceValueToValueGraph(
            value, node, graph, consumedInstructions, mutableValues, worklist);
  }

  private boolean addConstantValueToValueGraph(Value value) {
    // Constants do not depend on any other values, thus no edges are added to the graph.
    if (value.isConstant()) {
      return true;
    }
    assert !value.getAliasedValue().isConstant();
    AbstractValue abstractValue = value.getAbstractValue(appView, context);
    if (abstractValue.isSingleConstValue()) {
      return true;
    }
    if (abstractValue.isSingleFieldValue()) {
      DexField fieldReference = abstractValue.asSingleFieldValue().getField();
      DexClass holder = appView.definitionForHolder(fieldReference);
      DexEncodedField field = fieldReference.lookupOnClass(holder);
      if (field != null && field.isEnum()) {
        return true;
      }
    }
    return false;
  }

  private boolean addArrayValueToValueGraph(
      Value value,
      Node node,
      ValueGraph graph,
      Set<Instruction> consumedInstructions,
      Set<Value> mutableValues,
      WorkList<Value> worklist) {
    if (value.isPhi()) {
      // Would need to track the aliases, just give up.
      return false;
    }

    Instruction definition = value.definition;

    // Check that it is a constant array with a known size at this point in the IR.
    if (definition.isNewArrayFilled()) {
      NewArrayFilled newArrayFilled = definition.asNewArrayFilled();
      for (Value argument : newArrayFilled.arguments()) {
        graph.addDirectedEdge(node, graph.createNodeIfAbsent(argument));
        worklist.addIfNotSeen(argument);
      }
    } else if (definition.isNewArrayEmpty()) {
      NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
      Value sizeValue = newArrayEmpty.size();
      graph.addDirectedEdge(node, graph.createNodeIfAbsent(sizeValue));
      worklist.addIfNotSeen(sizeValue);
    } else {
      // Some other array creation.
      return false;
    }

    // Allow array-put and new-array-filled-data instructions that immediately follow the array
    // creation.
    for (Instruction instruction : definition.getBlock().instructionsAfter(definition)) {
      if (instruction.isArrayPut()) {
        ArrayPut arrayPut = instruction.asArrayPut();
        Value array = arrayPut.array().getAliasedValue();
        if (array != value) {
          // This ends the chain of array-put instructions that are allowed immediately after the
          // array creation.
          break;
        }
        graph.addDirectedEdge(node, graph.createNodeIfAbsent(arrayPut.index()));
        worklist.addIfNotSeen(arrayPut.index());
        graph.addDirectedEdge(node, graph.createNodeIfAbsent(arrayPut.value()));
        worklist.addIfNotSeen(arrayPut.value());
      } else if (instruction.isNewArrayFilledData()) {
        NewArrayFilledData newArrayFilledData = instruction.asNewArrayFilledData();
        Value array = newArrayFilledData.src();
        if (array != value) {
          // This ends the chain of array-put instructions that are allowed immediately after the
          // array creation.
          break;
        }
        consumedInstructions.add(instruction);
      } else if (instruction.instructionMayHaveSideEffects(appView, context)) {
        // This ends the chain of array-put instructions that are allowed immediately after the
        // array creation.
        break;
      }
      consumedInstructions.add(instruction);
    }
    mutableValues.add(value);
    return true;
  }

  private boolean addInvokeVirtualValueToValueGraph(
      Value value,
      Node node,
      ValueGraph graph,
      Set<Instruction> consumedInstructions,
      Set<Value> mutableValues,
      WorkList<Value> worklist) {
    if (!value.isDefinedByInstructionSatisfying(Instruction::isInvokeVirtual)) {
      return false;
    }

    InvokeVirtual invoke = value.getDefinition().asInvokeVirtual();
    if (invoke.getInvokedMethod() == dexItemFactory.classMethods.desiredAssertionStatus) {
      // We treat the value from calling MyClass.class.desiredAssertionStatus() as being independent
      // of the environment if MyClass is not pinned.
      return isNonPinnedClassConstant(invoke.getReceiver());
    }

    return false;
  }

  private boolean isNonPinnedClassConstant(Value value) {
    Value root = value.getAliasedValue();
    return root.isDefinedByInstructionSatisfying(Instruction::isConstClass)
        && !appView
            .getKeepInfo()
            .isPinnedWithDefinitionLookup(
                root.getDefinition().asConstClass().getType(), options, appView);
  }

  private boolean addLogicalBinopValueToValueGraph(
      Value value,
      Node node,
      ValueGraph graph,
      Set<Instruction> consumedInstructions,
      Set<Value> mutableValues,
      WorkList<Value> worklist) {
    if (!value.isDefinedByInstructionSatisfying(Instruction::isLogicalBinop)) {
      return false;
    }

    // The result of a logical binop depends on the environment if any of the operands does.
    LogicalBinop logicalBinop = value.getDefinition().asLogicalBinop();
    for (Value inValue : logicalBinop.inValues()) {
      graph.addDirectedEdge(node, graph.createNodeIfAbsent(inValue));
      worklist.addIfNotSeen(inValue);
    }

    return true;
  }

  private boolean addNewInstanceValueToValueGraph(
      Value value,
      Node node,
      ValueGraph graph,
      Set<Instruction> consumedInstructions,
      Set<Value> mutableValues,
      WorkList<Value> worklist) {
    if (!value.isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
      return false;
    }

    NewInstance newInstance = value.definition.asNewInstance();
    DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(newInstance.clazz));
    if (clazz == null) {
      return false;
    }

    // Find the single constructor invocation.
    InvokeDirect constructorInvoke = newInstance.getUniqueConstructorInvoke(dexItemFactory);
    if (constructorInvoke == null) {
      // Didn't find a constructor invocation, give up.
      return false;
    }

    // Check that it is a trivial initializer (otherwise, the constructor could do anything).
    DexClassAndMethod constructor =
        appView
            .appInfo()
            .resolveMethod(
                constructorInvoke.getInvokedMethod(), constructorInvoke.getInterfaceBit())
            .getResolutionPair();
    if (constructor == null) {
      return false;
    }

    if (!options.canInitNewInstanceUsingSuperclassConstructor()
        && constructor.getHolder() != clazz) {
      return false;
    }

    InstanceInitializerInfo initializerInfo =
        constructor.getOptimizationInfo().getInstanceInitializerInfo(constructorInvoke);

    List<DexClassAndField> fields = clazz.getDirectAndIndirectInstanceFields(appView);
    if (!fields.isEmpty()) {
      if (initializerInfo.instanceFieldInitializationMayDependOnEnvironment()) {
        return false;
      }

      // Check that none of the arguments to the constructor depend on the environment.
      for (int i = 1; i < constructorInvoke.arguments().size(); i++) {
        Value argument = constructorInvoke.getArgument(i);
        graph.addDirectedEdge(node, graph.createNodeIfAbsent(argument));
        worklist.addIfNotSeen(argument);
      }

      // Mark this value as mutable if it has a non-final field.
      boolean hasNonFinalField = false;
      for (DexClassAndField field : fields) {
        if (!field.getAccessFlags().isFinal()) {
          hasNonFinalField = true;
          break;
        }
      }
      if (hasNonFinalField) {
        mutableValues.add(value);
      }
    }

    if (!initializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
      consumedInstructions.add(constructorInvoke);
    }

    return true;
  }

  private boolean anyValueMayBeMutatedBeforeMethodExit(
      Set<Value> values, Set<Instruction> whitelist) {
    Set<BasicBlock> initialBlocks = Sets.newIdentityHashSet();
    for (Value value : values) {
      assert !value.isPhi();
      initialBlocks.add(value.definition.getBlock());
    }
    Map<BasicBlock, TrackedValuesState> blockExitStates = new IdentityHashMap<>();
    Deque<BasicBlock> worklist = new ArrayDeque<>(initialBlocks);
    while (!worklist.isEmpty()) {
      BasicBlock block = worklist.removeFirst();
      TrackedValuesState state = computeBlockEntryState(block, blockExitStates);
      boolean changed = false;
      for (Instruction instruction : block.getInstructions()) {
        if (whitelist.contains(instruction)) {
          continue;
        }
        if (instruction.isStaticPut()) {
          StaticPut staticPut = instruction.asStaticPut();
          if (state.isTrackingValue(staticPut.value())) {
            changed |= state.recordTrackedValueHasEscaped();
          }
          if (state.hasTrackedValueEscaped()) {
            DexType holder = staticPut.getField().holder;
            if (holder.classInitializationMayHaveSideEffectsInContext(appView, context)) {
              return true;
            }
          }
          continue;
        }
        if (instruction.instructionMayTriggerMethodInvocation(appView, context)) {
          if (instruction.hasInValueThatMatches(state::isTrackingValue)) {
            changed |= state.recordTrackedValueHasEscaped();
          }
          if (state.hasTrackedValueEscaped()
              && instruction.instructionMayHaveSideEffects(appView, context)) {
            return true;
          }
        }
        if (instruction.hasOutValue() && values.contains(instruction.outValue())) {
          changed |= state.startTrackingValue(instruction.outValue());
        }
      }
      blockExitStates.put(block, state);
      if (changed) {
        worklist.addAll(block.getSuccessors());
      }
    }
    return false;
  }

  private TrackedValuesState computeBlockEntryState(
      BasicBlock block, Map<BasicBlock, TrackedValuesState> states) {
    TrackedValuesState state = new TrackedValuesState();
    for (BasicBlock predecessor : block.getPredecessors()) {
      state.add(states.getOrDefault(predecessor, TrackedValuesState.empty()));
    }
    return state;
  }

  static class TrackedValuesState {

    private static final TrackedValuesState EMPTY = new TrackedValuesState();

    boolean hasTrackedValueEscaped;
    Set<Value> trackedValues = Sets.newIdentityHashSet();

    public static TrackedValuesState empty() {
      return EMPTY;
    }

    public void add(TrackedValuesState state) {
      hasTrackedValueEscaped |= state.hasTrackedValueEscaped;
      trackedValues.addAll(state.trackedValues);
    }

    public boolean hasTrackedValueEscaped() {
      return hasTrackedValueEscaped;
    }

    public boolean isTrackingValue(Value value) {
      return trackedValues.contains(value);
    }

    public boolean recordTrackedValueHasEscaped() {
      if (hasTrackedValueEscaped) {
        return false;
      }
      hasTrackedValueEscaped = true;
      return true;
    }

    public boolean startTrackingValue(Value value) {
      return trackedValues.add(value);
    }
  }
}
