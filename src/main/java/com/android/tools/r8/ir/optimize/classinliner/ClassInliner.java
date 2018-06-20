// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer.ClassTrivialInitializer;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.Inliner.InliningInfo;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.kotlin.KotlinInfo;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class ClassInliner {
  private final DexItemFactory factory;
  private final int totalMethodInstructionLimit;
  private final ConcurrentHashMap<DexType, Boolean> knownClasses = new ConcurrentHashMap<>();

  public interface InlinerAction {
    void inline(Map<InvokeMethodWithReceiver, InliningInfo> methods);
  }

  public ClassInliner(DexItemFactory factory, int totalMethodInstructionLimit) {
    this.factory = factory;
    this.totalMethodInstructionLimit = totalMethodInstructionLimit;
  }

  // Process method code and inline eligible class instantiations, in short:
  //
  // - collect all 'new-instance' and 'static-get' instructions (called roots below) in
  // the original code. Note that class inlining, if happens, mutates code and may add
  // new root instructions. Processing them as well is possible, but does not seem to
  // bring much value.
  //
  // - for each 'new-instance' root we check if it is eligible for inlining, i.e:
  //     -> the class of the new instance is 'eligible' (see computeClassEligible(...))
  //     -> the instance is initialized with 'eligible' constructor (see comments in
  //        CodeRewriter::identifyClassInlinerEligibility(...))
  //     -> has only 'eligible' uses, i.e:
  //          * as a receiver of a field read/write for a field defined in same class
  //            as method.
  //          * as a receiver of virtual or interface call with single target being
  //            an eligible method according to identifyClassInlinerEligibility(...);
  //            NOTE: if method receiver is used as a return value, the method call
  //            should ignore return value
  //
  // - for each 'static-get' root we check if it is eligible for inlining, i.e:
  //     -> the class of the new instance is 'eligible' (see computeClassEligible(...))
  //        *and* has a trivial class constructor (see CoreRewriter::computeClassInitializerInfo
  //        and description in isClassAndUsageEligible(...)) initializing the field root reads
  //     -> has only 'eligible' uses, (see above)
  //
  // - inline eligible root instructions, i.e:
  //     -> force inline methods called on the instance (including the initializer);
  //        (this may introduce additional instance field reads/writes on the receiver)
  //     -> replace instance field reads with appropriate values calculated based on
  //        fields writes
  //     -> remove the call to superclass initializer (if root is 'new-instance')
  //     -> remove all field writes
  //     -> remove root instructions
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
  //     static class F {
  //       final static F I = new F();
  //       int getX() {
  //         return 123;
  //       }
  //     }
  //     static int method1() {
  //       return new L(1).x;
  //     }
  //     static int method2() {
  //       return new L(1).getX();
  //     }
  //     static int method3() {
  //       return F.I.getX();
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
  //     static int method3() {
  //       return "F::getX";
  //     }
  //   }
  //
  public final void processMethodCode(
      AppInfoWithLiveness appInfo,
      DexEncodedMethod method,
      IRCode code,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      InlinerAction inliner) {

    // Collect all the new-instance and static-get instructions in the code before inlining.
    List<Instruction> roots = Streams.stream(code.instructionIterator())
        .filter(insn -> insn.isNewInstance() || insn.isStaticGet())
        .collect(Collectors.toList());

    for (Instruction root : roots) {
      Value eligibleInstance = root.outValue();
      if (eligibleInstance == null) {
        continue;
      }

      DexType eligibleClass = root.isNewInstance()
          ? root.asNewInstance().clazz : root.asStaticGet().getField().type;
      DexClass eligibleClassDefinition = appInfo.definitionFor(eligibleClass);
      if (eligibleClassDefinition == null) {
        continue;
      }

      // Check the static initializer of the type.
      if (!isClassAndUsageEligible(root, eligibleClass,
          eligibleClassDefinition, isProcessedConcurrently, appInfo)) {
        continue;
      }

      Map<InvokeMethodWithReceiver, InliningInfo> methodCalls =
          checkInstanceUsersEligibility(appInfo, method, isProcessedConcurrently,
              root, eligibleInstance, eligibleClass, eligibleClassDefinition);
      if (methodCalls == null) {
        continue;
      }

      if (!instructionLimitExempt(eligibleClassDefinition, appInfo) &&
          getTotalEstimatedMethodSize(methodCalls) >= totalMethodInstructionLimit) {
        continue;
      }

      // Inline the class instance.
      forceInlineAllMethodInvocations(inliner, methodCalls);
      removeSuperClassInitializerAndFieldReads(code, root, eligibleInstance);
      removeFieldWrites(eligibleInstance, eligibleClass);
      removeInstruction(root);

      // Restore normality.
      code.removeAllTrivialPhis();
      assert code.isConsistentSSA();
    }
  }

  private boolean isClassAndUsageEligible(Instruction root,
      DexType eligibleClass, DexClass eligibleClassDefinition,
      Predicate<DexEncodedMethod> isProcessedConcurrently, AppInfoWithLiveness appInfo) {
    if (!isClassEligible(appInfo, eligibleClass)) {
      return false;
    }

    if (root.isNewInstance()) {
      // There must be no static initializer on the class itself.
      return !eligibleClassDefinition.hasClassInitializer();
    }

    assert root.isStaticGet();

    // Checking if we can safely inline class implemented following singleton-like
    // pattern, by which we assume a static final field holding on to the reference
    // initialized in class constructor.
    //
    // In general we are targeting cases when the class is defined as:
    //
    //   class X {
    //     static final X F;
    //     static {
    //       F = new X();
    //     }
    //   }
    //
    // and being used as follows:
    //
    //   void foo() {
    //     f = X.F;
    //     f.bar();
    //   }
    //
    // The main difference from the similar case of class inliner with 'new-instance'
    // instruction is that in this case the instance we inline is not just leaked, but
    // is actually published via X.F field. There are several risks we need to address
    // in this case:
    //
    //    Risk: instance stored in field X.F has changed after it was initialized in
    //      class initializer
    //    Solution: we assume that final field X.F is not modified outside the class
    //      initializer. In rare cases when it is (e.g. via reflections) it should
    //      be marked with keep rules
    //
    //    Risk: instance stored in field X.F is not initialized yet
    //    Solution: not initialized instance can only be visible if X.<clinit>
    //      triggers other class initialization which references X.F. This
    //      situation should never happen if we:
    //        -- don't allow any superclasses to have static initializer,
    //        -- don't allow any subclasses,
    //        -- guarantee the class has trivial class initializer
    //           (see CodeRewriter::computeClassInitializerInfo), and
    //        -- guarantee the instance is initialized with trivial instance
    //           initializer (see CodeRewriter::computeInstanceInitializerInfo)
    //
    //    Risk: instance stored in field X.F was mutated
    //    Solution: we require that class X does not have any instance fields, and
    //      if any of its superclasses has instance fields, accessing them will make
    //      this instance not eligible for inlining. I.e. even though the instance is
    //      publicized and its state has been mutated, it will not effect the logic
    //      of class inlining
    //

    if (eligibleClassDefinition.instanceFields().length > 0 ||
        !eligibleClassDefinition.accessFlags.isFinal()) {
      return false;
    }

    // Singleton instance must be initialized in class constructor.
    DexEncodedMethod classInitializer = eligibleClassDefinition.getClassInitializer();
    if (classInitializer == null || isProcessedConcurrently.test(classInitializer)) {
      return false;
    }

    TrivialInitializer info =
        classInitializer.getOptimizationInfo().getTrivialInitializerInfo();
    assert info == null || info instanceof ClassTrivialInitializer;
    DexField instanceField = root.asStaticGet().getField();
    // Singleton instance field must NOT be pinned.
    return info != null &&
        ((ClassTrivialInitializer) info).field == instanceField &&
        !appInfo.isPinned(eligibleClassDefinition.lookupStaticField(instanceField).field);
  }

  private Map<InvokeMethodWithReceiver, InliningInfo> checkInstanceUsersEligibility(
      AppInfoWithSubtyping appInfo, DexEncodedMethod method,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      Instruction root, Value receiver,
      DexType eligibleClass, DexClass eligibleClassDefinition) {

    // No Phi users.
    if (receiver.numberOfPhiUsers() > 0) {
      return null; // Not eligible.
    }

    Map<InvokeMethodWithReceiver, InliningInfo> methodCalls = new IdentityHashMap<>();

    for (Instruction user : receiver.uniqueUsers()) {
      // Field read/write.
      if (user.isInstanceGet() ||
          (user.isInstancePut() && user.asInstancePut().value() != receiver)) {
        DexField field = user.asFieldInstruction().getField();
        if (field.clazz == eligibleClass &&
            eligibleClassDefinition.lookupInstanceField(field) != null) {
          // Since class inliner currently only supports classes directly extending
          // java.lang.Object, we don't need to worry about fields defined in superclasses.
          continue;
        }
        return null; // Not eligible.
      }

      // Eligible constructor call (for new instance roots only).
      if (user.isInvokeDirect() && root.isNewInstance()) {
        InliningInfo inliningInfo = isEligibleConstructorCall(appInfo, method,
            user.asInvokeDirect(), receiver, eligibleClass, isProcessedConcurrently);
        if (inliningInfo != null) {
          methodCalls.put(user.asInvokeDirect(), inliningInfo);
          continue;
        }
        return null; // Not eligible.
      }

      // Eligible virtual method call.
      if (user.isInvokeVirtual() || user.isInvokeInterface()) {
        InliningInfo inliningInfo = isEligibleMethodCall(
            appInfo, method, user.asInvokeMethodWithReceiver(),
            receiver, eligibleClass, isProcessedConcurrently);
        if (inliningInfo != null) {
          methodCalls.put(user.asInvokeMethodWithReceiver(), inliningInfo);
          continue;
        }
        return null;  // Not eligible.
      }

      return null;  // Not eligible.
    }
    return methodCalls;
  }

  // Remove call to superclass initializer, replace field reads with appropriate
  // values, insert phis when needed.
  private void removeSuperClassInitializerAndFieldReads(
      IRCode code, Instruction root, Value eligibleInstance) {
    Map<DexField, FieldValueHelper> fieldHelpers = new IdentityHashMap<>();
    for (Instruction user : eligibleInstance.uniqueUsers()) {
      // Remove the call to superclass constructor.
      if (root.isNewInstance() &&
          user.isInvokeDirect() &&
          user.asInvokeDirect().getInvokedMethod() == factory.objectMethods.constructor) {
        removeInstruction(user);
        continue;
      }

      if (user.isInstanceGet()) {
        // Replace a field read with appropriate value.
        replaceFieldRead(code, root, user.asInstanceGet(), fieldHelpers);
        continue;
      }

      if (user.isInstancePut()) {
        // Skip in this iteration since these instructions are needed to
        // properly calculate what value should field reads be replaced with.
        continue;
      }

      throw new Unreachable("Unexpected usage left after method inlining: " + user);
    }
  }

  private void removeFieldWrites(Value receiver, DexType clazz) {
    for (Instruction user : receiver.uniqueUsers()) {
      if (!user.isInstancePut()) {
        throw new Unreachable("Unexpected usage left after field reads removed: " + user);
      }
      if (user.asInstancePut().getField().clazz != clazz) {
        throw new Unreachable("Unexpected field write left after field reads removed: " + user);
      }
      removeInstruction(user);
    }
  }

  private int getTotalEstimatedMethodSize(Map<InvokeMethodWithReceiver, InliningInfo> methodCalls) {
    int totalSize = 0;
    for (InliningInfo info : methodCalls.values()) {
      totalSize += info.target.getCode().estimatedSizeForInlining();
    }
    return totalSize;
  }

  private boolean instructionLimitExempt(
      DexClass eligibleClassDefinition, AppInfoWithLiveness appInfo) {
    if (appInfo.isPinned(eligibleClassDefinition.type)) {
      return false;
    }
    KotlinInfo kotlinInfo = eligibleClassDefinition.getKotlinInfo();
    return kotlinInfo != null &&
        kotlinInfo.isSyntheticClass() &&
        kotlinInfo.asSyntheticClass().isLambda();
  }

  private void replaceFieldRead(IRCode code, Instruction root,
      InstanceGet fieldRead, Map<DexField, FieldValueHelper> fieldHelpers) {

    Value value = fieldRead.outValue();
    if (value != null) {
      FieldValueHelper helper = fieldHelpers.computeIfAbsent(
          fieldRead.getField(), field -> new FieldValueHelper(field, code, root));
      Value newValue = helper.getValueForFieldRead(fieldRead.getBlock(), fieldRead);
      value.replaceUsers(newValue);
      for (FieldValueHelper fieldValueHelper : fieldHelpers.values()) {
        fieldValueHelper.replaceValue(value, newValue);
      }
      assert value.numberOfAllUsers() == 0;
    }
    removeInstruction(fieldRead);
  }

  // Describes and caches what values are supposed to be used instead of field reads.
  private static final class FieldValueHelper {
    final DexField field;
    final IRCode code;
    final Instruction root;

    private Value defaultValue = null;
    private final Map<BasicBlock, Value> ins = new IdentityHashMap<>();
    private final Map<BasicBlock, Value> outs = new IdentityHashMap<>();

    private FieldValueHelper(DexField field, IRCode code, Instruction root) {
      this.field = field;
      this.code = code;
      this.root = root;
    }

    void replaceValue(Value oldValue, Value newValue) {
      for (Entry<BasicBlock, Value> entry : ins.entrySet()) {
        if (entry.getValue() == oldValue) {
          entry.setValue(newValue);
        }
      }
      for (Entry<BasicBlock, Value> entry : outs.entrySet()) {
        if (entry.getValue() == oldValue) {
          entry.setValue(newValue);
        }
      }
    }

    Value getValueForFieldRead(BasicBlock block, Instruction valueUser) {
      assert valueUser != null;
      Value value = getValueDefinedInTheBlock(block, valueUser);
      return value != null ? value : getOrCreateInValue(block);
    }

    private Value getOrCreateOutValue(BasicBlock block) {
      Value value = outs.get(block);
      if (value != null) {
        return value;
      }

      value = getValueDefinedInTheBlock(block, null);
      if (value == null) {
        // No value defined in the block.
        value = getOrCreateInValue(block);
      }

      assert value != null;
      outs.put(block, value);
      return value;
    }

    private Value getOrCreateInValue(BasicBlock block) {
      Value value = ins.get(block);
      if (value != null) {
        return value;
      }

      List<BasicBlock> predecessors = block.getPredecessors();
      if (predecessors.size() == 1) {
        value = getOrCreateOutValue(predecessors.get(0));
        ins.put(block, value);
      } else {
        // Create phi, add it to the block, cache in ins map for future use.
        Phi phi = new Phi(code.valueNumberGenerator.next(),
            block, ValueType.fromDexType(field.type), null);
        ins.put(block, phi);

        List<Value> operands = new ArrayList<>();
        for (BasicBlock predecessor : block.getPredecessors()) {
          operands.add(getOrCreateOutValue(predecessor));
        }
        // Add phi, but don't remove trivial phis; since we cache the phi
        // we just created for future use we should delay removing trivial
        // phis until we are done with replacing fields reads.
        phi.addOperands(operands, false);
        value = phi;
      }

      assert value != null;
      return value;
    }

    private Value getValueDefinedInTheBlock(BasicBlock block, Instruction stopAt) {
      InstructionListIterator iterator = stopAt == null ?
          block.listIterator(block.getInstructions().size()) : block.listIterator(stopAt);

      Instruction valueProducingInsn = null;
      while (iterator.hasPrevious()) {
        Instruction instruction = iterator.previous();
        assert instruction != null;

        if (instruction == root ||
            (instruction.isInstancePut() &&
                instruction.asInstancePut().getField() == field &&
                instruction.asInstancePut().object() == root.outValue())) {
          valueProducingInsn = instruction;
          break;
        }
      }

      if (valueProducingInsn == null) {
        return null;
      }
      if (valueProducingInsn.isInstancePut()) {
        return valueProducingInsn.asInstancePut().value();
      }

      assert root == valueProducingInsn;
      if (defaultValue == null) {
        // If we met newInstance it means that default value is supposed to be used.
        defaultValue = code.createValue(ValueType.fromDexType(field.type));
        ConstNumber defaultValueInsn = new ConstNumber(defaultValue, 0);
        defaultValueInsn.setPosition(root.getPosition());
        LinkedList<Instruction> instructions = block.getInstructions();
        instructions.add(instructions.indexOf(root) + 1, defaultValueInsn);
        defaultValueInsn.setBlock(block);
      }
      return defaultValue;
    }
  }

  private void forceInlineAllMethodInvocations(
      InlinerAction inliner, Map<InvokeMethodWithReceiver, InliningInfo> methodCalls) {
    if (!methodCalls.isEmpty()) {
      inliner.inline(methodCalls);
    }
  }

  private void removeInstruction(Instruction instruction) {
    instruction.inValues().forEach(v -> v.removeUser(instruction));
    instruction.getBlock().removeInstruction(instruction);
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

  private InliningInfo isEligibleConstructorCall(
      AppInfoWithSubtyping appInfo,
      DexEncodedMethod method,
      InvokeDirect initInvoke,
      Value receiver,
      DexType inlinedClass,
      Predicate<DexEncodedMethod> isProcessedConcurrently) {

    // Must be a constructor of the exact same class.
    DexMethod init = initInvoke.getInvokedMethod();
    if (!factory.isConstructor(init)) {
      return null;
    }
    // Must be a constructor called on the receiver.
    if (initInvoke.inValues().lastIndexOf(receiver) != 0) {
      return null;
    }

    assert init.holder == inlinedClass
        : "Inlined constructor? [invoke: " + initInvoke +
        ", expected class: " + inlinedClass + "]";

    DexEncodedMethod definition = appInfo.definitionFor(init);
    if (definition == null || isProcessedConcurrently.test(definition)) {
      return null;
    }

    if (!definition.isInliningCandidate(method, Reason.SIMPLE, appInfo)) {
      // We won't be able to inline it here.

      // Note that there may be some false negatives here since the method may
      // reference private fields of its class which are supposed to be replaced
      // with arguments after inlining. We should try and improve it later.

      // Using -allowaccessmodification mitigates this.
      return null;
    }

    return definition.getOptimizationInfo().getClassInlinerEligibility() != null
        ? new InliningInfo(definition, inlinedClass) : null;
  }

  private InliningInfo isEligibleMethodCall(
      AppInfoWithSubtyping appInfo,
      DexEncodedMethod method,
      InvokeMethodWithReceiver invoke,
      Value receiver,
      DexType inlinedClass,
      Predicate<DexEncodedMethod> isProcessedConcurrently) {

    if (invoke.inValues().lastIndexOf(receiver) > 0) {
      return null; // Instance passed as an argument.
    }

    DexEncodedMethod singleTarget =
        findSingleTarget(appInfo, invoke, inlinedClass);
    if (singleTarget == null || isProcessedConcurrently.test(singleTarget)) {
      return null;
    }
    if (method == singleTarget) {
      return null; // Don't inline itself.
    }

    ClassInlinerEligibility eligibility =
        singleTarget.getOptimizationInfo().getClassInlinerEligibility();
    if (eligibility == null) {
      return null;
    }

    // If the method returns receiver and the return value is actually
    // used in the code the method is not eligible.
    if (eligibility.returnsReceiver &&
        invoke.outValue() != null && invoke.outValue().numberOfAllUsers() > 0) {
      return null;
    }

    if (!singleTarget.isInliningCandidate(method, Reason.SIMPLE, appInfo)) {
      // We won't be able to inline it here.

      // Note that there may be some false negatives here since the method may
      // reference private fields of its class which are supposed to be replaced
      // with arguments after inlining. We should try and improve it later.

      // Using -allowaccessmodification mitigates this.
      return null;
    }

    return new InliningInfo(singleTarget, inlinedClass);
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
  //   - is not an abstract class or interface
  //   - directly extends java.lang.Object
  //   - does not declare finalizer
  //   - does not trigger any static initializers except for its own
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
    return !appInfo.canTriggerStaticInitializer(clazz, true);
  }
}
