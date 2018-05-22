// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.Inliner.InliningInfo;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ClassInliner {
  private final DexItemFactory factory;
  private final ConcurrentHashMap<DexType, Boolean> knownClasses = new ConcurrentHashMap<>();

  private static final Map<DexField, Integer> NO_MAPPING = new IdentityHashMap<>();

  public interface InlinerAction {
    void inline(Map<InvokeMethodWithReceiver, InliningInfo> methods) throws ApiLevelException;
  }

  public ClassInliner(DexItemFactory factory) {
    this.factory = factory;
  }

  // Process method code and inline eligible class instantiations, in short:
  //
  // - collect all 'new-instance' instructions in the original code. Note that class
  // inlining, if happens, mutates code and can add 'new-instance' instructions.
  // Processing them as well is possible, but does not seem to have much value.
  //
  // - for each 'new-instance' we check if it is eligible for inlining, i.e:
  //     -> the class of the new instance is 'eligible' (see computeClassEligible(...))
  //     -> the instance is initialized with 'eligible' constructor (see
  //        onlyInitializesFieldsWithNoOtherSideEffects flag in method's optimization
  //        info); eligible constructor also defines a set of instance fields directly
  //        initialized with parameter values, called field initialization mapping below
  //     -> has only 'eligible' uses, i.e:
  //          * it is a receiver of a field read if the field is present in the
  //            field initialization mapping
  //          * it is a receiver of virtual or interface call with single target being
  //            a method only reading fields in the current field initialization mapping
  //
  // - inline eligible 'new-instance' instructions, i.e:
  //     -> force inline methods called on the instance (which may introduce additional
  //        instance field reads, but only for fields present in the current field
  //        initialization mapping)
  //     -> replace instance field reads with appropriate values passed to the constructor
  //        according to field initialization mapping
  //     -> remove constructor call
  //     -> remove 'new-instance' instructions
  //
  // For example:
  //
  // Original code:
  //   class C {
  //     static class L {
  //       final int x;
  //       L(int x) {
  //         this.x = x;
  //       }
  //       int getX() {
  //         return x;
  //       }
  //     }
  //     static int method1() {
  //       return new L(1).x;
  //     }
  //     static int method2() {
  //       return new L(1).getX();
  //     }
  //   }
  //
  // Code after class C is 'inlined':
  //   class C {
  //     static int method1() {
  //       return 1;
  //     }
  //     static int method2() {
  //       return 1;
  //     }
  //   }
  //
  public final void processMethodCode(
      AppInfoWithSubtyping appInfo,
      DexEncodedMethod method,
      IRCode code,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      InlinerAction inliner) throws ApiLevelException {

    // Collect all the new-instance instructions in the code before inlining.
    List<NewInstance> newInstances = Streams.stream(code.instructionIterator())
        .filter(Instruction::isNewInstance)
        .map(Instruction::asNewInstance)
        .collect(Collectors.toList());

    nextNewInstance:
    for (NewInstance newInstance : newInstances) {
      Value eligibleInstance = newInstance.outValue();
      if (eligibleInstance == null) {
        continue;
      }

      DexType eligibleClass = newInstance.clazz;
      if (!isClassEligible(appInfo, eligibleClass)) {
        continue;
      }

      // No Phi users.
      if (eligibleInstance.numberOfPhiUsers() > 0) {
        continue;
      }

      Set<Instruction> uniqueUsers = eligibleInstance.uniqueUsers();

      // Find an initializer invocation.
      InvokeDirect eligibleInitCall = null;
      Map<DexField, Integer> mappings = null;
      for (Instruction user : uniqueUsers) {
        if (!user.isInvokeDirect()) {
          continue;
        }

        InvokeDirect candidate = user.asInvokeDirect();
        DexMethod candidateInit = candidate.getInvokedMethod();
        if (factory.isConstructor(candidateInit) &&
            candidate.inValues().lastIndexOf(eligibleInstance) == 0) {

          if (candidateInit.holder != eligibleClass) {
            // Inlined constructor call? We won't get field initialization mapping in this
            // case, but since we only support eligible classes extending java.lang.Object,
            // it's safe to assume an empty mapping.
            if (candidateInit.holder == factory.objectType) {
              mappings = Collections.emptyMap();
            }

          } else {
            // Is it a call to an *eligible* constructor?
            mappings = getConstructorFieldMappings(appInfo, candidateInit, isProcessedConcurrently);
          }

          eligibleInitCall = candidate;
          break;
        }
      }

      if (mappings == null) {
        continue;
      }

      // Check all regular users.
      Map<InvokeMethodWithReceiver, InliningInfo> methodCalls = new IdentityHashMap<>();

      for (Instruction user : uniqueUsers) {
        if (user == eligibleInitCall) {
          continue /* next user */;
        }

        if (user.isInstanceGet()) {
          InstanceGet instanceGet = user.asInstanceGet();
          if (mappings.containsKey(instanceGet.getField())) {
            continue /* next user */;
          }

          // Not replaceable field read.
          continue nextNewInstance;
        }

        if (user.isInvokeVirtual() || user.isInvokeInterface()) {
          InvokeMethodWithReceiver invoke = user.asInvokeMethodWithReceiver();
          if (invoke.inValues().lastIndexOf(eligibleInstance) > 0) {
            continue nextNewInstance; // Instance must only be passes as a receiver.
          }

          DexEncodedMethod singleTarget =
              findSingleTarget(appInfo, invoke, eligibleClass);
          if (singleTarget == null) {
            continue nextNewInstance;
          }
          if (isProcessedConcurrently.test(singleTarget)) {
            continue nextNewInstance;
          }
          if (method == singleTarget) {
            continue nextNewInstance; // Don't inline itself.
          }

          if (!singleTarget.getOptimizationInfo()
              .isReceiverOnlyUsedForReadingFields(mappings.keySet())) {
            continue nextNewInstance; // Target must be trivial.
          }

          if (!singleTarget.isInliningCandidate(method, Reason.SIMPLE, appInfo)) {
            // We won't be able to inline it here.

            // Note that there may be some false negatives here since the method may
            // reference private fields of its class which are supposed to be replaced
            // with arguments after inlining. We should try and improve it later.

            // Using -allowaccessmodification mitigates this.
            continue nextNewInstance;
          }

          methodCalls.put(invoke, new InliningInfo(singleTarget, eligibleClass));
          continue /* next user */;
        }

        continue nextNewInstance; // Unsupported user.
      }

      // Force-inline of method invocation if any.
      inlineAllCalls(inliner, methodCalls);
      assert assertOnlyConstructorAndFieldReads(eligibleInstance, eligibleInitCall, mappings);

      // Replace all field reads with arguments passed to the constructor.
      patchFieldReads(eligibleInstance, eligibleInitCall, mappings);
      assert assertOnlyConstructor(eligibleInstance, eligibleInitCall);

      // Remove constructor call and new-instance instructions.
      removeInstruction(eligibleInitCall);
      removeInstruction(newInstance);
      code.removeAllTrivialPhis();
    }
  }

  private void inlineAllCalls(InlinerAction inliner,
      Map<InvokeMethodWithReceiver, InliningInfo> methodCalls) throws ApiLevelException {
    if (!methodCalls.isEmpty()) {
      inliner.inline(methodCalls);
    }
  }

  private void patchFieldReads(
      Value instance, InvokeDirect invokeMethod, Map<DexField, Integer> mappings) {
    for (Instruction user : instance.uniqueUsers()) {
      if (!user.isInstanceGet()) {
        continue;
      }
      InstanceGet fieldRead = user.asInstanceGet();

      // Replace the field read with
      assert mappings.containsKey(fieldRead.getField());
      Value arg = invokeMethod.inValues().get(1 + mappings.get(fieldRead.getField()));
      assert arg != null;
      Value value = fieldRead.outValue();
      if (value != null) {
        value.replaceUsers(arg);
        assert value.numberOfAllUsers() == 0;
      }

      // Remove instruction.
      removeInstruction(fieldRead);
    }
  }

  private void removeInstruction(Instruction instruction) {
    instruction.inValues().forEach(v -> v.removeUser(instruction));
    instruction.getBlock().removeInstruction(instruction);
  }

  private boolean assertOnlyConstructorAndFieldReads(
      Value instance, InvokeDirect invokeMethod, Map<DexField, Integer> mappings) {
    for (Instruction user : instance.uniqueUsers()) {
      if (user != invokeMethod &&
          !(user.isInstanceGet() && mappings.containsKey(user.asFieldInstruction().getField()))) {
        throw new Unreachable("Not all calls are inlined!");
      }
    }
    return true;
  }

  private boolean assertOnlyConstructor(Value instance, InvokeDirect invokeMethod) {
    for (Instruction user : instance.uniqueUsers()) {
      if (user != invokeMethod) {
        throw new Unreachable("Not all field reads are substituted!");
      }
    }
    return true;
  }

  private DexEncodedMethod findSingleTarget(
      AppInfo appInfo, InvokeMethodWithReceiver invoke, DexType instanceType) {

    // We don't use computeSingleTarget(...) on invoke since it sometimes fails to
    // find the single target, while this code may be more successful since we exactly
    // know what is the actual type of the receiver.

    // Note that we also intentionally limit ourselves to methods directly defined in
    // the instance's class. This may be improved later.

    DexClass clazz = appInfo.definitionFor(instanceType);
    if (clazz != null) {
      DexMethod callee = invoke.getInvokedMethod();
      for (DexEncodedMethod candidate : clazz.virtualMethods()) {
        if (candidate.method.name == callee.name && candidate.method.proto == callee.proto) {
          return candidate;
        }
      }
    }
    return null;
  }

  private Map<DexField, Integer> getConstructorFieldMappings(
      AppInfo appInfo, DexMethod init, Predicate<DexEncodedMethod> isProcessedConcurrently) {
    assert isClassEligible(appInfo, init.holder);

    DexEncodedMethod definition = appInfo.definitionFor(init);
    if (definition == null) {
      return NO_MAPPING;
    }

    if (isProcessedConcurrently.test(definition)) {
      return NO_MAPPING;
    }

    if (definition.accessFlags.isAbstract() || definition.accessFlags.isNative()) {
      return NO_MAPPING;
    }

    return definition.getOptimizationInfo().onlyInitializesFieldsWithNoOtherSideEffects();
  }

  private boolean isClassEligible(AppInfo appInfo, DexType clazz) {
    Boolean eligible = knownClasses.get(clazz);
    if (eligible == null) {
      Boolean computed = computeClassEligible(appInfo, clazz);
      Boolean existing = knownClasses.putIfAbsent(clazz, computed);
      assert existing == null || existing == computed;
      eligible = existing == null ? computed : existing;
    }
    return eligible;
  }

  // Class is eligible for this optimization. Eligibility implementation:
  //   - not an abstract or interface
  //   - directly extends java.lang.Object
  //   - does not declare finalizer
  //   - does not trigger any static initializers
  private boolean computeClassEligible(AppInfo appInfo, DexType clazz) {
    DexClass definition = appInfo.definitionFor(clazz);
    if (definition == null || definition.isLibraryClass() ||
        definition.accessFlags.isAbstract() || definition.accessFlags.isInterface()) {
      return false;
    }

    // Must directly extend Object.
    if (definition.superType != factory.objectType) {
      return false;
    }

    // Class must not define finalizer.
    for (DexEncodedMethod method : definition.virtualMethods()) {
      if (method.method.name == factory.finalizeMethodName &&
          method.method.proto == factory.objectMethods.finalize.proto) {
        return false;
      }
    }

    // Check for static initializers in this class or any of interfaces it implements.
    return !appInfo.canTriggerStaticInitializer(clazz);
  }
}
