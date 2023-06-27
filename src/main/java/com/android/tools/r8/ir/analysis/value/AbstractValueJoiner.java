// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.ir.analysis.value.AbstractValue.unknown;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerUtils;

public abstract class AbstractValueJoiner {

  protected final AppView<? extends AppInfoWithClassHierarchy> appView;

  private AbstractValueJoiner(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
  }

  final AbstractValue internalJoin(
      AbstractValue abstractValue,
      AbstractValue otherAbstractValue,
      AbstractValueJoinerConfig config,
      DexType type) {
    if (abstractValue.isBottom() || otherAbstractValue.isUnknown()) {
      return otherAbstractValue;
    }
    if (abstractValue.isUnknown()
        || otherAbstractValue.isBottom()
        || abstractValue.equals(otherAbstractValue)) {
      return abstractValue;
    }
    return type.isReferenceType()
        ? joinReference(abstractValue, otherAbstractValue)
        : joinPrimitive(abstractValue, otherAbstractValue, config);
  }

  private AbstractValue joinPrimitive(
      AbstractValue abstractValue,
      AbstractValue otherAbstractValue,
      AbstractValueJoinerConfig config) {
    assert !abstractValue.isNullOrAbstractValue();
    assert !otherAbstractValue.isNullOrAbstractValue();

    if (config.canUseDefiniteBitsAbstraction()
        && abstractValue.isConstantOrNonConstantNumberValue()
        && otherAbstractValue.isConstantOrNonConstantNumberValue()) {
      // TODO(b/196017578): Implement join.
    }

    if (config.canUseNumberIntervalAndNumberSetAbstraction()
        && abstractValue.isConstantOrNonConstantNumberValue()
        && otherAbstractValue.isConstantOrNonConstantNumberValue()) {
      NumberFromSetValue.Builder numberFromSetValueBuilder;
      if (abstractValue.isSingleNumberValue()) {
        numberFromSetValueBuilder = NumberFromSetValue.builder(abstractValue.asSingleNumberValue());
      } else {
        assert abstractValue.isNumberFromSetValue();
        numberFromSetValueBuilder = abstractValue.asNumberFromSetValue().instanceBuilder();
      }
      if (otherAbstractValue.isSingleNumberValue()) {
        numberFromSetValueBuilder.addInt(otherAbstractValue.asSingleNumberValue().getIntValue());
      } else {
        assert otherAbstractValue.isNumberFromSetValue();
        numberFromSetValueBuilder.addInts(otherAbstractValue.asNumberFromSetValue());
      }
      return numberFromSetValueBuilder.build(appView.abstractValueFactory());
    }

    return unknown();
  }

  private AbstractValue joinReference(
      AbstractValue abstractValue, AbstractValue otherAbstractValue) {
    if (abstractValue.isNull()) {
      return NullOrAbstractValue.create(otherAbstractValue);
    }
    if (otherAbstractValue.isNull()) {
      return NullOrAbstractValue.create(abstractValue);
    }
    if (abstractValue.isNullOrAbstractValue()
        && abstractValue.asNullOrAbstractValue().getNonNullValue().equals(otherAbstractValue)) {
      return abstractValue;
    }
    if (otherAbstractValue.isNullOrAbstractValue()
        && otherAbstractValue.asNullOrAbstractValue().getNonNullValue().equals(abstractValue)) {
      return otherAbstractValue;
    }
    return unknown();
  }

  public static class AbstractValueFieldJoiner extends AbstractValueJoiner {

    public AbstractValueFieldJoiner(AppView<? extends AppInfoWithClassHierarchy> appView) {
      super(appView);
    }

    public AbstractValue join(
        AbstractValue abstractValue, AbstractValue otherAbstractValue, ProgramField field) {
      AbstractValueJoinerConfig config = getConfig(field);
      AbstractValue result =
          internalJoin(abstractValue, otherAbstractValue, config, field.getType());
      assert result.equals(
          internalJoin(otherAbstractValue, abstractValue, config, field.getType()));
      return result;
    }

    private AbstractValueJoinerConfig getConfig(ProgramField field) {
      if (HorizontalClassMergerUtils.isClassIdField(appView, field)) {
        return AbstractValueJoinerConfig.getClassIdFieldConfig();
      }
      return AbstractValueJoinerConfig.getDefaultConfig();
    }
  }

  public static class AbstractValueParameterJoiner extends AbstractValueJoiner {

    public AbstractValueParameterJoiner(AppView<? extends AppInfoWithClassHierarchy> appView) {
      super(appView);
    }

    public AbstractValue join(
        AbstractValue abstractValue, AbstractValue otherAbstractValue, DexType type) {
      // TODO(b/196017578): Use a config that allows the definite bits abstraction for parameters
      //  used in bitwise operations.
      AbstractValueJoinerConfig config = AbstractValueJoinerConfig.getDefaultConfig();
      AbstractValue result = internalJoin(abstractValue, otherAbstractValue, config, type);
      assert result.equals(internalJoin(otherAbstractValue, abstractValue, config, type));
      return result;
    }
  }

  private static class AbstractValueJoinerConfig {

    // The power set lattice is an expensive abstraction, so use it with caution.
    private static final AbstractValueJoinerConfig CLASS_ID_FIELD_CONFIG =
        new AbstractValueJoinerConfig().setCanUseNumberIntervalAndNumberSetAbstraction();

    private static final AbstractValueJoinerConfig DEFAULT_CONFIG = new AbstractValueJoinerConfig();

    public static AbstractValueJoinerConfig getClassIdFieldConfig() {
      return CLASS_ID_FIELD_CONFIG;
    }

    public static AbstractValueJoinerConfig getDefaultConfig() {
      return DEFAULT_CONFIG;
    }

    private boolean canUseDefiniteBitsAbstraction;
    private boolean canUseNumberIntervalAndNumberSetAbstraction;

    boolean canUseDefiniteBitsAbstraction() {
      return canUseDefiniteBitsAbstraction;
    }

    @SuppressWarnings("UnusedMethod")
    AbstractValueJoinerConfig setCanUseDefiniteBitsAbstraction() {
      canUseDefiniteBitsAbstraction = true;
      return this;
    }

    boolean canUseNumberIntervalAndNumberSetAbstraction() {
      return canUseNumberIntervalAndNumberSetAbstraction;
    }

    AbstractValueJoinerConfig setCanUseNumberIntervalAndNumberSetAbstraction() {
      canUseNumberIntervalAndNumberSetAbstraction = true;
      return this;
    }
  }
}
