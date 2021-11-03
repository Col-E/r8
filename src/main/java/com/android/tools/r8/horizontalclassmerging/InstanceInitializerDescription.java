// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
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
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.SingleConstValue;
import com.android.tools.r8.ir.analysis.value.SingleDexItemBasedStringValue;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.utils.IntBox;
import com.google.common.collect.ImmutableList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.objectweb.asm.Opcodes;

/**
 * A simple abstraction of an instance initializer's code, which allows a parent constructor call
 * followed by a sequence of instance-put instructions.
 */
public class InstanceInitializerDescription {

  // Field assignments that happen prior to the parent constructor call.
  //
  // Most fields are generally assigned after the parent constructor call, but both javac and
  // kotlinc may assign instance fields prior to the parent constructor call. For example, the
  // synthetic this$0 field for non-static inner classes is typically assigned prior to the parent
  // constructor call.
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre;

  // Field assignments that happens after the parent constructor call.
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost;

  // The parent constructor method and the arguments passed to it.
  private final DexMethod parentConstructor;
  private final List<InstanceFieldInitializationInfo> parentConstructorArguments;

  // The constructor parameters, where reference types have been mapped to java.lang.Object, to
  // ensure we don't group constructors such as <init>(int) and <init>(Object), since this would
  // lead to type errors.
  private final DexTypeList relaxedParameters;

  InstanceInitializerDescription(
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost,
      DexMethod parentConstructor,
      List<InstanceFieldInitializationInfo> parentConstructorArguments,
      DexTypeList relaxedParameters) {
    this.instanceFieldAssignmentsPre = instanceFieldAssignmentsPre;
    this.instanceFieldAssignmentsPost = instanceFieldAssignmentsPost;
    this.parentConstructor = parentConstructor;
    this.parentConstructorArguments = parentConstructorArguments;
    this.relaxedParameters = relaxedParameters;
  }

  public static Builder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod instanceInitializer) {
    return new Builder(appView.dexItemFactory(), instanceInitializer);
  }

  /**
   * Transform this description into actual CF code.
   *
   * @param newMethodReference the reference of the method that is being synthesized
   * @param originalMethodReference the original reference of the representative method
   * @param syntheticMethodReference the original, synthetic reference of the new method reference
   *     ($r8$init$synthetic)
   */
  public CfCode createCfCode(
      DexMethod newMethodReference,
      DexMethod originalMethodReference,
      DexMethod syntheticMethodReference,
      MergeGroup group,
      boolean hasClassId,
      int extraNulls) {
    int[] argumentToLocalIndex =
        new int[newMethodReference.getParameters().size() + 1 - extraNulls];
    int maxLocals = 0;
    argumentToLocalIndex[0] = maxLocals++;
    for (int i = 1; i < argumentToLocalIndex.length; i++) {
      argumentToLocalIndex[i] = maxLocals;
      maxLocals += newMethodReference.getParameter(i - 1).getRequiredRegisters();
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
    if (group.hasClassIdField()) {
      assert hasClassId;
      int classIdLocalIndex = maxLocals - 1;
      instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));
      instructionBuilder.add(new CfLoad(ValueType.INT, classIdLocalIndex));
      instructionBuilder.add(new CfInstanceFieldWrite(group.getClassIdField()));
      maxStack.set(2);
    } else {
      assert !hasClassId;
    }

    // Assign each field.
    addCfInstructionsForInstanceFieldAssignments(
        instructionBuilder, instanceFieldAssignmentsPre, argumentToLocalIndex, maxStack);

    // Load receiver for parent constructor call.
    int stackHeightForParentConstructorCall = 1;
    instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));

    // Load constructor arguments.
    int i = 0;
    for (InstanceFieldInitializationInfo initializationInfo : parentConstructorArguments) {
      stackHeightForParentConstructorCall +=
          addCfInstructionsForInitializationInfo(
              instructionBuilder,
              initializationInfo,
              argumentToLocalIndex,
              parentConstructor.getParameter(i));
      i++;
    }

    // Invoke parent constructor.
    instructionBuilder.add(new CfInvoke(Opcodes.INVOKESPECIAL, parentConstructor, false));
    maxStack.setMax(stackHeightForParentConstructorCall);

    // Assign each field.
    addCfInstructionsForInstanceFieldAssignments(
        instructionBuilder, instanceFieldAssignmentsPost, argumentToLocalIndex, maxStack);

    // Return.
    instructionBuilder.add(new CfReturnVoid());

    return new HorizontalClassMergerCfCode(
        newMethodReference.getHolderType(),
        maxStack.get(),
        maxLocals,
        instructionBuilder.build(),
        ImmutableList.of(),
        ImmutableList.of());
  }

  private static void addCfInstructionsForInstanceFieldAssignments(
      ImmutableList.Builder<CfInstruction> instructionBuilder,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignments,
      int[] argumentToLocalIndex,
      IntBox maxStack) {
    instanceFieldAssignments.forEach(
        (field, initializationInfo) -> {
          // Load the receiver, the field value, and then set the field.
          instructionBuilder.add(new CfLoad(ValueType.OBJECT, 0));
          int stackSizeForInitializationInfo =
              addCfInstructionsForInitializationInfo(
                  instructionBuilder, initializationInfo, argumentToLocalIndex, field.getType());
          instructionBuilder.add(new CfInstanceFieldWrite(field));
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
    } else if (singleConstValue.isSingleNumberValue()) {
      if (type.isReferenceType()) {
        assert singleConstValue.isNull();
        instructionBuilder.add(new CfConstNull());
        return 1;
      } else {
        instructionBuilder.add(
            new CfConstNumber(
                singleConstValue.asSingleNumberValue().getValue(), ValueType.fromDexType(type)));
        return type.getRequiredRegisters();
      }
    } else {
      assert singleConstValue.isSingleStringValue();
      instructionBuilder.add(
          new CfConstString(singleConstValue.asSingleStringValue().getDexString()));
      return 1;
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    InstanceInitializerDescription description = (InstanceInitializerDescription) obj;
    return instanceFieldAssignmentsPre.equals(description.instanceFieldAssignmentsPre)
        && instanceFieldAssignmentsPost.equals(description.instanceFieldAssignmentsPost)
        && parentConstructor == description.parentConstructor
        && parentConstructorArguments.equals(description.parentConstructorArguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        instanceFieldAssignmentsPre,
        instanceFieldAssignmentsPost,
        parentConstructor,
        parentConstructorArguments,
        relaxedParameters);
  }

  public static class Builder {

    private final DexItemFactory dexItemFactory;
    private final DexTypeList relaxedParameters;

    private Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPre =
        new LinkedHashMap<>();
    private Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignmentsPost =
        new LinkedHashMap<>();
    private DexMethod parentConstructor;
    private List<InstanceFieldInitializationInfo> parentConstructorArguments;

    Builder(DexItemFactory dexItemFactory, ProgramMethod method) {
      this.dexItemFactory = dexItemFactory;
      this.relaxedParameters =
          method
              .getParameters()
              .map(
                  parameter -> parameter.isPrimitiveType() ? parameter : dexItemFactory.objectType);
    }

    public void addInstancePut(DexField field, InstanceFieldInitializationInfo value) {
      if (parentConstructor == null) {
        instanceFieldAssignmentsPre.put(field, value);
        return;
      }

      // If the parent constructor is java.lang.Object.<init>() then group all the field assignments
      // before the parent constructor call to allow more sharing.
      //
      // Note that field assignments that store the receiver cannot be hoisted to before the
      // Object.<init>() call, since this would lead to an illegal use of the uninitialized 'this'.
      if (parentConstructor == dexItemFactory.objectMembers.constructor) {
        if (!value.isArgumentInitializationInfo()
            || value.asArgumentInitializationInfo().getArgumentIndex() != 0) {
          instanceFieldAssignmentsPre.put(field, value);
          return;
        }
      }

      instanceFieldAssignmentsPost.put(field, value);
    }

    public boolean addInvokeConstructor(
        DexMethod method, List<InstanceFieldInitializationInfo> arguments) {
      if (parentConstructor == null) {
        parentConstructor = method;
        parentConstructorArguments = arguments;
        return true;
      }
      return false;
    }

    public InstanceInitializerDescription build() {
      assert isValid();
      return new InstanceInitializerDescription(
          instanceFieldAssignmentsPre,
          instanceFieldAssignmentsPost,
          parentConstructor,
          parentConstructorArguments,
          relaxedParameters);
    }

    public boolean isValid() {
      return parentConstructor != null;
    }
  }
}
