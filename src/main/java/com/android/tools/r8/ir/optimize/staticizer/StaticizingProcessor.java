// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

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
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.Outliner;
import com.android.tools.r8.ir.optimize.staticizer.ClassStaticizer.CandidateInfo;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class StaticizingProcessor {
  private final ClassStaticizer classStaticizer;

  private final Set<DexEncodedMethod> referencingExtraMethods = Sets.newIdentityHashSet();
  private final Map<DexEncodedMethod, CandidateInfo> hostClassInits = new IdentityHashMap<>();
  private final Set<DexEncodedMethod> methodsToBeStaticized = Sets.newIdentityHashSet();
  private final Map<DexField, CandidateInfo> singletonFields = new IdentityHashMap<>();
  private final Map<DexType, DexType> candidateToHostMapping = new IdentityHashMap<>();

  StaticizingProcessor(ClassStaticizer classStaticizer) {
    this.classStaticizer = classStaticizer;
  }

  final void run(OptimizationFeedback feedback) {
    // Filter out candidates based on the information we collected
    // while examining methods.
    finalEligibilityCheck();

    // Prepare interim data.
    prepareCandidates();

    // Process all host class initializers (only remove instantiations).
    processMethods(hostClassInits.keySet().stream(), this::removeCandidateInstantiation, feedback);

    // Process instance methods to be staticized (only remove references to 'this').
    processMethods(methodsToBeStaticized.stream(), this::removeReferencesToThis, feedback);

    // Convert instance methods into static methods with an extra parameter.
    Set<DexEncodedMethod> staticizedMethods = staticizeMethodSymbols();

    // Process all other methods that may reference singleton fields
    // and call methods on them. (Note that we exclude the former instance methods,
    // but include new static methods created as a result of staticizing.
    Stream<DexEncodedMethod> methods = Streams.concat(
        referencingExtraMethods.stream(),
        staticizedMethods.stream(),
        hostClassInits.keySet().stream());
    processMethods(methods, this::rewriteReferences, feedback);
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

  private void processMethods(Stream<DexEncodedMethod> methods,
      BiConsumer<DexEncodedMethod, IRCode> strategy, OptimizationFeedback feedback) {
    classStaticizer.setFixupStrategy(strategy);
    methods.sorted(DexEncodedMethod::slowCompare).forEach(
        method -> classStaticizer.converter.processMethod(method, feedback,
            x -> false, CallSiteInformation.empty(), Outliner::noProcessing));
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
    fixupStaticizedValueUsers(code, code.getThis());
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
      CandidateInfo candidateInfo = singletonFields.get(read.getField());
      assert candidateInfo != null;
      Value value = read.dest();
      if (value != null) {
        fixupStaticizedValueUsers(code, value);
      }
      if (!candidateInfo.preserveRead.get()) {
        read.removeOrReplaceByDebugLocalRead();
      }
    });

    if (!candidateToHostMapping.isEmpty()) {
      remapMovedCandidates(code);
    }
  }

  // Fixup value usages: rewrites all method calls so that they point to static methods.
  private void fixupStaticizedValueUsers(IRCode code, Value thisValue) {
    assert thisValue != null;
    assert thisValue.numberOfPhiUsers() == 0;

    for (Instruction user : thisValue.uniqueUsers()) {
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

    assert thisValue.numberOfUsers() == 0;
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
                  MemberType.fromDexType(field.type),
                  code.createValue(
                      TypeLatticeElement.fromDexType(field.type, true, classStaticizer.appInfo),
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
          it.replaceCurrentInstruction(
              new StaticPut(MemberType.fromDexType(field.type), staticPut.inValue(), field)
          );
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
                      method.proto.returnType, true, classStaticizer.appInfo),
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
