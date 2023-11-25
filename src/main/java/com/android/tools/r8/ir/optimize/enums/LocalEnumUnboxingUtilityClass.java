// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap.EnumData;
import com.android.tools.r8.ir.synthetic.EnumUnboxingCfCodeProvider;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticMethodBuilder.SyntheticCodeGenerator;
import com.android.tools.r8.utils.StringUtils;

public class LocalEnumUnboxingUtilityClass extends EnumUnboxingUtilityClass {

  private static final String ENUM_UNBOXING_LOCAL_UTILITY_CLASS_SUFFIX =
      "$r8$EnumUnboxingLocalUtility";

  private final DexProgramClass localUtilityClass;
  private final EnumData data;

  public LocalEnumUnboxingUtilityClass(
      DexProgramClass localUtilityClass, EnumData data, DexProgramClass synthesizingContext) {
    super(synthesizingContext);
    this.localUtilityClass = localUtilityClass;
    this.data = data;
  }

  public static Builder builder(
      AppView<AppInfoWithLiveness> appView, DexProgramClass enumToUnbox, EnumData data) {
    return new Builder(appView, enumToUnbox, data);
  }

  @Override
  public void ensureMethods(AppView<AppInfoWithLiveness> appView) {
    data.instanceFieldMap.forEach(
        (field, fieldData) -> {
          if (fieldData.isMapping()) {
            ensureGetInstanceFieldMethod(appView, field);
          }
        });
    if (data.instanceFieldMap.containsKey(appView.dexItemFactory().enumMembers.nameField)) {
      ensureStringValueOfMethod(appView);
      ensureValueOfMethod(appView);
    }
  }

  public ProgramMethod ensureGetInstanceFieldMethod(
      AppView<AppInfoWithLiveness> appView,
      DexField field,
      ProgramMethod context,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    ProgramMethod method = ensureGetInstanceFieldMethod(appView, field);
    eventConsumer.acceptEnumUnboxerLocalUtilityClassMethodContext(method, context);
    return method;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexString computeGetInstanceFieldMethodName(DexField field, DexItemFactory factory) {
    String fieldName = field.getName().toString();
    if (field.getHolderType() == getSynthesizingContext().getType()) {
      return factory.createString(
          "get" + StringUtils.toUpperCase(fieldName.substring(0, 1)) + fieldName.substring(1));
    }
    assert field == factory.enumMembers.nameField || field == factory.enumMembers.ordinalField;
    return field.getName();
  }

  private DexProto computeGetInstanceFieldMethodProto(DexField field, DexItemFactory factory) {
    return factory.createProto(field.getType(), factory.intType);
  }

  public ProgramMethod ensureToStringMethod(AppView<AppInfoWithLiveness> appView) {
    return ensureGetInstanceFieldMethod(appView, appView.dexItemFactory().enumMembers.nameField);
  }

  private ProgramMethod ensureGetInstanceFieldMethod(
      AppView<AppInfoWithLiveness> appView, DexField field) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexString methodName = computeGetInstanceFieldMethodName(field, dexItemFactory);
    return internalEnsureMethod(
        appView,
        methodName,
        computeGetInstanceFieldMethodProto(field, dexItemFactory),
        method ->
            new EnumUnboxingCfCodeProvider.EnumUnboxingInstanceFieldCfCodeProvider(
                    appView, getType(), data, field)
                .generateCfCode());
  }

  public ProgramMethod ensureStringValueOfMethod(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod context,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    ProgramMethod method = ensureStringValueOfMethod(appView);
    eventConsumer.acceptEnumUnboxerLocalUtilityClassMethodContext(method, context);
    return method;
  }

  private ProgramMethod ensureStringValueOfMethod(AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    AbstractValue defaultValue =
        appView.abstractValueFactory().createSingleStringValue(dexItemFactory.createString("null"));
    return internalEnsureMethod(
        appView,
        dexItemFactory.createString("stringValueOf"),
        dexItemFactory.createProto(dexItemFactory.stringType, dexItemFactory.intType),
        method ->
            new EnumUnboxingCfCodeProvider.EnumUnboxingInstanceFieldCfCodeProvider(
                    appView, getType(), data, dexItemFactory.enumMembers.nameField, defaultValue)
                .generateCfCode());
  }

  public ProgramMethod ensureValueOfMethod(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod context,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    ProgramMethod method = ensureValueOfMethod(appView);
    eventConsumer.acceptEnumUnboxerLocalUtilityClassMethodContext(method, context);
    return method;
  }

  private ProgramMethod ensureValueOfMethod(AppView<AppInfoWithLiveness> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    return internalEnsureMethod(
        appView,
        dexItemFactory.createString("valueOf"),
        dexItemFactory.createProto(dexItemFactory.intType, dexItemFactory.stringType),
        method ->
            new EnumUnboxingCfCodeProvider.EnumUnboxingValueOfCfCodeProvider(
                    appView,
                    getType(),
                    getSynthesizingContext().getType(),
                    data.getInstanceFieldData(dexItemFactory.enumMembers.nameField)
                        .asEnumFieldMappingData())
                .generateCfCode());
  }

  private ProgramMethod internalEnsureMethod(
      AppView<AppInfoWithLiveness> appView,
      DexString methodName,
      DexProto methodProto,
      SyntheticCodeGenerator codeGenerator) {
    return appView
        .getSyntheticItems()
        .ensureFixedClassMethod(
            methodName,
            methodProto,
            kinds -> kinds.ENUM_UNBOXING_LOCAL_UTILITY_CLASS,
            getSynthesizingContext(),
            appView,
            emptyConsumer(),
            methodBuilder ->
                methodBuilder
                    .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                    .setApiLevelForDefinition(appView.computedMinApiLevel())
                    .setApiLevelForCode(appView.computedMinApiLevel())
                    .setCode(codeGenerator)
                    .setClassFileVersion(CfVersion.V1_6));
  }

  @Override
  public DexProgramClass getDefinition() {
    return localUtilityClass;
  }

  public DexType getType() {
    return localUtilityClass.getType();
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final EnumData data;
    private final DexProgramClass enumToUnbox;
    private final DexType localUtilityClassType;

    private Builder(
        AppView<AppInfoWithLiveness> appView, DexProgramClass enumToUnbox, EnumData data) {
      this.appView = appView;
      this.data = data;
      this.enumToUnbox = enumToUnbox;
      this.localUtilityClassType =
          EnumUnboxingUtilityClasses.Builder.getUtilityClassType(
              enumToUnbox, ENUM_UNBOXING_LOCAL_UTILITY_CLASS_SUFFIX, appView.dexItemFactory());

      assert appView.appInfo().definitionForWithoutExistenceAssert(localUtilityClassType) == null;
    }

    LocalEnumUnboxingUtilityClass build() {
      DexProgramClass clazz = createClass();
      return new LocalEnumUnboxingUtilityClass(clazz, data, enumToUnbox);
    }

    private DexProgramClass createClass() {
      DexProgramClass clazz =
          appView
              .getSyntheticItems()
              .createFixedClass(
                  kinds -> kinds.ENUM_UNBOXING_LOCAL_UTILITY_CLASS,
                  enumToUnbox,
                  appView,
                  builder -> builder.setUseSortedMethodBacking(true));
      assert clazz.getAccessFlags().equals(ClassAccessFlags.createPublicFinalSynthetic());
      return clazz;
    }
  }
}
