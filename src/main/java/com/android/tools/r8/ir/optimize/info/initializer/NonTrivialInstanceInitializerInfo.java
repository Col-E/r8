// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.ConcreteMutableFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.EmptyFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.UnknownFieldSet;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public final class NonTrivialInstanceInitializerInfo extends InstanceInitializerInfo {

  private static final int INSTANCE_FIELD_INITIALIZATION_INDEPENDENT_OF_ENVIRONMENT = 1 << 0;
  private static final int NO_OTHER_SIDE_EFFECTS_THAN_INSTANCE_FIELD_ASSIGNMENTS = 1 << 1;
  private static final int RECEIVER_NEVER_ESCAPE_OUTSIDE_CONSTRUCTOR_CHAIN = 1 << 2;

  private final int data;
  private final InstanceFieldInitializationInfoCollection fieldInitializationInfos;
  private final AbstractFieldSet readSet;
  private final DexMethod parent;

  private NonTrivialInstanceInitializerInfo(
      int data,
      InstanceFieldInitializationInfoCollection fieldInitializationInfos,
      AbstractFieldSet readSet,
      DexMethod parent) {
    assert verifyNoUnknownBits(data);
    this.data = data;
    this.fieldInitializationInfos = fieldInitializationInfos;
    this.readSet = readSet;
    this.parent = parent;
  }

  @Override
  public boolean isNonTrivialInstanceInitializerInfo() {
    return true;
  }

  @Override
  public NonTrivialInstanceInitializerInfo asNonTrivialInstanceInitializerInfo() {
    return this;
  }

  private static boolean verifyNoUnknownBits(int data) {
    int knownBits =
        INSTANCE_FIELD_INITIALIZATION_INDEPENDENT_OF_ENVIRONMENT
            | NO_OTHER_SIDE_EFFECTS_THAN_INSTANCE_FIELD_ASSIGNMENTS
            | RECEIVER_NEVER_ESCAPE_OUTSIDE_CONSTRUCTOR_CHAIN;
    assert (data & ~knownBits) == 0;
    return true;
  }

  public static Builder builder(
      InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos) {
    return new Builder(instanceFieldInitializationInfos);
  }

  @Override
  public boolean hasParent() {
    return parent != null;
  }

  @Override
  public DexMethod getParent() {
    return parent;
  }

  @Override
  public InstanceFieldInitializationInfoCollection fieldInitializationInfos() {
    return fieldInitializationInfos;
  }

  @Override
  public AbstractFieldSet readSet() {
    return readSet;
  }

  @Override
  public boolean instanceFieldInitializationMayDependOnEnvironment() {
    return (data & INSTANCE_FIELD_INITIALIZATION_INDEPENDENT_OF_ENVIRONMENT) == 0;
  }

  @Override
  public boolean mayHaveOtherSideEffectsThanInstanceFieldAssignments() {
    return (data & NO_OTHER_SIDE_EFFECTS_THAN_INSTANCE_FIELD_ASSIGNMENTS) == 0;
  }

  @Override
  public boolean receiverNeverEscapesOutsideConstructorChain() {
    return (data & RECEIVER_NEVER_ESCAPE_OUTSIDE_CONSTRUCTOR_CHAIN) != 0;
  }

  @Override
  public NonTrivialInstanceInitializerInfo fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection argumentInfoCollection) {
    return new NonTrivialInstanceInitializerInfo(
        data,
        fieldInitializationInfos.fixupAfterParametersChanged(argumentInfoCollection),
        readSet.fixupReadSetAfterParametersChanged(appView, argumentInfoCollection),
        parent);
  }

  @Override
  public NonTrivialInstanceInitializerInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView,
      GraphLens lens,
      GraphLens codeLens,
      PrunedItems prunedItems) {
    return new NonTrivialInstanceInitializerInfo(
        data,
        fieldInitializationInfos.rewrittenWithLens(appView, lens, codeLens),
        readSet.rewrittenWithLens(appView, lens, codeLens, prunedItems),
        lens.getRenamedMethodSignature(parent, codeLens));
  }

  @Override
  public String toString() {
    return "NonTrivialInstanceInitializerInfo(" + fieldInitializationInfos + ")";
  }

  public static class Builder {

    private final InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos;

    private int data =
        INSTANCE_FIELD_INITIALIZATION_INDEPENDENT_OF_ENVIRONMENT
            | NO_OTHER_SIDE_EFFECTS_THAN_INSTANCE_FIELD_ASSIGNMENTS
            | RECEIVER_NEVER_ESCAPE_OUTSIDE_CONSTRUCTOR_CHAIN;
    private AbstractFieldSet readSet = EmptyFieldSet.getInstance();
    private DexMethod parent;

    public Builder(InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos) {
      this.instanceFieldInitializationInfos = instanceFieldInitializationInfos;
    }

    private boolean isTrivial() {
      return instanceFieldInitializationInfos.isEmpty()
          && data == 0
          && readSet.isTop()
          && parent == null;
    }

    public Builder markFieldAsRead(DexClassAndField field) {
      if (readSet.isKnownFieldSet()) {
        if (readSet.isBottom()) {
          readSet = new ConcreteMutableFieldSet(field.getDefinition());
        } else {
          readSet.asConcreteFieldSet().add(field.getDefinition());
        }
      }
      assert readSet.contains(field);
      return this;
    }

    public Builder markFieldsAsRead(AbstractFieldSet otherReadSet) {
      if (readSet.isTop() || otherReadSet.isBottom()) {
        return this;
      }
      if (otherReadSet.isTop()) {
        return markAllFieldsAsRead();
      }
      ConcreteMutableFieldSet otherConcreteReadSet = otherReadSet.asConcreteFieldSet();
      if (readSet.isBottom()) {
        readSet = new ConcreteMutableFieldSet().addAll(otherConcreteReadSet);
      } else {
        readSet.asConcreteFieldSet().addAll(otherConcreteReadSet);
      }
      return this;
    }

    public Builder markAllFieldsAsRead() {
      readSet = UnknownFieldSet.getInstance();
      return this;
    }

    public Builder merge(InstanceInitializerInfo instanceInitializerInfo) {
      markFieldsAsRead(instanceInitializerInfo.readSet());
      if (instanceInitializerInfo.instanceFieldInitializationMayDependOnEnvironment()) {
        setInstanceFieldInitializationMayDependOnEnvironment();
      }
      if (instanceInitializerInfo.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
        setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
      }
      if (instanceInitializerInfo.receiverMayEscapeOutsideConstructorChain()) {
        setReceiverMayEscapeOutsideConstructorChain();
      }
      return this;
    }

    public Builder setInstanceFieldInitializationMayDependOnEnvironment() {
      data &= ~INSTANCE_FIELD_INITIALIZATION_INDEPENDENT_OF_ENVIRONMENT;
      return this;
    }

    public boolean mayHaveOtherSideEffectsThanInstanceFieldAssignments() {
      return (data & ~NO_OTHER_SIDE_EFFECTS_THAN_INSTANCE_FIELD_ASSIGNMENTS) == 0;
    }

    public Builder setMayHaveOtherSideEffectsThanInstanceFieldAssignments() {
      data &= ~NO_OTHER_SIDE_EFFECTS_THAN_INSTANCE_FIELD_ASSIGNMENTS;
      return this;
    }

    public Builder setReceiverMayEscapeOutsideConstructorChain() {
      data &= ~RECEIVER_NEVER_ESCAPE_OUTSIDE_CONSTRUCTOR_CHAIN;
      return this;
    }

    public boolean hasParent() {
      return parent != null;
    }

    public DexMethod getParent() {
      return parent;
    }

    @SuppressWarnings("ReferenceEquality")
    public Builder setParent(DexMethod parent) {
      assert !hasParent() || getParent() == parent;
      this.parent = parent;
      return this;
    }

    public InstanceInitializerInfo build() {
      return isTrivial()
          ? DefaultInstanceInitializerInfo.getInstance()
          : new NonTrivialInstanceInitializerInfo(
              data, instanceFieldInitializationInfos, readSet, parent);
    }
  }
}
