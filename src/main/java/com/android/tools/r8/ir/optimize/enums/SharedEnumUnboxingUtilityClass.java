// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;

public class SharedEnumUnboxingUtilityClass extends EnumUnboxingUtilityClass {

  public static final String ENUM_UNBOXING_SHARED_UTILITY_CLASS_SUFFIX =
      "$r8$EnumUnboxingSharedUtility";

  private final DexProgramClass sharedUtilityClass;
  private final ProgramField valuesField;
  private final ProgramMethod valuesMethod;

  public SharedEnumUnboxingUtilityClass(
      DexProgramClass sharedUtilityClass, ProgramField valuesField, ProgramMethod valuesMethod) {
    this.sharedUtilityClass = sharedUtilityClass;
    this.valuesField = valuesField;
    this.valuesMethod = valuesMethod;
  }

  public static Builder builder(
      AppView<AppInfoWithLiveness> appView,
      EnumDataMap enumDataMap,
      Set<DexProgramClass> enumsToUnbox,
      FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
    return new Builder(
        appView, enumDataMap, enumsToUnbox, fieldAccessInfoCollectionModifierBuilder);
  }

  @Override
  public DexProgramClass getDefinition() {
    return sharedUtilityClass;
  }

  public ProgramField getValuesField() {
    return valuesField;
  }

  public ProgramMethod getValuesMethod() {
    return valuesMethod;
  }

  public DexType getType() {
    return sharedUtilityClass.getType();
  }

  public static class Builder {

    private final AppView<AppInfoWithLiveness> appView;
    private final DexItemFactory dexItemFactory;
    private final EnumDataMap enumDataMap;
    private final Set<DexProgramClass> enumsToUnbox;
    private final FieldAccessInfoCollectionModifier.Builder
        fieldAccessInfoCollectionModifierBuilder;
    private final DexType sharedUtilityClassType;

    private DexEncodedField valuesField;
    private DexEncodedMethod valuesMethod;

    private Builder(
        AppView<AppInfoWithLiveness> appView,
        EnumDataMap enumDataMap,
        Set<DexProgramClass> enumsToUnbox,
        FieldAccessInfoCollectionModifier.Builder fieldAccessInfoCollectionModifierBuilder) {
      this.appView = appView;
      this.dexItemFactory = appView.dexItemFactory();
      this.enumDataMap = enumDataMap;
      this.enumsToUnbox = enumsToUnbox;
      this.fieldAccessInfoCollectionModifierBuilder = fieldAccessInfoCollectionModifierBuilder;
      this.sharedUtilityClassType =
          EnumUnboxingUtilityClasses.Builder.getUtilityClassType(
              findDeterministicContextType(enumsToUnbox),
              ENUM_UNBOXING_SHARED_UTILITY_CLASS_SUFFIX,
              dexItemFactory);

      assert appView.appInfo().definitionForWithoutExistenceAssert(sharedUtilityClassType) == null;
    }

    SharedEnumUnboxingUtilityClass build(DirectMappedDexApplication.Builder appBuilder) {
      DexProgramClass clazz = createClass();
      appBuilder.addSynthesizedClass(clazz);
      appView.appInfo().addSynthesizedClassToBase(clazz, enumsToUnbox);
      return new SharedEnumUnboxingUtilityClass(
          clazz, new ProgramField(clazz, valuesField), new ProgramMethod(clazz, valuesMethod));
    }

    private DexProgramClass createClass() {
      DexEncodedField valuesField = createValuesField(sharedUtilityClassType);
      return new DexProgramClass(
          sharedUtilityClassType,
          null,
          new SynthesizedOrigin("enum unboxing", EnumUnboxer.class),
          ClassAccessFlags.createPublicFinalSynthetic(),
          dexItemFactory.objectType,
          DexTypeList.empty(),
          null,
          null,
          Collections.emptyList(),
          null,
          Collections.emptyList(),
          ClassSignature.noSignature(),
          DexAnnotationSet.empty(),
          new DexEncodedField[] {valuesField},
          DexEncodedField.EMPTY_ARRAY,
          new DexEncodedMethod[] {
            createClassInitializer(valuesField), createValuesMethod(valuesField)
          },
          DexEncodedMethod.EMPTY_ARRAY,
          dexItemFactory.getSkipNameValidationForTesting(),
          DexProgramClass::checksumFromType);
    }

    // Fields.

    private DexEncodedField createValuesField(DexType sharedUtilityClassType) {
      DexEncodedField valuesField =
          new DexEncodedField(
              dexItemFactory.createField(
                  sharedUtilityClassType, dexItemFactory.intArrayType, "$VALUES"),
              FieldAccessFlags.createPublicStaticFinalSynthetic(),
              FieldTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              DexEncodedField.NO_STATIC_VALUE,
              DexEncodedField.NOT_DEPRECATED,
              DexEncodedField.D8_R8_SYNTHESIZED);
      fieldAccessInfoCollectionModifierBuilder
          .recordFieldReadInUnknownContext(valuesField.getReference())
          .recordFieldWriteInUnknownContext(valuesField.getReference());
      this.valuesField = valuesField;
      return valuesField;
    }

    // Methods.

    private DexEncodedMethod createClassInitializer(DexEncodedField valuesField) {
      return new DexEncodedMethod(
          dexItemFactory.createClassInitializer(sharedUtilityClassType),
          MethodAccessFlags.createForClassInitializer(),
          MethodTypeSignature.noSignature(),
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          createClassInitializerCode(valuesField),
          DexEncodedMethod.D8_R8_SYNTHESIZED,
          CfVersion.V1_6);
    }

    private CfCode createClassInitializerCode(DexEncodedField valuesField) {
      int maxValuesArraySize = enumDataMap.getMaxValuesSize();
      int numberOfInstructions = 4 + maxValuesArraySize * 4;
      List<CfInstruction> instructions = new ArrayList<>(numberOfInstructions);
      instructions.add(new CfConstNumber(maxValuesArraySize, ValueType.INT));
      instructions.add(new CfNewArray(dexItemFactory.intArrayType));
      for (int i = 0; i < maxValuesArraySize; i++) {
        instructions.add(new CfStackInstruction(Opcode.Dup));
        instructions.add(new CfConstNumber(i, ValueType.INT));
        // i + 1 because 0 represents the null value.
        instructions.add(new CfConstNumber(i + 1, ValueType.INT));
        instructions.add(new CfArrayStore(MemberType.INT));
      }
      instructions.add(new CfFieldInstruction(Opcodes.PUTSTATIC, valuesField.getReference()));
      instructions.add(new CfReturnVoid());

      int maxStack = 4;
      int maxLocals = 0;
      return new CfCode(
          sharedUtilityClassType,
          maxStack,
          maxLocals,
          instructions,
          Collections.emptyList(),
          Collections.emptyList());
    }

    private DexEncodedMethod createValuesMethod(DexEncodedField valuesField) {
      DexEncodedMethod valuesMethod =
          new DexEncodedMethod(
              dexItemFactory.createMethod(
                  sharedUtilityClassType,
                  dexItemFactory.createProto(dexItemFactory.intArrayType, dexItemFactory.intType),
                  "values"),
              MethodAccessFlags.createPublicStaticSynthetic(),
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              createValuesMethodCode(valuesField),
              DexEncodedMethod.D8_R8_SYNTHESIZED,
              CfVersion.V1_6);
      this.valuesMethod = valuesMethod;
      return valuesMethod;
    }

    private CfCode createValuesMethodCode(DexEncodedField valuesField) {
      int maxStack = 5;
      int maxLocals = 2;
      int argumentLocalSlot = 0;
      int resultLocalSlot = 1;
      return new CfCode(
          sharedUtilityClassType,
          maxStack,
          maxLocals,
          ImmutableList.of(
              // int[] result = new int[size];
              new CfLoad(ValueType.INT, argumentLocalSlot),
              new CfNewArray(dexItemFactory.intArrayType),
              new CfStore(ValueType.OBJECT, resultLocalSlot),
              // System.arraycopy(SharedUtilityClass.$VALUES, 0, result, 0, size);
              new CfFieldInstruction(Opcodes.GETSTATIC, valuesField.getReference()),
              new CfConstNumber(0, ValueType.INT),
              new CfLoad(ValueType.OBJECT, resultLocalSlot),
              new CfConstNumber(0, ValueType.INT),
              new CfLoad(ValueType.INT, argumentLocalSlot),
              new CfInvoke(
                  Opcodes.INVOKESTATIC, dexItemFactory.javaLangSystemMethods.arraycopy, false),
              // return result
              new CfLoad(ValueType.OBJECT, resultLocalSlot),
              new CfReturn(ValueType.OBJECT)),
          Collections.emptyList(),
          Collections.emptyList());
    }

    private static DexProgramClass findDeterministicContextType(Set<DexProgramClass> contexts) {
      DexProgramClass deterministicContext = null;
      for (DexProgramClass context : contexts) {
        if (deterministicContext == null) {
          deterministicContext = context;
        } else if (context.type.compareTo(deterministicContext.type) < 0) {
          deterministicContext = context;
        }
      }
      return deterministicContext;
    }
  }
}
