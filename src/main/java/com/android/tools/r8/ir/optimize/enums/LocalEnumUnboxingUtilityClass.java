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
      DexField field) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    String fieldName = field.getName().toString();
    DexString methodName;
    if (field.getHolderType() == getSynthesizingContext().getType()) {
      methodName =
          dexItemFactory.createString(
              "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
    } else {
      assert field == appView.dexItemFactory().enumMembers.nameField
          || field == appView.dexItemFactory().enumMembers.ordinalField;
      methodName = field.getName();
    }
    return internalEnsureMethod(
        appView,
        methodName,
        dexItemFactory.createProto(field.getType(), dexItemFactory.intType),
        method ->
            new EnumUnboxingCfCodeProvider.EnumUnboxingInstanceFieldCfCodeProvider(
                    appView, getType(), data, field)
                .generateCfCode());
  }

  public ProgramMethod ensureStringValueOfMethod(AppView<AppInfoWithLiveness> appView) {
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

  public ProgramMethod ensureValueOfMethod(AppView<AppInfoWithLiveness> appView) {
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
