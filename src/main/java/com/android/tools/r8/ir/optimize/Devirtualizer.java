// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult.isOverriding;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeSuper;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.SafeCheckCast;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tries to rewrite virtual invokes to their most specific target by:
 *
 * <pre>
 * 1) Rewriting all invoke-interface instructions that have a unique target on a class into
 * invoke-virtual with the corresponding unique target.
 * 2) Rewriting all invoke-virtual instructions that have a more specific target to an
 * invoke-virtual with the corresponding target.
 * </pre>
 */
public class Devirtualizer {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;

  public Devirtualizer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.options = appView.options();
  }

  @SuppressWarnings("ReferenceEquality")
  public void devirtualizeInvokeInterface(IRCode code) {
    AffectedValues affectedValues = new AffectedValues();
    ProgramMethod context = code.context();
    Map<InvokeInterface, InvokeVirtual> devirtualizedCall = new IdentityHashMap<>();
    DominatorTree dominatorTree = new DominatorTree(code);
    Map<Value, Map<DexType, Value>> castedReceiverCache = new IdentityHashMap<>();
    Set<SafeCheckCast> newCheckCastInstructions = Sets.newIdentityHashSet();

    BasicBlockIterator blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction current = it.next();

        // (out <-) invoke-interface rcv_i, ... I#foo
        // ...  // could be split due to catch handlers
        // non_null_rcv <- non-null rcv_i
        //
        //   ~>
        //
        // rcv_c <- check-cast C rcv_i
        // (out <-) invoke-virtual rcv_c, ... C#foo
        // ...
        // non_null_rcv <- non-null rcv_c  // <- Update the input rcv to the non-null, too.
        if (current.isAssumeWithNonNullAssumption()) {
          Assume nonNull = current.asAssume();
          Instruction origin = nonNull.origin();
          if (origin.isInvokeInterface()
              && !origin.asInvokeInterface().getReceiver().hasLocalInfo()
              && devirtualizedCall.containsKey(origin.asInvokeInterface())
              && origin.asInvokeInterface().getReceiver() == nonNull.getAliasForOutValue()) {
            InvokeVirtual devirtualizedInvoke = devirtualizedCall.get(origin.asInvokeInterface());

            // Extract the newly added check-cast instruction, if any.
            SafeCheckCast newCheckCast = null;
            Value newReceiver = devirtualizedInvoke.getReceiver();
            if (!newReceiver.isPhi() && newReceiver.definition.isSafeCheckCast()) {
              SafeCheckCast definition = newReceiver.definition.asSafeCheckCast();
              if (newCheckCastInstructions.contains(definition)) {
                newCheckCast = definition;
              }
            }

            if (newCheckCast != null) {
              // If this non-null instruction is dominated by the check-cast instruction, then
              // replace the in-value to the non-null instruction, since this gives us more precise
              // type information in the rest of the method. This should only be done, though, if
              // the out-value of the cast instruction is a more precise type than the in-value,
              // otherwise we could introduce type errors.
              Value oldReceiver = newCheckCast.object();
              TypeElement oldReceiverType = oldReceiver.getType();
              TypeElement newReceiverType = newReceiver.getType();
              if (newReceiverType.lessThanOrEqual(oldReceiverType, appView)
                  && dominatorTree.dominatedBy(block, devirtualizedInvoke.getBlock())) {
                assert nonNull.src() == oldReceiver;
                assert !oldReceiver.hasLocalInfo();
                oldReceiver.replaceSelectiveUsers(
                    newReceiver, ImmutableSet.of(nonNull), ImmutableMap.of(), affectedValues);
              }
            }
          }
          continue;
        }

        if (current.isInvokeSuper()) {
          InvokeSuper invoke = current.asInvokeSuper();

          // Check if the instruction can be rewritten to invoke-virtual. This allows inlining of
          // the enclosing method into contexts outside the current class.
          if (options.testing.enableInvokeSuperToInvokeVirtualRewriting) {
            DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
            if (singleTarget != null) {
              DexMethod invokedMethod = invoke.getInvokedMethod();
              DexClassAndMethod newSingleTarget =
                  InvokeVirtual.lookupSingleTarget(
                      appView,
                      context,
                      invoke.getReceiver().getDynamicType(appView),
                      invokedMethod);
              if (newSingleTarget != null
                  && newSingleTarget.getReference() == singleTarget.getReference()) {
                it.replaceCurrentInstruction(
                    new InvokeVirtual(invokedMethod, invoke.outValue(), invoke.arguments()));
                continue;
              }
            }
          }

          // Rebind the invoke to the most specific target.
          DexMethod invokedMethod = invoke.getInvokedMethod();
          DexClass reboundTargetClass = rebindSuperInvokeToMostSpecific(invokedMethod, context);
          if (reboundTargetClass != null) {
            DexMethod reboundMethod =
                invokedMethod.withHolder(reboundTargetClass, appView.dexItemFactory());
            if (reboundMethod != invokedMethod
                && !isRebindingNewClassIntoMainDex(context, reboundMethod)) {
              it.replaceCurrentInstruction(
                  new InvokeSuper(
                      reboundMethod,
                      invoke.outValue(),
                      invoke.arguments(),
                      reboundTargetClass.isInterface()));
            }
          }
          continue;
        }

        if (current.isInvokeVirtual()) {
          InvokeVirtual invoke = current.asInvokeVirtual();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          DexMethod reboundTarget =
              rebindVirtualInvokeToMostSpecific(invokedMethod, invoke.getReceiver(), context);
          if (reboundTarget != invokedMethod) {
            it.replaceCurrentInstruction(
                new InvokeVirtual(reboundTarget, invoke.outValue(), invoke.arguments()));
          }
          continue;
        }

        if (!current.isInvokeInterface()) {
          continue;
        }
        InvokeInterface invoke = current.asInvokeInterface();
        DexClassAndMethod target = invoke.lookupSingleTarget(appView, context);
        if (target == null) {
          continue;
        }
        DexClass holderClass = target.getHolder();
        // Make sure we are not landing on another interface, e.g., interface's default method.
        if (holderClass == null || holderClass.isInterface()) {
          continue;
        }

        // Due to the potential downcast below, make sure the new target holder is visible.
        if (AccessControl.isClassAccessible(holderClass, context, appView).isPossiblyFalse()) {
          continue;
        }

        // Ensure that we are not adding a new main dex root
        if (isRebindingNewClassIntoMainDex(context, target.getReference())) {
          continue;
        }

        InvokeVirtual devirtualizedInvoke =
            new InvokeVirtual(target.getReference(), invoke.outValue(), invoke.inValues());
        it.replaceCurrentInstruction(devirtualizedInvoke);
        devirtualizedCall.put(invoke, devirtualizedInvoke);

        // We may need to add downcast together. E.g.,
        // i <- check-cast I o  // suppose it is known to be of type class A, not interface I
        // (out <-) invoke-interface i, ... I#foo
        //
        //  ~>
        //
        // i <- check-cast I o  // could be removed by {@link
        // CodeRewriter#removeTrivialCheckCastAndInstanceOfInstructions}.
        // a <- check-cast A i  // Otherwise ART verification error.
        // (out <-) invoke-virtual a, ... A#foo
        if (holderClass.getType() != invoke.getInvokedMethod().holder) {
          Value receiver = invoke.getReceiver();
          TypeElement receiverTypeLattice = receiver.getType();
          TypeElement castTypeLattice =
              TypeElement.fromDexType(
                  holderClass.getType(), receiverTypeLattice.nullability(), appView);
          // Avoid adding trivial cast and up-cast.
          // We should not use strictlyLessThan(castType, receiverType), which detects downcast,
          // due to side-casts, e.g., A (unused) < I, B < I, and cast from A to B.
          if (!receiverTypeLattice.lessThanOrEqual(castTypeLattice, appView)) {
            Value newReceiver = null;
            // If this value is ever downcast'ed to the same holder type before, and that casted
            // value is safely accessible, i.e., the current line is dominated by that cast, use it.
            // Otherwise, we will see something like:
            // ...
            // a1 <- check-cast A i
            // invoke-virtual a1, ... A#m1 (from I#m1)
            // ...
            // a2 <- check-cast A i  // We should be able to reuse a1 here!
            // invoke-virtual a2, ... A#m2 (from I#m2)
            if (castedReceiverCache.containsKey(receiver)
                && castedReceiverCache.get(receiver).containsKey(holderClass.getType())) {
              Value cachedReceiver = castedReceiverCache.get(receiver).get(holderClass.getType());
              BasicBlock cachedReceiverBlock = cachedReceiver.definition.getBlock();
              BasicBlock dominatorBlock = null;
              if (cachedReceiverBlock.hasCatchHandlers()) {
                if (cachedReceiverBlock.hasUniqueNormalSuccessor()) {
                  dominatorBlock = cachedReceiverBlock.getUniqueNormalSuccessor();
                } else {
                  assert false;
                }
              } else {
                dominatorBlock = cachedReceiverBlock;
              }
              if (dominatorBlock != null && dominatorTree.dominatedBy(block, dominatorBlock)) {
                newReceiver = cachedReceiver;
              }
            }

            // No cached, we need a new downcast'ed receiver.
            if (newReceiver == null) {
              newReceiver = code.createValue(castTypeLattice);
              // Cache the new receiver with a narrower type to avoid redundant checkcast.
              if (!receiver.hasLocalInfo()) {
                castedReceiverCache.putIfAbsent(receiver, new IdentityHashMap<>());
                castedReceiverCache.get(receiver).put(holderClass.getType(), newReceiver);
              }
              SafeCheckCast checkCast =
                  new SafeCheckCast(newReceiver, receiver, holderClass.getType());
              checkCast.setPosition(invoke.getPosition());
              newCheckCastInstructions.add(checkCast);

              // We need to add this checkcast *before* the devirtualized invoke-virtual.
              assert it.peekPrevious() == devirtualizedInvoke;
              it.previous();

              // If the current block has catch handlers, then split the block before adding the new
              // check-cast instruction. The catch handlers are copied to the split block to ensure
              // that all throwing instructions are covered by a catch-all catch handler in case of
              // monitor instructions (see also b/174167294).
              BasicBlock blockWithDevirtualizedInvoke =
                  block.hasCatchHandlers()
                      ? it.splitCopyCatchHandlers(code, blocks, options)
                      : block;
              if (blockWithDevirtualizedInvoke != block) {
                // If we split, add the new checkcast at the end of the currently visiting block.
                block.listIterator(code, block.getInstructions().size() - 1).add(checkCast);
                // Update the dominator tree after the split.
                dominatorTree = new DominatorTree(code);
                // Restore the cursor.
                it = blockWithDevirtualizedInvoke.listIterator(code);
                assert it.peekNext() == devirtualizedInvoke;
                it.next();
              } else {
                // Otherwise, just add it to the current block at the position of the iterator.
                it.add(checkCast);
                // Restore the cursor.
                assert it.peekNext() == devirtualizedInvoke;
                it.next();
              }
            }
            if (!receiver.hasLocalInfo()) {
              receiver.replaceSelectiveUsers(
                  newReceiver,
                  ImmutableSet.of(devirtualizedInvoke),
                  ImmutableMap.of(),
                  affectedValues);
            } else {
              devirtualizedInvoke.replaceValue(receiver, newReceiver, affectedValues);
            }
          }
        }
      }
    }
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }

  /** This rebinds invoke-super instructions to their most specific target. */
  @SuppressWarnings("ReferenceEquality")
  private DexClass rebindSuperInvokeToMostSpecific(DexMethod target, ProgramMethod context) {
    DexClassAndMethod method = appView.appInfo().lookupSuperTarget(target, context, appView);
    if (method == null) {
      return null;
    }

    if (method.getHolder().isInterface()
        && method.getHolderType() != context.getHolder().superType) {
      // Not allowed.
      return null;
    }

    if (AccessControl.isMemberAccessible(method, method.getHolder(), context, appView)
        .isPossiblyFalse()) {
      return null;
    }

    if (method.getHolder().isLibraryClass()) {
      // We've found a library class as the new holder of the method. Since the library can only
      // rebind to the library class boundary. Search from the target upwards until we find a
      // library class.
      DexClass lowerBound = appView.definitionFor(target.getHolderType(), context);
      while (lowerBound != null
          && lowerBound.isProgramClass()
          && lowerBound != method.getHolder()) {
        lowerBound = appView.definitionFor(lowerBound.superType, lowerBound.asProgramClass());
      }
      return lowerBound;
    }

    return method.getHolder();
  }

  /**
   * This rebinds invoke-virtual instructions to their most specific target.
   *
   * <p>As a simple example, consider the instruction "invoke-virtual A.foo(v0)", and assume that v0
   * is defined by an instruction "new-instance v0, B". If B is a subtype of A, and B overrides the
   * method foo(), then we rewrite the invocation into "invoke-virtual B.foo(v0)".
   *
   * <p>If A.foo() ends up being unused, this helps to ensure that we can get rid of A.foo()
   * entirely. Without this rewriting, we would have to keep A.foo() because the method is targeted.
   */
  @SuppressWarnings("ReferenceEquality")
  private DexMethod rebindVirtualInvokeToMostSpecific(
      DexMethod target, Value receiver, ProgramMethod context) {
    if (!receiver.getType().isClassType()) {
      return target;
    }

    SingleResolutionResult<?> resolutionResult =
        appView
            .appInfo()
            .resolveMethodOnClassLegacy(target.getHolderType(), target)
            .asSingleResolution();
    if (resolutionResult == null
        || resolutionResult
            .isAccessibleForVirtualDispatchFrom(context, appView)
            .isPossiblyFalse()) {
      // Method does not resolve or is not accessible.
      return target;
    }

    DexType receiverType = receiver.getType().asClassType().getClassType();
    if (receiverType == target.holder) {
      // Virtual invoke is already as specific as it can get.
      return target;
    }

    SingleResolutionResult<?> newResolutionResult =
        appView.appInfo().resolveMethodOnClassLegacy(receiverType, target).asSingleResolution();
    if (newResolutionResult == null
        || newResolutionResult
            .isAccessibleForVirtualDispatchFrom(context, appView)
            .isPossiblyFalse()
        || !newResolutionResult
            .getResolvedMethod()
            .isAtLeastAsVisibleAsOtherInSameHierarchy(resolutionResult.getResolvedMethod(), appView)
        // isOverriding expects both arguments to be not private.
        || (!newResolutionResult.getResolvedMethod().isPrivateMethod()
            && !isOverriding(
                resolutionResult.getResolvedMethod(), newResolutionResult.getResolvedMethod()))) {
      return target;
    }

    DexClass newTargetHolder = newResolutionResult.getResolvedHolder();
    if (!newTargetHolder.isProgramClass() || newTargetHolder.isInterface()) {
      // Not safe to invoke the new resolution result with virtual invoke from the current context.
      return target;
    }

    // Change the invoke-virtual instruction to target the refined resolution result instead.
    return newResolutionResult.getResolvedMethod().getReference();
  }

  private boolean isRebindingNewClassIntoMainDex(ProgramMethod context, DexMethod reboundMethod) {
    return !appView
        .appInfo()
        .getMainDexInfo()
        .canRebindReference(context, reboundMethod, appView.getSyntheticItems());
  }
}
