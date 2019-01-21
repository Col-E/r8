// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.code.DominatorTree.Assumption.MAY_HAVE_UNREACHABLE_BLOCKS;
import static com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization.Strategy.ALLOW_ARGUMENT_REMOVAL;
import static com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization.Strategy.DISALLOW_ARGUMENT_REMOVAL;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.graph.GraphLense.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.GraphLense.RewrittenPrototypeDescription.RemovedArgumentInfo;
import com.android.tools.r8.graph.GraphLense.RewrittenPrototypeDescription.RemovedArgumentsInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.MethodPoolCollection.MethodPool;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class UninstantiatedTypeOptimization {

  enum Strategy {
    ALLOW_ARGUMENT_REMOVAL,
    DISALLOW_ARGUMENT_REMOVAL
  }

  static class UninstantiatedTypeOptimizationGraphLense extends NestedGraphLense {

    private final Map<DexMethod, RemovedArgumentsInfo> removedArgumentsInfoPerMethod;

    UninstantiatedTypeOptimizationGraphLense(
        BiMap<DexMethod, DexMethod> methodMap,
        Map<DexMethod, RemovedArgumentsInfo> removedArgumentsInfoPerMethod,
        AppView<? extends AppInfo> appView) {
      super(
          ImmutableMap.of(),
          methodMap,
          ImmutableMap.of(),
          null,
          methodMap.inverse(),
          appView.graphLense(),
          appView.dexItemFactory());
      this.removedArgumentsInfoPerMethod = removedArgumentsInfoPerMethod;
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChanges(DexMethod method) {
      DexMethod originalMethod = originalMethodSignatures.getOrDefault(method, method);
      RewrittenPrototypeDescription result = previousLense.lookupPrototypeChanges(originalMethod);
      if (originalMethod != method) {
        if (method.proto.returnType.isVoidType() && !originalMethod.proto.returnType.isVoidType()) {
          result = result.withConstantReturn();
        }
        RemovedArgumentsInfo removedArgumentsInfo = removedArgumentsInfoPerMethod.get(method);
        if (removedArgumentsInfo != null) {
          result = result.withRemovedArguments(removedArgumentsInfo);
        }
      } else {
        assert !removedArgumentsInfoPerMethod.containsKey(method);
      }
      return result;
    }
  }

  private final AppView<? extends AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  private int numberOfInstanceGetOrInstancePutWithNullReceiver = 0;
  private int numberOfInvokesWithNullArgument = 0;
  private int numberOfInvokesWithNullReceiver = 0;

  public UninstantiatedTypeOptimization(
      AppView<? extends AppInfoWithLiveness> appView, InternalOptions options) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = options;
  }

  public GraphLense run(
      MethodPoolCollection methodPoolCollection, ExecutorService executorService, Timing timing) {
    BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();
    Map<DexMethod, RemovedArgumentsInfo> removedArgumentsInfoPerMethod = new IdentityHashMap<>();

    MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();

    Map<Wrapper<DexMethod>, Set<DexType>> changedVirtualMethods = new HashMap<>();

    try {
      methodPoolCollection.buildAll(executorService, timing);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    TopDownClassHierarchyTraversal.visit(
        appView,
        appView.appInfo().classes(),
        clazz -> {
          MethodPool methodPool = methodPoolCollection.get(clazz);

          if (clazz.isInterface()) {
            // Do not allow changing the prototype of methods that override an interface method.
            // This achieved by faking that there is already a method with the given signature.
            for (DexEncodedMethod virtualMethod : clazz.virtualMethods()) {
              RewrittenPrototypeDescription prototypeChanges =
                  new RewrittenPrototypeDescription(
                      isAlwaysNull(virtualMethod.method.proto.returnType),
                      getRemovedArgumentsInfo(virtualMethod, ALLOW_ARGUMENT_REMOVAL));
              if (!prototypeChanges.isEmpty()) {
                DexMethod newMethod = getNewMethodSignature(virtualMethod, prototypeChanges);
                Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);
                if (!methodPool.hasSeenDirectly(wrapper)) {
                  methodPool.seen(wrapper);
                }
              }
            }
            return;
          }

          Map<DexEncodedMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod =
              new IdentityHashMap<>();
          for (DexEncodedMethod directMethod : clazz.directMethods()) {
            RewrittenPrototypeDescription prototypeChanges =
                getPrototypeChanges(directMethod, ALLOW_ARGUMENT_REMOVAL);
            if (!prototypeChanges.isEmpty()) {
              prototypeChangesPerMethod.put(directMethod, prototypeChanges);
            }
          }

          // Reserve all signatures which are known to not be touched below.
          Set<Wrapper<DexMethod>> usedSignatures = new HashSet<>();
          for (DexEncodedMethod method : clazz.methods()) {
            if (!prototypeChangesPerMethod.containsKey(method)) {
              usedSignatures.add(equivalence.wrap(method.method));
            }
          }

          // Change the return type of direct methods that return an uninstantiated type to void.
          DexEncodedMethod[] directMethods = clazz.directMethods();
          for (int i = 0; i < directMethods.length; ++i) {
            DexEncodedMethod encodedMethod = directMethods[i];
            DexMethod method = encodedMethod.method;
            RewrittenPrototypeDescription prototypeChanges =
                prototypeChangesPerMethod.getOrDefault(
                    encodedMethod, RewrittenPrototypeDescription.none());
            RemovedArgumentsInfo removedArgumentsInfo = prototypeChanges.getRemovedArgumentsInfo();
            DexMethod newMethod = getNewMethodSignature(encodedMethod, prototypeChanges);
            if (newMethod != method) {
              Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);

              // TODO(b/110806787): Can be extended to handle collisions by renaming the given
              // method.
              if (usedSignatures.add(wrapper)) {
                directMethods[i] = encodedMethod.toTypeSubstitutedMethod(newMethod);
                methodMapping.put(method, newMethod);
                if (removedArgumentsInfo.hasRemovedArguments()) {
                  removedArgumentsInfoPerMethod.put(newMethod, removedArgumentsInfo);
                }
              }
            }
          }

          // Change the return type of virtual methods that return an uninstantiated type to void.
          // This is done in two steps. First we change the return type of all methods that override
          // a method whose return type has already been changed to void previously. Note that
          // all supertypes of the current class are always visited prior to the current class.
          // This is important to ensure that a method that used to override a method in its super
          // class will continue to do so after this optimization.
          DexEncodedMethod[] virtualMethods = clazz.virtualMethods();
          for (int i = 0; i < virtualMethods.length; ++i) {
            DexEncodedMethod encodedMethod = virtualMethods[i];
            DexMethod method = encodedMethod.method;
            RewrittenPrototypeDescription prototypeChanges =
                getPrototypeChanges(encodedMethod, DISALLOW_ARGUMENT_REMOVAL);
            DexMethod newMethod = getNewMethodSignature(encodedMethod, prototypeChanges);
            if (newMethod != method) {
              Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);

              boolean isOverrideOfPreviouslyChangedMethodInSuperClass =
                  changedVirtualMethods.getOrDefault(equivalence.wrap(method), ImmutableSet.of())
                      .stream()
                      .anyMatch(other -> clazz.type.isSubtypeOf(other, appView.appInfo()));
              if (isOverrideOfPreviouslyChangedMethodInSuperClass) {
                assert methodPool.hasSeen(wrapper);

                boolean signatureIsAvailable = usedSignatures.add(wrapper);
                assert signatureIsAvailable;

                virtualMethods[i] = encodedMethod.toTypeSubstitutedMethod(newMethod);
                methodMapping.put(method, newMethod);
              }
            }
          }
          for (int i = 0; i < virtualMethods.length; ++i) {
            DexEncodedMethod encodedMethod = virtualMethods[i];
            DexMethod method = encodedMethod.method;
            RewrittenPrototypeDescription prototypeChanges =
                getPrototypeChanges(encodedMethod, DISALLOW_ARGUMENT_REMOVAL);
            DexMethod newMethod = getNewMethodSignature(encodedMethod, prototypeChanges);
            if (newMethod != method) {
              Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);

              // TODO(b/110806787): Can be extended to handle collisions by renaming the given
              // method. Note that this also requires renaming all of the methods that override this
              // method, though.
              if (!methodPool.hasSeen(wrapper) && usedSignatures.add(wrapper)) {
                methodPool.seen(wrapper);

                virtualMethods[i] = encodedMethod.toTypeSubstitutedMethod(newMethod);
                methodMapping.put(method, newMethod);

                boolean added =
                    changedVirtualMethods
                        .computeIfAbsent(equivalence.wrap(method), key -> Sets.newIdentityHashSet())
                        .add(clazz.type);
                assert added;
              }
            }
          }
        });

    // TODO(christofferqa): There is no need to do anything at the call site when the graph lense
    // is unchanged!
    if (!methodMapping.isEmpty()) {
      return new UninstantiatedTypeOptimizationGraphLense(
          methodMapping, removedArgumentsInfoPerMethod, appView);
    }
    return appView.graphLense();
  }

  private RewrittenPrototypeDescription getPrototypeChanges(
      DexEncodedMethod encodedMethod, Strategy strategy) {
    if (ArgumentRemovalUtils.isPinned(encodedMethod, appView)
        || appView.appInfo().keepConstantArguments.contains(encodedMethod.method)) {
      return RewrittenPrototypeDescription.none();
    }
    return new RewrittenPrototypeDescription(
        isAlwaysNull(encodedMethod.method.proto.returnType),
        getRemovedArgumentsInfo(encodedMethod, strategy));
  }

  private RemovedArgumentsInfo getRemovedArgumentsInfo(
      DexEncodedMethod encodedMethod, Strategy strategy) {
    if (strategy == DISALLOW_ARGUMENT_REMOVAL) {
      return RemovedArgumentsInfo.empty();
    }

    List<RemovedArgumentInfo> removedArgumentsInfo = null;
    DexProto proto = encodedMethod.method.proto;
    int offset = encodedMethod.isStatic() ? 0 : 1;
    for (int i = 0; i < proto.parameters.size(); ++i) {
      DexType type = proto.parameters.values[i];
      if (isAlwaysNull(type)) {
        if (removedArgumentsInfo == null) {
          removedArgumentsInfo = new ArrayList<>();
        }
        removedArgumentsInfo.add(
            RemovedArgumentInfo.builder().setArgumentIndex(i + offset).setIsAlwaysNull().build());
      }
    }
    return removedArgumentsInfo != null
        ? new RemovedArgumentsInfo(removedArgumentsInfo)
        : RemovedArgumentsInfo.empty();
  }

  private DexMethod getNewMethodSignature(
      DexEncodedMethod encodedMethod, RewrittenPrototypeDescription prototypeChanges) {
    DexMethod method = encodedMethod.method;
    RemovedArgumentsInfo removedArgumentsInfo = prototypeChanges.getRemovedArgumentsInfo();

    if (prototypeChanges.isEmpty()) {
      return method;
    }

    DexType newReturnType =
        prototypeChanges.hasBeenChangedToReturnVoid()
            ? dexItemFactory.voidType
            : method.proto.returnType;

    DexType[] newParameters;
    if (removedArgumentsInfo.hasRemovedArguments()) {
      // Currently not allowed to remove the receiver of an instance method. This would involve
      // changing invoke-direct/invoke-virtual into invoke-static.
      assert encodedMethod.isStatic() || !removedArgumentsInfo.isArgumentRemoved(0);
      newParameters =
          new DexType
              [method.proto.parameters.size() - removedArgumentsInfo.numberOfRemovedArguments()];
      int offset = encodedMethod.isStatic() ? 0 : 1;
      int newParametersIndex = 0;
      for (int argumentIndex = 0; argumentIndex < method.proto.parameters.size(); ++argumentIndex) {
        if (!removedArgumentsInfo.isArgumentRemoved(argumentIndex + offset)) {
          newParameters[newParametersIndex] = method.proto.parameters.values[argumentIndex];
          newParametersIndex++;
        }
      }
    } else {
      newParameters = method.proto.parameters.values;
    }

    return dexItemFactory.createMethod(
        method.holder, dexItemFactory.createProto(newReturnType, newParameters), method.name);
  }

  public void rewrite(DexEncodedMethod method, IRCode code) {
    Set<BasicBlock> blocksToBeRemoved = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blocksToBeRemoved.contains(block)) {
        continue;
      }
      InstructionListIterator instructionIterator = block.listIterator();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.isFieldInstruction()) {
          if (instruction.isInstanceGet() || instruction.isInstancePut()) {
            rewriteInstanceFieldInstruction(
                instruction.asFieldInstruction(),
                blockIterator,
                instructionIterator,
                code,
                blocksToBeRemoved);
          } else {
            rewriteStaticFieldInstruction(
                instruction.asFieldInstruction(),
                blockIterator,
                instructionIterator,
                code,
                blocksToBeRemoved);
          }
        } else if (instruction.isInvokeMethod()) {
          rewriteInvoke(
              instruction.asInvokeMethod(),
              blockIterator,
              instructionIterator,
              code,
              blocksToBeRemoved);
        }
      }
    }
    code.removeBlocks(blocksToBeRemoved);
    code.removeAllTrivialPhis();
    code.removeUnreachableBlocks();
    assert code.isConsistentSSA();
  }

  public void logResults() {
    assert Log.ENABLED;
    Log.info(
        getClass(),
        "Number of instance-get/instance-put with null receiver: %s",
        numberOfInstanceGetOrInstancePutWithNullReceiver);
    Log.info(
        getClass(), "Number of invokes with null argument: %s", numberOfInvokesWithNullArgument);
    Log.info(
        getClass(), "Number of invokes with null receiver: %s", numberOfInvokesWithNullReceiver);
  }

  private void rewriteInstanceFieldInstruction(
      FieldInstruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    assert instruction.isInstanceGet() || instruction.isInstancePut();
    boolean replacedByThrowNull = false;

    Value receiver = instruction.inValues().get(0);
    if (isAlwaysNull(receiver)) {
      // Unable to rewrite instruction if the receiver is defined from "const-number 0", since this
      // would lead to an IncompatibleClassChangeError (see MemberResolutionTest#lookupStaticField-
      // WithFieldGetFromNullReferenceDirectly).
      if (!receiver.getTypeLattice().isDefinitelyNull()) {
        replaceCurrentInstructionWithThrowNull(
            instruction, blockIterator, instructionIterator, code, blocksToBeRemoved);
        ++numberOfInstanceGetOrInstancePutWithNullReceiver;
        replacedByThrowNull = true;
      }
    }

    if (!replacedByThrowNull) {
      rewriteFieldInstruction(
          instruction, blockIterator, instructionIterator, code, blocksToBeRemoved);
    }
  }

  private void rewriteStaticFieldInstruction(
      FieldInstruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    assert instruction.isStaticGet() || instruction.isStaticPut();
    rewriteFieldInstruction(
        instruction, blockIterator, instructionIterator, code, blocksToBeRemoved);
  }

  private void rewriteFieldInstruction(
      FieldInstruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    DexType fieldType = instruction.getField().type;
    if (isAlwaysNull(fieldType)) {
      // Before trying to remove this instruction, we need to be sure that the field actually
      // exists. Otherwise this instruction would throw a NoSuchFieldError exception.
      DexEncodedField field = appView.appInfo().definitionFor(instruction.getField());
      if (field == null) {
        return;
      }

      // We also need to be sure that this field instruction cannot trigger static class
      // initialization.
      if (field.field.clazz != code.method.method.holder) {
        DexClass enclosingClass = appView.appInfo().definitionFor(code.method.method.holder);
        if (enclosingClass == null
            || enclosingClass.classInitializationMayHaveSideEffects(appView.appInfo())) {
          return;
        }
      }

      BasicBlock block = instruction.getBlock();
      if (instruction.isFieldPut()) {
        Value value =
            instruction.isInstancePut()
                ? instruction.asInstancePut().value()
                : instruction.asStaticPut().inValue();

        TypeLatticeElement fieldLatticeType =
            TypeLatticeElement.fromDexType(
                fieldType, Nullability.maybeNull(), appView.appInfo());
        if (!value.getTypeLattice().lessThanOrEqual(fieldLatticeType, appView.appInfo())) {
          // Broken type hierarchy. See FieldTypeTest#test_brokenTypeHierarchy.
          assert options.testing.allowTypeErrors;
          return;
        }

        // We know that the right-hand side must be null, so this is a no-op.
        instructionIterator.removeOrReplaceByDebugLocalRead();
      } else {
        // Replace the field read by the constant null.
        instructionIterator.replaceCurrentInstruction(code.createConstNull());
      }

      if (block.hasCatchHandlers()) {
        // This block can no longer throw.
        block.getCatchHandlers().getUniqueTargets().forEach(BasicBlock::unlinkCatchHandler);
      }
    }
  }

  private void rewriteInvoke(
      InvokeMethod invoke,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    if (invoke.isInvokeMethodWithReceiver()) {
      Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
      if (isAlwaysNull(receiver)) {
        replaceCurrentInstructionWithThrowNull(
            invoke, blockIterator, instructionIterator, code, blocksToBeRemoved);
        ++numberOfInvokesWithNullReceiver;
        return;
      }
    }

    DexEncodedMethod target =
        invoke.lookupSingleTarget(appView.appInfo(), code.method.method.holder);
    if (target == null) {
      return;
    }

    BitSet facts = target.getOptimizationInfo().getNonNullParamOrThrow();
    if (facts != null) {
      for (int i = 0; i < invoke.arguments().size(); i++) {
        Value argument = invoke.arguments().get(i);
        if (isAlwaysNull(argument) && facts.get(i)) {
          replaceCurrentInstructionWithThrowNull(
              invoke, blockIterator, instructionIterator, code, blocksToBeRemoved);
          ++numberOfInvokesWithNullArgument;
          return;
        }
      }
    }
  }

  private void replaceCurrentInstructionWithThrowNull(
      Instruction instruction,
      ListIterator<BasicBlock> blockIterator,
      InstructionListIterator instructionIterator,
      IRCode code,
      Set<BasicBlock> blocksToBeRemoved) {
    BasicBlock block = instruction.getBlock();
    assert !blocksToBeRemoved.contains(block);

    BasicBlock normalSuccessorBlock = instructionIterator.split(code, blockIterator);
    instructionIterator.previous();

    // Unlink all blocks that are dominated by successor.
    {
      DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
      blocksToBeRemoved.addAll(block.unlink(normalSuccessorBlock, dominatorTree));
    }

    // Insert constant null before the instruction.
    instructionIterator.previous();
    ConstNumber constNumberInstruction = code.createConstNull();
    // Note that we only keep position info for throwing instructions in release mode.
    constNumberInstruction.setPosition(options.debug ? instruction.getPosition() : Position.none());
    instructionIterator.add(constNumberInstruction);
    instructionIterator.next();

    // Replace the instruction by throw.
    Throw throwInstruction = new Throw(constNumberInstruction.outValue());
    for (Value inValue : instruction.inValues()) {
      if (inValue.hasLocalInfo()) {
        // Add this value as a debug value to avoid changing its live range.
        throwInstruction.addDebugValue(inValue);
      }
    }
    instructionIterator.replaceCurrentInstruction(throwInstruction);
    instructionIterator.next();
    instructionIterator.remove();

    // Remove all catch handlers where the guard does not include NullPointerException.
    if (block.hasCatchHandlers()) {
      CatchHandlers<BasicBlock> catchHandlers = block.getCatchHandlers();
      catchHandlers.forEach(
          (guard, target) -> {
            if (blocksToBeRemoved.contains(target)) {
              // Already removed previously. This may happen if two catch handlers have the same
              // target.
              return;
            }
            if (!dexItemFactory.npeType.isSubtypeOf(guard, appView.appInfo())) {
              // TODO(christofferqa): Consider updating previous dominator tree instead of
              // rebuilding it from scratch.
              DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
              blocksToBeRemoved.addAll(block.unlink(target, dominatorTree));
            }
          });
    }
  }

  private boolean isAlwaysNull(Value value) {
    if (value.hasLocalInfo()) {
      // Not always null as the value can be changed via the debugger.
      return false;
    }
    TypeLatticeElement typeLatticeElement = value.getTypeLattice();
    if (typeLatticeElement.isDefinitelyNull()) {
      return true;
    }
    if (typeLatticeElement.isClassType()) {
      return isAlwaysNull(typeLatticeElement.asClassTypeLatticeElement().getClassType());
    }
    return false;
  }

  private boolean isAlwaysNull(DexType type) {
    if (type.isClassType()) {
      DexClass clazz = appView.appInfo().definitionFor(type);
      return clazz != null
          && clazz.isProgramClass()
          && !appView.appInfo().isInstantiatedDirectlyOrIndirectly(type);
    }
    return false;
  }
}
