// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import static com.android.tools.r8.ir.code.Opcodes.ARRAY_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.InterfaceCollection;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.FieldPut;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.interfaces.collection.NonEmptyOpenClosedInterfacesCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions.OpenClosedInterfacesOptions;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;

public class OpenClosedInterfacesAnalysisImpl extends OpenClosedInterfacesAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;

  private Set<DexClass> openInterfaces;

  public OpenClosedInterfacesAnalysisImpl(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public void analyze(ProgramMethod method, IRCode code) {
    if (openInterfaces == null) {
      return;
    }
    // Analyze each instruction that may assign to an interface type.
    for (Instruction instruction : code.instructions()) {
      switch (instruction.opcode()) {
        case ARRAY_PUT:
          analyzeArrayPut(instruction.asArrayPut());
          break;
        case INSTANCE_PUT:
        case STATIC_PUT:
          analyzeFieldPut(instruction.asFieldPut());
          break;
        case INVOKE_DIRECT:
        case INVOKE_INTERFACE:
        case INVOKE_STATIC:
        case INVOKE_SUPER:
        case INVOKE_VIRTUAL:
          analyzeInvokeMethod(instruction.asInvokeMethod());
          break;
        case RETURN:
          analyzeReturn(instruction.asReturn(), method);
          break;
        default:
          break;
      }
    }
  }

  private void analyzeArrayPut(ArrayPut arrayPut) {
    Value array = arrayPut.array();
    TypeElement arrayType = array.getType();
    if (!arrayType.isArrayType()) {
      return;
    }
    TypeElement valueType = arrayPut.value().getType();
    TypeElement arrayMemberType = arrayType.asArrayType().getMemberType();
    checkAssignment(valueType, arrayMemberType);
  }

  private void analyzeFieldPut(FieldPut fieldPut) {
    TypeElement valueType = fieldPut.value().getType();
    TypeElement fieldType = fieldPut.getField().getTypeElement(appView);
    checkAssignment(valueType, fieldType);
  }

  private void analyzeInvokeMethod(InvokeMethod invoke) {
    DexTypeList parameters = invoke.getInvokedMethod().getParameters();
    for (int parameterIndex = 0; parameterIndex < parameters.size(); parameterIndex++) {
      Value argument = invoke.getArgumentForParameter(parameterIndex);
      TypeElement argumentType = argument.getType();
      TypeElement parameterType = parameters.get(parameterIndex).toTypeElement(appView);
      checkAssignment(argumentType, parameterType);
    }
  }

  private void analyzeReturn(Return returnInstruction, ProgramMethod context) {
    if (returnInstruction.isReturnVoid()) {
      return;
    }
    TypeElement valueType = returnInstruction.returnValue().getType();
    TypeElement returnType = context.getReturnType().toTypeElement(appView);
    checkAssignment(valueType, returnType);
  }

  private void checkAssignment(TypeElement fromType, TypeElement toType) {
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

  @Override
  public void prepareForPrimaryOptimizationPass() {
    openInterfaces = Sets.newConcurrentHashSet();
  }

  @Override
  public void onPrimaryOptimizationPassComplete() {
    // If open interfaces are not allowed and there are one or more suppressions, we should find at
    // least one open interface.
    OpenClosedInterfacesOptions options = appView.options().getOpenClosedInterfacesOptions();
    assert options.isOpenInterfacesAllowed()
            || !options.hasSuppressions()
            || !openInterfaces.isEmpty()
        : "Expected to find at least one open interface";

    includeParentOpenInterfaces();
    appView.setOpenClosedInterfacesCollection(
        new NonEmptyOpenClosedInterfacesCollection(
            openInterfaces.stream()
                .map(DexClass::getType)
                .collect(
                    Collectors.toCollection(
                        () -> SetUtils.newIdentityHashSet(openInterfaces.size())))));
    openInterfaces = null;
  }

  private void includeParentOpenInterfaces() {
    // This includes all parent interfaces of each open interface in the set of open interfaces,
    // by using the open interfaces as the seen set.
    WorkList<DexClass> worklist = WorkList.newWorkList(openInterfaces);
    worklist.addAllIgnoringSeenSet(openInterfaces);
    while (worklist.hasNext()) {
      DexClass openInterface = worklist.next();
      for (DexType indirectOpenInterfaceType : openInterface.getInterfaces()) {
        DexClass indirectOpenInterfaceDefinition = appView.definitionFor(indirectOpenInterfaceType);
        if (indirectOpenInterfaceDefinition != null) {
          worklist.addIfNotSeen(indirectOpenInterfaceDefinition);
        }
      }
    }
  }

  private boolean verifyOpenInterfaceWitnessIsSuppressed(
      TypeElement valueType, DexClass openInterface) {
    OpenClosedInterfacesOptions options = appView.options().getOpenClosedInterfacesOptions();
    assert options.isSuppressed(appView, valueType, openInterface)
        : "Unexpected open interface "
            + openInterface.getTypeName()
            + " (assignment: "
            + valueType
            + ")";
    return true;
  }
}
