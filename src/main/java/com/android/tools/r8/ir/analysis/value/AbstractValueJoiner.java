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
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public abstract class AbstractValueJoiner {

  protected final AppView<?> appView;

  private AbstractValueJoiner(AppView<?> appView) {
    this.appView = appView;
  }

  private AbstractValueFactory factory() {
    return appView.abstractValueFactory();
  }

  final AbstractValue internalJoin(
      AbstractValue abstractValue,
      AbstractValue otherAbstractValue,
      AbstractValueJoinerConfig config,
      TypeElement type) {
    if (abstractValue.isBottom() || otherAbstractValue.isUnknown()) {
      return otherAbstractValue;
    }
    if (abstractValue.isUnknown()
        || otherAbstractValue.isBottom()
        || abstractValue.equals(otherAbstractValue)) {
      return abstractValue;
    }
    if (type.isReferenceType()) {
      return joinReference(abstractValue, otherAbstractValue);
    } else {
      assert type.isPrimitiveType();
      return joinPrimitive(abstractValue, otherAbstractValue, config, type.asPrimitiveType());
    }
  }

  private AbstractValue joinPrimitive(
      AbstractValue abstractValue,
      AbstractValue otherAbstractValue,
      AbstractValueJoinerConfig config,
      PrimitiveTypeElement type) {
    assert !abstractValue.isNullOrAbstractValue();
    assert !otherAbstractValue.isNullOrAbstractValue();

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
      return numberFromSetValueBuilder.build(factory());
    }

    if (config.canUseDefiniteBitsAbstraction() && type.isInt()) {
      return joinPrimitiveToDefiniteBitsNumberValue(abstractValue, otherAbstractValue);
    }

    return unknown();
  }

  private AbstractValue joinPrimitiveToDefiniteBitsNumberValue(
      AbstractValue abstractValue, AbstractValue otherAbstractValue) {
    if (!abstractValue.hasDefinitelySetAndUnsetBitsInformation()
        || !otherAbstractValue.hasDefinitelySetAndUnsetBitsInformation()) {
      return unknown();
    }
    // Normalize order.
    if (!abstractValue.isSingleNumberValue() && otherAbstractValue.isSingleNumberValue()) {
      AbstractValue tmp = abstractValue;
      abstractValue = otherAbstractValue;
      otherAbstractValue = tmp;
    }
    if (abstractValue.isSingleNumberValue()) {
      SingleNumberValue singleNumberValue = abstractValue.asSingleNumberValue();
      if (otherAbstractValue.isSingleNumberValue()) {
        SingleNumberValue otherSingleNumberValue = otherAbstractValue.asSingleNumberValue();
        return factory()
            .createDefiniteBitsNumberValue(
                singleNumberValue.getDefinitelySetIntBits()
                    & otherSingleNumberValue.getDefinitelySetIntBits(),
                singleNumberValue.getDefinitelyUnsetIntBits()
                    & otherSingleNumberValue.getDefinitelyUnsetIntBits());
      } else {
        assert otherAbstractValue.isDefiniteBitsNumberValue();
        DefiniteBitsNumberValue otherDefiniteBitsNumberValue =
            otherAbstractValue.asDefiniteBitsNumberValue();
        return otherDefiniteBitsNumberValue.join(factory(), singleNumberValue);
      }
    } else {
      // Both are guaranteed to be non-const due to normalization.
      assert abstractValue.isDefiniteBitsNumberValue();
      assert otherAbstractValue.isDefiniteBitsNumberValue();
      DefiniteBitsNumberValue definiteBitsNumberValue = abstractValue.asDefiniteBitsNumberValue();
      DefiniteBitsNumberValue otherDefiniteBitsNumberValue =
          otherAbstractValue.asDefiniteBitsNumberValue();
      return definiteBitsNumberValue.join(factory(), otherDefiniteBitsNumberValue);
    }
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

  public static class AbstractValueConstantPropagationJoiner extends AbstractValueJoiner {

    public AbstractValueConstantPropagationJoiner(AppView<?> appView) {
      super(appView);
    }

    public AbstractValue join(
        AbstractValue abstractValue, AbstractValue otherAbstractValue, TypeElement type) {
      AbstractValueJoinerConfig config = AbstractValueJoinerConfig.getDefaultConfig();
      AbstractValue result = internalJoin(abstractValue, otherAbstractValue, config, type);
      assert result.equals(internalJoin(otherAbstractValue, abstractValue, config, type));
      return result;
    }

    public boolean lessThanOrEqualTo(
        AbstractValue abstractValue, AbstractValue otherAbstractValue, TypeElement type) {
      return join(abstractValue, otherAbstractValue, type).equals(otherAbstractValue);
    }
  }

  public static class AbstractValueFieldJoiner extends AbstractValueJoiner {

    public AbstractValueFieldJoiner(AppView<? extends AppInfoWithClassHierarchy> appView) {
      super(appView);
    }

    public AbstractValue join(
        AbstractValue abstractValue, AbstractValue otherAbstractValue, ProgramField field) {
      AbstractValueJoinerConfig config = getConfig(field);
      TypeElement type = field.getType().toTypeElement(appView);
      AbstractValue result = internalJoin(abstractValue, otherAbstractValue, config, type);
      assert result.equals(internalJoin(otherAbstractValue, abstractValue, config, type));
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
      TypeElement typeElement = type.toTypeElement(appView);
      AbstractValue result = internalJoin(abstractValue, otherAbstractValue, config, typeElement);
      assert result.equals(internalJoin(otherAbstractValue, abstractValue, config, typeElement));
      return result;
    }
  }

  private static class AbstractValueJoinerConfig {

    // The power set lattice is an expensive abstraction, so use it with caution.
    private static final AbstractValueJoinerConfig CLASS_ID_FIELD_CONFIG =
        new AbstractValueJoinerConfig().setCanUseNumberIntervalAndNumberSetAbstraction();

    private static final AbstractValueJoinerConfig DEFAULT_CONFIG =
        new AbstractValueJoinerConfig().setCanUseDefiniteBitsAbstraction();

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
