// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.Outliner;
import com.android.tools.r8.ir.optimize.staticizer.ClassStaticizer.CandidateInfo;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class StaticizingProcessor {

  private final ClassStaticizer classStaticizer;
  private final ExecutorService executorService;

  private final Set<DexEncodedMethod> referencingExtraMethods = Sets.newIdentityHashSet();
  private final Map<DexEncodedMethod, CandidateInfo> hostClassInits = new IdentityHashMap<>();
  private final Set<DexEncodedMethod> methodsToBeStaticized = Sets.newIdentityHashSet();
  private final Map<DexField, CandidateInfo> singletonFields = new IdentityHashMap<>();
  private final Map<DexType, DexType> candidateToHostMapping = new IdentityHashMap<>();

  StaticizingProcessor(ClassStaticizer classStaticizer, ExecutorService executorService) {
    this.classStaticizer = classStaticizer;
    this.executorService = executorService;
  }

  /** @return the set of methods that have been reprocessed as a result of staticizing. */
  final Set<DexEncodedMethod> run(OptimizationFeedback feedback) throws ExecutionException {
    // Filter out candidates based on the information we collected while examining methods.
    finalEligibilityCheck();

    // Prepare interim data.
    prepareCandidates();

    // Process all host class initializers (only remove instantiations).
    processMethodsConcurrently(
        hostClassInits.keySet(), this::removeCandidateInstantiation, feedback);

    // Process instance methods to be staticized (only remove references to 'this').
    processMethodsConcurrently(methodsToBeStaticized, this::removeReferencesToThis, feedback);

    // Convert instance methods into static methods with an extra parameter.
    Set<DexEncodedMethod> methods = staticizeMethodSymbols();

    // Process all other methods that may reference singleton fields and call methods on them.
    // (Note that we exclude the former instance methods, but include new static methods created as
    // a result of staticizing.)
    methods.addAll(referencingExtraMethods);
    methods.addAll(hostClassInits.keySet());
    processMethodsConcurrently(methods, this::rewriteReferences, feedback);

    return methods;
  }

  private void finalEligibilityCheck() {
    Iterator<Entry<DexType, CandidateInfo>> it =
        classStaticizer.candidates.entrySet().iterator();
    while (it.hasNext()) {
      Entry<DexType, CandidateInfo> entry = it.next();
      DexType candidateType = entry.getKey();
      CandidateInfo info = entry.getValue();
      DexProgramClass candidateClass = info.candidate;
      DexType candidateHostType = info.hostType();
      DexEncodedMethod constructorUsed = info.constructor.get();

      int instancesCreated = info.instancesCreated.get();
      assert instancesCreated == info.fieldWrites.get();
      assert instancesCreated <= 1;
      assert (instancesCreated == 0) == (constructorUsed == null);

      // CHECK: One instance, one singleton field, known constructor
      if (instancesCreated == 0) {
        // Give up on the candidate, if there are any reads from instance
        // field the user should read null.
        it.remove();
        continue;
      }

      // CHECK: instance initializer used to create an instance is trivial.
      // NOTE: Along with requirement that candidate does not have instance
      // fields this should guarantee that the constructor is empty.
      assert candidateClass.instanceFields().length == 0;
      assert constructorUsed.isProcessed();
      TrivialInitializer trivialInitializer =
          constructorUsed.getOptimizationInfo().getTrivialInitializerInfo();
      if (trivialInitializer == null) {
        it.remove();
        continue;
      }

      // CHECK: class initializer should only be present if candidate itself is its own host.
      DexEncodedMethod classInitializer = candidateClass.getClassInitializer();
      assert classInitializer != null || candidateType != candidateHostType;
      if (classInitializer != null && candidateType != candidateHostType) {
        it.remove();
        continue;
      }

      // CHECK: no abstract or native instance methods.
      if (Streams.stream(candidateClass.methods()).anyMatch(
          method -> !method.isStatic() && (method.shouldNotHaveCode()))) {
        it.remove();
        continue;
      }
    }
  }

  private void prepareCandidates() {
    Set<DexEncodedMethod> removedInstanceMethods = Sets.newIdentityHashSet();

    for (CandidateInfo candidate : classStaticizer.candidates.values()) {
      DexProgramClass candidateClass = candidate.candidate;
      // Host class initializer
      DexClass hostClass = candidate.hostClass();
      DexEncodedMethod hostClassInitializer = hostClass.getClassInitializer();
      assert hostClassInitializer != null;
      CandidateInfo previous = hostClassInits.put(hostClassInitializer, candidate);
      assert previous == null;

      // Collect instance methods to be staticized.
      for (DexEncodedMethod method : candidateClass.methods()) {
        if (!method.isStatic()) {
          removedInstanceMethods.add(method);
          if (!factory().isConstructor(method.method)) {
            methodsToBeStaticized.add(method);
          }
        }
      }
      singletonFields.put(candidate.singletonField.field, candidate);
      referencingExtraMethods.addAll(candidate.referencedFrom);
    }

    referencingExtraMethods.removeAll(removedInstanceMethods);
  }

  /**
   * Processes the given methods concurrently using the given strategy.
   *
   * <p>Note that, when the strategy {@link #rewriteReferences(DexEncodedMethod, IRCode)} is being
   * applied, it is important that we never inline a method from `methods` which has still not been
   * reprocessed. This could lead to broken code, because the strategy that rewrites the broken
   * references is applied *before* inlining (because the broken references in the inlinee are never
   * rewritten). We currently avoid this situation by processing all the methods concurrently
   * (inlining of a method that is processed concurrently is not allowed).
   */
  private void processMethodsConcurrently(
      Set<DexEncodedMethod> methods,
      BiConsumer<DexEncodedMethod, IRCode> strategy,
      OptimizationFeedback feedback)
      throws ExecutionException {
    classStaticizer.setFixupStrategy(strategy);

    List<Future<?>> futures = new ArrayList<>();
    for (DexEncodedMethod method : methods) {
      futures.add(
          executorService.submit(
              () -> {
                classStaticizer.converter.processMethod(
                    method,
                    feedback,
                    methods::contains,
                    CallSiteInformation.empty(),
                    Outliner::noProcessing);
                return null; // we want a Callable not a Runnable to be able to throw
              }));
    }
    ThreadUtils.awaitFutures(futures);

    classStaticizer.cleanFixupStrategy();
  }

  private void removeCandidateInstantiation(DexEncodedMethod method, IRCode code) {
    CandidateInfo candidateInfo = hostClassInits.get(method);
    assert candidateInfo != null;

    // Find and remove instantiation and its users.
    InstructionIterator iterator = code.instructionIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (instruction.isNewInstance() &&
          instruction.asNewInstance().clazz == candidateInfo.candidate.type) {
        // Remove all usages
        // NOTE: requiring (a) the instance initializer to be trivial, (b) not allowing
        //       candidates with instance fields and (c) requiring candidate to directly
        //       extend java.lang.Object guarantees that the constructor is actually
        //       empty and does not need to be inlined.
        assert candidateInfo.candidate.superType == factory().objectType;
        assert candidateInfo.candidate.instanceFields().length == 0;

        Value singletonValue = instruction.outValue();
        assert singletonValue != null;
        singletonValue.uniqueUsers().forEach(Instruction::removeOrReplaceByDebugLocalRead);
        instruction.removeOrReplaceByDebugLocalRead();
        return;
      }
    }

    assert false : "Must always be able to find and remove the instantiation";
  }

  private void removeReferencesToThis(DexEncodedMethod method, IRCode code) {
    fixupStaticizedThisUsers(code, code.getThis());
  }

  private void rewriteReferences(DexEncodedMethod method, IRCode code) {
    // Process all singleton field reads and rewrite their users.
    List<StaticGet> singletonFieldReads =
        Streams.stream(code.instructionIterator())
            .filter(Instruction::isStaticGet)
            .map(Instruction::asStaticGet)
            .filter(get -> singletonFields.containsKey(get.getField()))
            .collect(Collectors.toList());

    singletonFieldReads.forEach(read -> {
      DexField field = read.getField();
      CandidateInfo candidateInfo = singletonFields.get(field);
      assert candidateInfo != null;
      Value value = read.dest();
      if (value != null) {
        fixupStaticizedFieldReadUsers(code, value, field);
      }
      if (!candidateInfo.preserveRead.get()) {
        read.removeOrReplaceByDebugLocalRead();
      }
    });

    if (!candidateToHostMapping.isEmpty()) {
      remapMovedCandidates(code);
    }
  }

  // Fixup `this` usages: rewrites all method calls so that they point to static methods.
  private void fixupStaticizedThisUsers(IRCode code, Value thisValue) {
    assert thisValue != null;
    assert thisValue.numberOfPhiUsers() == 0;

    fixupStaticizedValueUsers(code, thisValue.uniqueUsers());

    assert thisValue.numberOfUsers() == 0;
  }

  // Re-processing finalized code may create slightly different IR code than what the examining
  // phase has seen. For example,
  //
  //  b1:
  //    s1 <- static-get singleton
  //    ...
  //    invoke-virtual { s1, ... } mtd1
  //    goto Exit
  //  b2:
  //    s2 <- static-get singleoton
  //    ...
  //    invoke-virtual { s2, ... } mtd1
  //    goto Exit
  //  ...
  //  Exit: ...
  //
  // ~>
  //
  //  b1:
  //    s1 <- static-get singleton
  //    ...
  //    goto Exit
  //  b2:
  //    s2 <- static-get singleton
  //    ...
  //    goto Exit
  //  Exit:
  //    sp <- phi(s1, s2)
  //    invoke-virtual { sp, ... } mtd1
  //    ...
  //
  // From staticizer's viewpoint, `sp` is trivial in the sense that it is composed of values that
  // refer to the same singleton field. If so, we can safely relax the assertion; remove uses of
  // field reads; remove quasi-trivial phis; and then remove original field reads.
  private boolean testAndcollectPhisComposedOfSameFieldRead(
      Set<Phi> phisToCheck, DexField field, Set<Phi> trivialPhis) {
    for (Phi phi : phisToCheck) {
      Set<Phi> chainedPhis = Sets.newIdentityHashSet();
      for (Value operand : phi.getOperands()) {
        if (operand.isPhi()) {
          chainedPhis.add(operand.asPhi());
        } else {
          if (!operand.definition.isStaticGet()) {
            return false;
          }
          if (operand.definition.asStaticGet().getField() != field) {
            return false;
          }
        }
      }
      if (!chainedPhis.isEmpty()) {
        if (!testAndcollectPhisComposedOfSameFieldRead(chainedPhis, field, trivialPhis)) {
          return false;
        }
      }
      trivialPhis.add(phi);
    }
    return true;
  }

  // Fixup field read usages. Same as {@link #fixupStaticizedThisUsers} except this one is handling
  // quasi-trivial phis that might be introduced while re-processing finalized code.
  private void fixupStaticizedFieldReadUsers(IRCode code, Value dest, DexField field) {
    assert dest != null;
    // During the examine phase, field reads with any phi users have been invalidated, hence zero.
    // However, it may be not true if re-processing introduces phis after optimizing common suffix.
    Set<Phi> trivialPhis = Sets.newIdentityHashSet();
    boolean hasTrivialPhis =
        testAndcollectPhisComposedOfSameFieldRead(dest.uniquePhiUsers(), field, trivialPhis);
    assert dest.numberOfPhiUsers() == 0 || hasTrivialPhis;
    Set<Instruction> users = new HashSet<>(dest.uniqueUsers());
    // If that is the case, method calls we want to fix up include users of those phis.
    if (hasTrivialPhis) {
      for (Phi phi : trivialPhis) {
        users.addAll(phi.uniqueUsers());
      }
    }

    fixupStaticizedValueUsers(code, users);

    if (hasTrivialPhis) {
      // We can't directly use Phi#removeTrivialPhi because they still refer to different operands.
      for (Phi phi : trivialPhis) {
        // First, make sure phi users are indeed uses of field reads and removed via fixup.
        assert phi.numberOfUsers() == 0;
        // Then, manually clean up this from all of the operands.
        for (Value operand : phi.getOperands()) {
          operand.removePhiUser(phi);
        }
        // And remove it from the containing block.
        phi.getBlock().removePhi(phi);
      }
    }

    // No matter what, number of phi users should be zero too.
    assert dest.numberOfUsers() == 0 && dest.numberOfPhiUsers() == 0;
  }

  private void fixupStaticizedValueUsers(IRCode code, Set<Instruction> users) {
    for (Instruction user : users) {
      assert user.isInvokeVirtual() || user.isInvokeDirect();
      InvokeMethodWithReceiver invoke = user.asInvokeMethodWithReceiver();
      Value newValue = null;
      Value outValue = invoke.outValue();
      if (outValue != null) {
        newValue = code.createValue(outValue.getTypeLattice());
        DebugLocalInfo localInfo = outValue.getLocalInfo();
        if (localInfo != null) {
          newValue.setLocalInfo(localInfo);
        }
      }
      List<Value> args = invoke.inValues();
      invoke.replace(new InvokeStatic(
          invoke.getInvokedMethod(), newValue, args.subList(1, args.size())));
    }
  }

  private void remapMovedCandidates(IRCode code) {
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();

      if (instruction.isStaticGet()) {
        StaticGet staticGet = instruction.asStaticGet();
        DexField field = mapFieldIfMoved(staticGet.getField());
        if (field != staticGet.getField()) {
          Value outValue = staticGet.dest();
          assert outValue != null;
          it.replaceCurrentInstruction(
              new StaticGet(
                  code.createValue(
                      TypeLatticeElement.fromDexType(
                          field.type, maybeNull(), classStaticizer.appInfo),
                      outValue.getLocalInfo()),
                  field
              )
          );
        }
        continue;
      }

      if (instruction.isStaticPut()) {
        StaticPut staticPut = instruction.asStaticPut();
        DexField field = mapFieldIfMoved(staticPut.getField());
        if (field != staticPut.getField()) {
          it.replaceCurrentInstruction(new StaticPut(staticPut.inValue(), field));
        }
        continue;
      }

      if (instruction.isInvokeStatic()) {
        InvokeStatic invoke = instruction.asInvokeStatic();
        DexMethod method = invoke.getInvokedMethod();
        DexType hostType = candidateToHostMapping.get(method.holder);
        if (hostType != null) {
          DexMethod newMethod = factory().createMethod(hostType, method.proto, method.name);
          Value outValue = invoke.outValue();
          Value newOutValue = method.proto.returnType.isVoidType() ? null
              : code.createValue(
                  TypeLatticeElement.fromDexType(
                      method.proto.returnType, maybeNull(), classStaticizer.appInfo),
                  outValue == null ? null : outValue.getLocalInfo());
          it.replaceCurrentInstruction(
              new InvokeStatic(newMethod, newOutValue, invoke.inValues()));
        }
        continue;
      }
    }
  }

  private DexField mapFieldIfMoved(DexField field) {
    DexType hostType = candidateToHostMapping.get(field.clazz);
    if (hostType != null) {
      field = factory().createField(hostType, field.type, field.name);
    }
    hostType = candidateToHostMapping.get(field.type);
    if (hostType != null) {
      field = factory().createField(field.clazz, hostType, field.name);
    }
    return field;
  }

  private Set<DexEncodedMethod> staticizeMethodSymbols() {
    BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();
    Map<DexEncodedMethod, DexEncodedMethod> encodedMethodMapping = new HashMap<>();
    BiMap<DexField, DexField> fieldMapping = HashBiMap.create();

    Set<DexEncodedMethod> staticizedMethods = Sets.newIdentityHashSet();
    for (CandidateInfo candidate : classStaticizer.candidates.values()) {
      DexProgramClass candidateClass = candidate.candidate;

      // Move instance methods into static ones.
      List<DexEncodedMethod> newDirectMethods = new ArrayList<>();
      for (DexEncodedMethod method : candidateClass.methods()) {
        if (method.isStatic()) {
          newDirectMethods.add(method);
        } else if (!factory().isConstructor(method.method)) {
          DexEncodedMethod staticizedMethod = method.toStaticMethodWithoutThis();
          newDirectMethods.add(staticizedMethod);
          staticizedMethods.add(staticizedMethod);
          methodMapping.put(method.method, staticizedMethod.method);
          encodedMethodMapping.put(method, staticizedMethod);
        }
      }
      candidateClass.setVirtualMethods(DexEncodedMethod.EMPTY_ARRAY);
      candidateClass.setDirectMethods(
          newDirectMethods.toArray(new DexEncodedMethod[newDirectMethods.size()]));

      // Consider moving static members from candidate into host.
      DexType hostType = candidate.hostType();
      if (candidateClass.type != hostType) {
        DexClass hostClass = classStaticizer.appInfo.definitionFor(hostType);
        assert hostClass != null;
        if (!classMembersConflict(candidateClass, hostClass)) {
          // Move all members of the candidate class into host class.
          moveMembersIntoHost(staticizedMethods,
              candidateClass, hostType, hostClass, methodMapping, fieldMapping);
        }
      }
    }

    if (!methodMapping.isEmpty() || fieldMapping.isEmpty()) {
      classStaticizer.converter.appView.setGraphLense(
          new ClassStaticizerGraphLense(
              classStaticizer.converter.graphLense(),
              classStaticizer.factory,
              fieldMapping,
              methodMapping,
              encodedMethodMapping));
    }
    return staticizedMethods;
  }

  private boolean classMembersConflict(DexClass a, DexClass b) {
    assert Streams.stream(a.methods()).allMatch(DexEncodedMethod::isStatic);
    assert a.instanceFields().length == 0;
    return Stream.of(a.staticFields()).anyMatch(fld -> b.lookupField(fld.field) != null) ||
        Streams.stream(a.methods()).anyMatch(method -> b.lookupMethod(method.method) != null);
  }

  private void moveMembersIntoHost(Set<DexEncodedMethod> staticizedMethods,
      DexProgramClass candidateClass,
      DexType hostType, DexClass hostClass,
      BiMap<DexMethod, DexMethod> methodMapping,
      BiMap<DexField, DexField> fieldMapping) {
    candidateToHostMapping.put(candidateClass.type, hostType);

    // Process static fields.
    // Append fields first.
    if (candidateClass.staticFields().length > 0) {
      DexEncodedField[] oldFields = hostClass.staticFields();
      DexEncodedField[] extraFields = candidateClass.staticFields();
      DexEncodedField[] newFields = new DexEncodedField[oldFields.length + extraFields.length];
      System.arraycopy(oldFields, 0, newFields, 0, oldFields.length);
      System.arraycopy(extraFields, 0, newFields, oldFields.length, extraFields.length);
      hostClass.setStaticFields(newFields);
    }

    // Fixup field types.
    DexEncodedField[] staticFields = hostClass.staticFields();
    for (int i = 0; i < staticFields.length; i++) {
      DexEncodedField field = staticFields[i];
      DexField newField = mapCandidateField(field.field, candidateClass.type, hostType);
      if (newField != field.field) {
        staticFields[i] = field.toTypeSubstitutedField(newField);
        fieldMapping.put(field.field, newField);
      }
    }

    // Process static methods.
    if (candidateClass.directMethods().length > 0) {
      DexEncodedMethod[] oldMethods = hostClass.directMethods();
      DexEncodedMethod[] extraMethods = candidateClass.directMethods();
      DexEncodedMethod[] newMethods = new DexEncodedMethod[oldMethods.length + extraMethods.length];
      System.arraycopy(oldMethods, 0, newMethods, 0, oldMethods.length);
      for (int i = 0; i < extraMethods.length; i++) {
        DexEncodedMethod method = extraMethods[i];
        DexEncodedMethod newMethod = method.toTypeSubstitutedMethod(
            factory().createMethod(hostType, method.method.proto, method.method.name));
        newMethods[oldMethods.length + i] = newMethod;
        staticizedMethods.add(newMethod);
        staticizedMethods.remove(method);
        DexMethod originalMethod = methodMapping.inverse().get(method.method);
        if (originalMethod == null) {
          methodMapping.put(method.method, newMethod.method);
        } else {
          methodMapping.put(originalMethod, newMethod.method);
        }
      }
      hostClass.setDirectMethods(newMethods);
    }
  }

  private DexField mapCandidateField(DexField field, DexType candidateType, DexType hostType) {
    return field.clazz != candidateType && field.type != candidateType ? field
        : factory().createField(
            field.clazz == candidateType ? hostType : field.clazz,
            field.type == candidateType ? hostType : field.type,
            field.name);
  }

  private DexItemFactory factory() {
    return classStaticizer.factory;
  }
}
