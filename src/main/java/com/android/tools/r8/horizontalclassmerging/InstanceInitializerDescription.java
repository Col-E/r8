// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
      AppView<? extends AppInfoWithClassHierarchy> appView, DexMethod instanceInitializer) {
    return new Builder(appView.dexItemFactory(), instanceInitializer);
  }

  @SuppressWarnings("InvalidParam")
  public static Builder builder(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod instanceInitializer) {
    return new Builder(appView.dexItemFactory(), instanceInitializer);
  }

  /**
   * Transform this description into actual CF code.
   *
   * @param originalMethodReference the original reference of the representative method
   * @param syntheticMethodReference the original, synthetic reference of the new method reference
   *     ($r8$init$synthetic)
   */
  public IncompleteMergedInstanceInitializerCode createCfCode(
      DexMethod originalMethodReference,
      DexMethod syntheticMethodReference,
      MergeGroup group,
      boolean hasClassId,
      int extraNulls) {
    return new IncompleteMergedInstanceInitializerCode(
        hasClassId ? group.getClassIdField() : null,
        extraNulls,
        originalMethodReference,
        syntheticMethodReference,
        instanceFieldAssignmentsPre,
        instanceFieldAssignmentsPost,
        parentConstructor,
        parentConstructorArguments);
  }

  @Override
  @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
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

    Builder(DexItemFactory dexItemFactory, DexMethod methodReference) {
      this.dexItemFactory = dexItemFactory;
      this.relaxedParameters =
          methodReference
              .getParameters()
              .map(
                  parameter -> parameter.isPrimitiveType() ? parameter : dexItemFactory.objectType);
    }

    Builder(DexItemFactory dexItemFactory, ProgramMethod method) {
      this(dexItemFactory, method.getReference());
    }

    @SuppressWarnings("ReferenceEquality")
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
