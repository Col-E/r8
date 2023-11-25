// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfStaticFieldWrite;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCodeDiagnostics;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.google.common.collect.Sets;
import java.util.Set;

class CfOpenClosedInterfacesAnalysisHelper {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final ProgramMethod method;
  private final InternalOptions options;

  private final Set<DexClass> openInterfaces = Sets.newIdentityHashSet();
  private final ProgramMethodMap<UnverifiableCfCodeDiagnostic> unverifiableCodeDiagnostics;

  CfOpenClosedInterfacesAnalysisHelper(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      ProgramMethodMap<UnverifiableCfCodeDiagnostic> unverifiableCodeDiagnostics) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.method = method;
    this.options = appView.options();
    this.unverifiableCodeDiagnostics = unverifiableCodeDiagnostics;
  }

  Set<DexClass> getOpenInterfaces() {
    return openInterfaces;
  }

  void processInstruction(CfInstruction instruction, CfFrameState state) {
    assert !state.isError();
    if (state.isBottom()) {
      // Unreachable.
      return;
    }
    assert state.isConcrete();
    ConcreteCfFrameState concreteState = state.asConcrete();
    if (instruction.isArrayStore()) {
      processArrayStore(instruction.asArrayStore(), concreteState);
    } else if (instruction.isInstanceFieldPut()) {
      processInstanceFieldPut(instruction.asInstanceFieldPut(), concreteState);
    } else if (instruction.isInvoke()) {
      processInvoke(instruction.asInvoke(), concreteState);
    } else if (instruction.isReturn() && !instruction.isReturnVoid()) {
      processReturn(concreteState);
    } else if (instruction.isStaticFieldPut()) {
      processStaticFieldPut(instruction.asStaticFieldPut(), concreteState);
    }
  }

  private void processArrayStore(CfArrayStore arrayStore, ConcreteCfFrameState state) {
    if (!arrayStore.getType().isObject()) {
      return;
    }
    state.peekStackElements(
        3,
        stack -> {
          FrameType array = stack.peekFirst();
          FrameType value = stack.peekLast();
          if (array.isInitializedNonNullReferenceType()) {
            ReferenceTypeElement arrayType =
                array.asInitializedNonNullReferenceType().getInitializedTypeWithInterfaces(appView);
            if (arrayType.isArrayType()) {
              processAssignment(value, arrayType.asArrayType().getMemberType());
            } else {
              assert false;
            }
          } else {
            assert array.isNullType();
          }
        },
        options);
  }

  private void processInstanceFieldPut(
      CfInstanceFieldWrite instanceFieldPut, ConcreteCfFrameState state) {
    state.peekStackElement(
        head -> processAssignment(head, instanceFieldPut.getField().getType()), options);
  }

  private void processInvoke(CfInvoke invoke, ConcreteCfFrameState state) {
    DexMethod invokedMethod = invoke.getMethod();
    state.peekStackElements(
        invokedMethod.getNumberOfArguments(invoke.isInvokeStatic()),
        arguments -> {
          int argumentIndex = 0;
          for (FrameType argument : arguments) {
            DexType parameter =
                invokedMethod.getArgumentType(argumentIndex, invoke.isInvokeStatic());
            processAssignment(argument, parameter);
            argumentIndex++;
          }
        },
        options);
  }

  private void processReturn(ConcreteCfFrameState state) {
    state.peekStackElement(head -> processAssignment(head, method.getReturnType()), options);
  }

  private void processStaticFieldPut(
      CfStaticFieldWrite staticFieldPut, ConcreteCfFrameState state) {
    state.peekStackElement(
        head -> processAssignment(head, staticFieldPut.getField().getType()), options);
  }

  private void processAssignment(FrameType fromType, DexType toType) {
    if (fromType.isInitializedNonNullReferenceType()) {
      processAssignment(
          fromType.asInitializedNonNullReferenceType().getInitializedTypeWithInterfaces(appView),
          toType);
    }
  }

  private void processAssignment(FrameType fromType, TypeElement toType) {
    if (fromType.isInitializedNonNullReferenceType()) {
      processAssignment(
          fromType.asInitializedNonNullReferenceType().getInitializedTypeWithInterfaces(appView),
          toType);
    }
  }

  private void processAssignment(ReferenceTypeElement fromType, DexType toType) {
    processAssignment(fromType, toType.toTypeElement(appView));
  }

  @SuppressWarnings("ReferenceEquality")
  private void processAssignment(TypeElement fromType, TypeElement toType) {
    // If the type is an interface type, then check that the assigned value is a subtype of the
    // interface type, or mark the interface as open.
    if (!toType.isClassType()) {
      return;
    }
    ClassTypeElement toClassType = toType.asClassType();
    if (toClassType.getClassType() != dexItemFactory.objectType) {
      return;
    }
    InterfaceCollection interfaceCollection = toClassType.getInterfaces();
    interfaceCollection.forEachKnownInterface(
        knownInterfaceType -> {
          DexClass knownInterface = appView.definitionFor(knownInterfaceType);
          if (knownInterface == null) {
            return;
          }
          assert knownInterface.isInterface();
          if (fromType.lessThanOrEqualUpToNullability(toType, appView)) {
            return;
          }
          assert verifyOpenInterfaceWitnessIsSuppressed(fromType, knownInterface);
          openInterfaces.add(knownInterface);
        });
  }

  void registerUnverifiableCode(
      ProgramMethod method, int instructionIndex, ErroneousCfFrameState state) {
    if (options.getCfCodeAnalysisOptions().isUnverifiableCodeReportingEnabled()) {
      unverifiableCodeDiagnostics.put(
          method,
          new UnverifiableCfCodeDiagnostic(
              method.getMethodReference(),
              instructionIndex,
              state.getMessage(),
              method.getOrigin()));
    }
    openInterfaces.clear();
  }

  void registerUnverifiableCodeWithFrames(CfCodeDiagnostics diagnostics) {
    if (options.getCfCodeAnalysisOptions().isUnverifiableCodeReportingEnabled()) {
      unverifiableCodeDiagnostics.put(
          method,
          new UnverifiableCfCodeDiagnostic(
              method.getMethodReference(),
              -1,
              diagnostics.getDiagnosticMessage(),
              method.getOrigin()));
    }
    openInterfaces.clear();
  }

  private boolean verifyOpenInterfaceWitnessIsSuppressed(
      TypeElement valueType, DexClass openInterface) {
    assert options.getOpenClosedInterfacesOptions().isSuppressed(appView, valueType, openInterface)
        : "Unexpected open interface "
            + openInterface.getTypeName()
            + " (assignment: "
            + valueType
            + ")";
    return true;
  }
}
