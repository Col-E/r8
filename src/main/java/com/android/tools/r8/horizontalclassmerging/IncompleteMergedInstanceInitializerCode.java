// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.cf.code.CfConstClass;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfSafeCheckCast;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.analysis.value.SingleConstValue;
import com.android.tools.r8.ir.analysis.value.SingleDexItemBasedStringValue;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.utils.IntBox;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;

/**
 * Similar to CfCode, but with a marker that makes it possible to recognize this is synthesized by
 * the horizontal class merger.
 */
public class IncompleteMergedInstanceInitializerCode extends IncompleteHorizontalClassMergerCode {

  private final DexField classIdField;
  private final int extraNulls;
  private final DexMethod originalMethodReference;
  private final DexMethod syntheticMethodReference;

  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre;
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost;

  private final DexMethod parentConstructor;
  private final List<InstanceFieldInitializationInfo> parentConstructorArguments;

  IncompleteMergedInstanceInitializerCode(
      DexField classIdField,
      int extraNulls,
      DexMethod originalMethodReference,
      DexMethod syntheticMethodReference,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost,
      DexMethod parentConstructor,
      List<InstanceFieldInitializationInfo> parentConstructorArguments) {
    this.classIdField = classIdField;
    this.extraNulls = extraNulls;
    this.originalMethodReference = originalMethodReference;
    this.syntheticMethodReference = syntheticMethodReference;
    this.instanceFieldAssignmentsPre = instanceFieldAssignmentsPre;
    this.instanceFieldAssignmentsPost = instanceFieldAssignmentsPost;
    this.parentConstructor = parentConstructor;
    this.parentConstructorArguments = parentConstructorArguments;
  }

  @Override
  public CfCode toCfCode(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      HorizontalClassMergerGraphLens lens) {
    int[] argumentToLocalIndex = new int[method.getDefinition().getNumberOfArguments()];
    int maxLocals = 0;
    for (int argumentIndex = 0; argumentIndex < argumentToLocalIndex.length; argumentIndex++) {
      argumentToLocalIndex[argumentIndex] = maxLocals;
      maxLocals += method.getArgumentType(argumentIndex).getRequiredRegisters();
    }

    IntBox maxStack = new IntBox();
    ImmutableList.Builder<CfInstruction> instructionBuilder = ImmutableList.builder();

    // Set position.
    Position callerPosition =
        SyntheticPosition.builder().setLine(0).setMethod(syntheticMethodReference).build();
    Position calleePosition =
        SyntheticPosition.builder()
            .setLine(0)
            .setMethod(originalMethodReference)
            .setCallerPosition(callerPosition)
            .build();
    CfPosition position = new CfPosition(new CfLabel(), calleePosition);
    instructionBuilder.add(position);
    instructionBuilder.add(position.getLabel());

    // Assign class id.
    if (classIdField != null) {
      int classIdLocalIndex = maxLocals - 1 - extraNulls;
      instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));
      instructionBuilder.add(new CfLoad(ValueType.INT, classIdLocalIndex));
      instructionBuilder.add(
          new CfInstanceFieldWrite(
              lens.getRenamedFieldSignature(classIdField, lens.getPrevious())));
      maxStack.set(2);
    }

    // Assign each field.
    addCfInstructionsForInstanceFieldAssignments(
        appView,
        method,
        instructionBuilder,
        instanceFieldAssignmentsPre,
        argumentToLocalIndex,
        maxStack,
        lens);

    // Load receiver for parent constructor call.
    int stackHeightForParentConstructorCall = 1;
    instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));

    // Load constructor arguments.
    MethodLookupResult parentConstructorLookup = lens.lookupInvokeDirect(parentConstructor, method);

    int i = 0;
    for (InstanceFieldInitializationInfo initializationInfo : parentConstructorArguments) {
      stackHeightForParentConstructorCall +=
          addCfInstructionsForInitializationInfo(
              instructionBuilder,
              initializationInfo,
              argumentToLocalIndex,
              parentConstructorLookup.getReference().getParameter(i));
      i++;
    }

    for (ExtraParameter extraParameter :
        parentConstructorLookup.getPrototypeChanges().getExtraParameters()) {
      stackHeightForParentConstructorCall +=
          addCfInstructionsForInitializationInfo(
              instructionBuilder,
              extraParameter.getValue(appView),
              argumentToLocalIndex,
              parentConstructorLookup.getReference().getParameter(i));
      i++;
    }

    assert i == parentConstructorLookup.getReference().getParameters().size();

    // Invoke parent constructor.
    instructionBuilder.add(
        new CfInvoke(Opcodes.INVOKESPECIAL, parentConstructorLookup.getReference(), false));
    maxStack.setMax(stackHeightForParentConstructorCall);

    // Assign each field.
    addCfInstructionsForInstanceFieldAssignments(
        appView,
        method,
        instructionBuilder,
        instanceFieldAssignmentsPost,
        argumentToLocalIndex,
        maxStack,
        lens);

    // Return.
    instructionBuilder.add(new CfReturnVoid());

    return new CfCode(
        originalMethodReference.getHolderType(),
        maxStack.get(),
        maxLocals,
        instructionBuilder.build()) {

      @Override
      public GraphLens getCodeLens(AppView<?> appView) {
        return lens;
      }
    };
  }

  private static void addCfInstructionsForInstanceFieldAssignments(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod method,
      ImmutableList.Builder<CfInstruction> instructionBuilder,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignments,
      int[] argumentToLocalIndex,
      IntBox maxStack,
      HorizontalClassMergerGraphLens lens) {
    instanceFieldAssignments.forEach(
        (field, initializationInfo) -> {
          // Load the receiver, the field value, and then set the field.
          instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));
          int stackSizeForInitializationInfo =
              addCfInstructionsForInitializationInfo(
                  instructionBuilder, initializationInfo, argumentToLocalIndex, field.getType());
          DexField rewrittenField = lens.getRenamedFieldSignature(field, lens.getPrevious());

          // Insert a check to ensure the program continues to type check according to Java type
          // checking. Otherwise, instance initializer merging may cause open interfaces. If
          // <init>(A) and <init>(B) both have the behavior `this.i = arg; this.j = arg` where the
          // type of `i` is I and the type of `j` is J, and both A and B implements I and J, then
          // the constructors are merged into a single constructor <init>(java.lang.Object), which
          // is no longer strictly type checking. Note that no choice of parameter type would solve
          // this.
          if (initializationInfo.isArgumentInitializationInfo()) {
            int argumentIndex =
                initializationInfo.asArgumentInitializationInfo().getArgumentIndex();
            if (argumentIndex > 0) {
              DexType argumentType = method.getArgumentType(argumentIndex);
              if (argumentType.isClassType()
                  && !appView.appInfo().isSubtype(argumentType, rewrittenField.getType())) {
                instructionBuilder.add(new CfSafeCheckCast(rewrittenField.getType()));
              }
            }
          }

          instructionBuilder.add(new CfInstanceFieldWrite(rewrittenField));
          maxStack.setMax(stackSizeForInitializationInfo + 1);
        });
  }

  private static int addCfInstructionsForInitializationInfo(
      ImmutableList.Builder<CfInstruction> instructionBuilder,
      InstanceFieldInitializationInfo initializationInfo,
      int[] argumentToLocalIndex,
      DexType type) {
    if (initializationInfo.isArgumentInitializationInfo()) {
      int argumentIndex = initializationInfo.asArgumentInitializationInfo().getArgumentIndex();
      instructionBuilder.add(
          new CfLoad(ValueType.fromDexType(type), argumentToLocalIndex[argumentIndex]));
      return type.getRequiredRegisters();
    }

    assert initializationInfo.isSingleValue();
    assert initializationInfo.asSingleValue().isSingleConstValue();

    SingleConstValue singleConstValue = initializationInfo.asSingleValue().asSingleConstValue();
    if (singleConstValue.isSingleConstClassValue()) {
      instructionBuilder.add(
          new CfConstClass(singleConstValue.asSingleConstClassValue().getType()));
      return 1;
    } else if (singleConstValue.isSingleDexItemBasedStringValue()) {
      SingleDexItemBasedStringValue dexItemBasedStringValue =
          singleConstValue.asSingleDexItemBasedStringValue();
      instructionBuilder.add(
          new CfDexItemBasedConstString(
              dexItemBasedStringValue.getItem(), dexItemBasedStringValue.getNameComputationInfo()));
      return 1;
    } else if (singleConstValue.isNull()) {
      assert type.isReferenceType();
      instructionBuilder.add(new CfConstNull());
      return 1;
    } else if (singleConstValue.isSingleNumberValue()) {
      assert type.isPrimitiveType();
      instructionBuilder.add(
          new CfConstNumber(
              singleConstValue.asSingleNumberValue().getValue(), ValueType.fromDexType(type)));
      return type.getRequiredRegisters();
    } else {
      assert singleConstValue.isSingleStringValue();
      instructionBuilder.add(
          new CfConstString(singleConstValue.asSingleStringValue().getDexString()));
      return 1;
    }
  }

  @Override
  public String toString() {
    return "IncompleteMergedInstanceInitializerCode";
  }
}
