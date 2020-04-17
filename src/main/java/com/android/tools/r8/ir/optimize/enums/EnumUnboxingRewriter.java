// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfo;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayAccess;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.synthetic.EnumUnboxingCfCodeProvider;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class EnumUnboxingRewriter {

  public static final String ENUM_UNBOXING_UTILITY_CLASS_NAME = "$r8$EnumUnboxingUtility";
  public static final String ENUM_UNBOXING_UTILITY_ORDINAL = "$enumboxing$ordinal";
  private static final String ENUM_UNBOXING_UTILITY_VALUES = "$enumboxing$values";
  private static final int REQUIRED_CLASS_FILE_VERSION = 52;

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  private final EnumValueInfoMapCollection enumsToUnbox;
  private final Map<DexMethod, DexEncodedMethod> extraUtilityMethods = new ConcurrentHashMap<>();
  private final Map<DexField, DexEncodedField> extraUtilityFields = new ConcurrentHashMap<>();

  private final DexType utilityClassType;
  private final DexMethod ordinalUtilityMethod;
  private final DexMethod valuesUtilityMethod;

  private boolean requiresOrdinalUtilityMethod = false;
  private boolean requiresValuesUtilityMethod = false;

  EnumUnboxingRewriter(AppView<AppInfoWithLiveness> appView, Set<DexType> enumsToUnbox) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    EnumValueInfoMapCollection.Builder builder = EnumValueInfoMapCollection.builder();
    for (DexType toUnbox : enumsToUnbox) {
      builder.put(toUnbox, appView.appInfo().withLiveness().getEnumValueInfoMap(toUnbox));
    }
    this.enumsToUnbox = builder.build();

    this.utilityClassType = factory.enumUnboxingUtilityType;
    this.ordinalUtilityMethod =
        factory.createMethod(
            utilityClassType,
            factory.createProto(factory.intType, factory.intType),
            ENUM_UNBOXING_UTILITY_ORDINAL);
    this.valuesUtilityMethod =
        factory.createMethod(
            utilityClassType,
            factory.createProto(factory.intArrayType, factory.intType),
            ENUM_UNBOXING_UTILITY_VALUES);
  }

  public EnumValueInfoMapCollection getEnumsToUnbox() {
    return enumsToUnbox;
  }

  Set<Phi> rewriteCode(IRCode code) {
    // We should not process the enum methods, they will be removed and they may contain invalid
    // rewriting rules.
    if (enumsToUnbox.isEmpty()) {
      return Sets.newIdentityHashSet();
    }
    assert code.isConsistentSSABeforeTypesAreCorrect();
    Set<Phi> affectedPhis = Sets.newIdentityHashSet();
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      // Rewrites specific enum methods, such as ordinal, into their corresponding enum unboxed
      // counterpart.
      if (instruction.isInvokeMethodWithReceiver()) {
        InvokeMethodWithReceiver invokeMethod = instruction.asInvokeMethodWithReceiver();
        DexMethod invokedMethod = invokeMethod.getInvokedMethod();
        if (invokedMethod == factory.enumMethods.ordinal
            && isEnumToUnboxOrInt(invokeMethod.getReceiver().getType())) {
          instruction =
              new InvokeStatic(
                  ordinalUtilityMethod, invokeMethod.outValue(), invokeMethod.inValues());
          iterator.replaceCurrentInstruction(instruction);
          requiresOrdinalUtilityMethod = true;
          continue;
        }
        // TODO(b/147860220): rewrite also other enum methods.
      } else if (instruction.isInvokeStatic()) {
        InvokeStatic invokeStatic = instruction.asInvokeStatic();
        DexMethod invokedMethod = invokeStatic.getInvokedMethod();
        if (invokedMethod == factory.enumMethods.valueOf
            && invokeStatic.inValues().get(0).isConstClass()) {
          DexType enumType =
              invokeStatic.inValues().get(0).getConstInstruction().asConstClass().getValue();
          if (enumsToUnbox.containsEnum(enumType)) {
            DexMethod valueOfMethod = computeValueOfUtilityMethod(enumType);
            Value outValue = invokeStatic.outValue();
            Value rewrittenOutValue = null;
            if (outValue != null) {
              rewrittenOutValue = code.createValue(TypeElement.getInt());
              affectedPhis.addAll(outValue.uniquePhiUsers());
            }
            iterator.replaceCurrentInstruction(
                new InvokeStatic(
                    valueOfMethod,
                    rewrittenOutValue,
                    Collections.singletonList(invokeStatic.inValues().get(1))));
            continue;
          }
        }
      }
      // Rewrites direct access to enum values into the corresponding int, $VALUES is not
      // supported.
      if (instruction.isStaticGet()) {
        StaticGet staticGet = instruction.asStaticGet();
        DexType holder = staticGet.getField().holder;
        if (enumsToUnbox.containsEnum(holder)) {
          if (staticGet.outValue() == null) {
            iterator.removeInstructionIgnoreOutValue();
            continue;
          }
          EnumValueInfoMap enumValueInfoMap = enumsToUnbox.getEnumValueInfoMap(holder);
          assert enumValueInfoMap != null;
          affectedPhis.addAll(staticGet.outValue().uniquePhiUsers());
          EnumValueInfo enumValueInfo = enumValueInfoMap.getEnumValueInfo(staticGet.getField());
          if (enumValueInfo == null && staticGet.getField().name == factory.enumValuesFieldName) {
            requiresValuesUtilityMethod = true;
            DexField fieldValues = createValuesField(holder);
            extraUtilityFields.computeIfAbsent(fieldValues, this::computeValuesEncodedField);
            DexMethod methodValues = createValuesMethod(holder);
            extraUtilityMethods.computeIfAbsent(
                methodValues,
                m -> computeValuesEncodedMethod(m, fieldValues, enumValueInfoMap.size()));
            Value rewrittenOutValue =
                code.createValue(
                    ArrayTypeElement.create(TypeElement.getInt(), definitelyNotNull()));
            iterator.replaceCurrentInstruction(
                new InvokeStatic(methodValues, rewrittenOutValue, ImmutableList.of()));
          } else {
            // Replace by ordinal + 1 for null check (null is 0).
            assert enumValueInfo != null
                : "Invalid read to " + staticGet.getField().name + ", error during enum analysis";
            iterator.replaceCurrentInstruction(
                code.createIntConstant(enumValueInfo.convertToInt()));
          }
        }
      }
      // Rewrite array accesses from MyEnum[] (OBJECT) to int[] (INT).
      if (instruction.isArrayAccess()) {
        ArrayAccess arrayAccess = instruction.asArrayAccess();
        if (shouldRewriteArrayAccess(arrayAccess)) {
          instruction = arrayAccess.withMemberType(MemberType.INT);
          iterator.replaceCurrentInstruction(instruction);
        }
        assert validateArrayAccess(instruction.asArrayAccess());
      }
    }
    assert code.isConsistentSSABeforeTypesAreCorrect();
    return affectedPhis;
  }

  private boolean validateArrayAccess(ArrayAccess arrayAccess) {
    ArrayTypeElement arrayType = arrayAccess.array().getType().asArrayType();
    assert arrayAccess.getMemberType() != MemberType.OBJECT
        || arrayType.getNesting() > 1
        || arrayType.getBaseType().isReferenceType();
    return true;
  }

  private boolean isEnumToUnboxOrInt(TypeElement type) {
    if (type.isInt()) {
      return true;
    }
    if (!type.isClassType()) {
      return false;
    }
    return enumsToUnbox.containsEnum(type.asClassType().getClassType());
  }

  private String compatibleName(DexType type) {
    return type.toSourceString().replace('.', '$');
  }

  private DexField createValuesField(DexType type) {
    return factory.createField(
        utilityClassType,
        factory.intArrayType,
        factory.enumValuesFieldName + "$field$" + compatibleName(type));
  }

  private DexEncodedField computeValuesEncodedField(DexField field) {
    return new DexEncodedField(
        field,
        FieldAccessFlags.fromSharedAccessFlags(
            Constants.ACC_SYNTHETIC | Constants.ACC_STATIC | Constants.ACC_PUBLIC),
        DexAnnotationSet.empty(),
        null);
  }

  private DexMethod createValuesMethod(DexType type) {
    return factory.createMethod(
        utilityClassType,
        factory.createProto(factory.intArrayType),
        factory.enumValuesFieldName + "$method$" + compatibleName(type));
  }

  private DexEncodedMethod computeValuesEncodedMethod(
      DexMethod method, DexField fieldValues, int numEnumInstances) {
    CfCode cfCode =
        new EnumUnboxingCfCodeProvider.EnumUnboxingValuesCfCodeProvider(
                appView, utilityClassType, fieldValues, numEnumInstances, valuesUtilityMethod)
            .generateCfCode();
    return synthesizeUtilityMethod(cfCode, method, true);
  }

  private DexMethod computeValueOfUtilityMethod(DexType type) {
    assert enumsToUnbox.containsEnum(type);
    DexMethod valueOf =
        factory.createMethod(
            utilityClassType,
            factory.createProto(factory.intType, factory.stringType),
            "valueOf" + compatibleName(type));
    extraUtilityMethods.computeIfAbsent(valueOf, m -> synthesizeValueOfUtilityMethod(m, type));
    return valueOf;
  }

  private boolean shouldRewriteArrayAccess(ArrayAccess arrayAccess) {
    ArrayTypeElement arrayType = arrayAccess.array().getType().asArrayType();
    if (arrayType == null) {
      assert arrayAccess.array().getType().isNullType();
      return false;
    }
    if (arrayType.getNesting() != 1) {
      return false;
    }
    TypeElement baseType = arrayType.getBaseType();
    return baseType.isClassType()
        && enumsToUnbox.containsEnum(baseType.asClassType().getClassType());
  }

  // TODO(b/150172351): Synthesize the utility class upfront in the enqueuer.
  void synthesizeEnumUnboxingUtilityClass(
      DexApplication.Builder<?> appBuilder, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    // Synthesize a class which holds various utility methods that may be called from the IR
    // rewriting. If any of these methods are not used, they will be removed by the Enqueuer.
    List<DexEncodedMethod> requiredMethods = new ArrayList<>(extraUtilityMethods.values());
    // Sort for deterministic order.
    requiredMethods.sort((m1, m2) -> m1.method.name.slowCompareTo(m2.method.name));
    if (requiresOrdinalUtilityMethod) {
      requiredMethods.add(synthesizeOrdinalMethod());
    }
    if (requiresValuesUtilityMethod) {
      requiredMethods.add(synthesizeValuesUtilityMethod());
    }
    // TODO(b/147860220): synthesize also other enum methods.
    if (requiredMethods.isEmpty()) {
      return;
    }
    DexEncodedField[] fields = extraUtilityFields.values().toArray(DexEncodedField.EMPTY_ARRAY);
    Arrays.sort(fields, (f1, f2) -> f1.field.name.slowCompareTo(f2.field.name));
    DexProgramClass utilityClass =
        new DexProgramClass(
            utilityClassType,
            null,
            new SynthesizedOrigin("EnumUnboxing ", getClass()),
            ClassAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC),
            factory.objectType,
            DexTypeList.empty(),
            factory.createString("enumunboxing"),
            null,
            Collections.emptyList(),
            null,
            Collections.emptyList(),
            DexAnnotationSet.empty(),
            fields,
            DexEncodedField.EMPTY_ARRAY,
            // All synthesized methods are static in this case.
            requiredMethods.toArray(DexEncodedMethod.EMPTY_ARRAY),
            DexEncodedMethod.EMPTY_ARRAY,
            factory.getSkipNameValidationForTesting(),
            DexProgramClass::checksumFromType);
    appBuilder.addSynthesizedClass(utilityClass, utilityClassInMainDexList());
    appView.appInfo().addSynthesizedClass(utilityClass);
    converter.optimizeSynthesizedClass(utilityClass, executorService);
  }

  private DexEncodedMethod synthesizeValueOfUtilityMethod(DexMethod method, DexType enumType) {
    CfCode cfCode =
        new EnumUnboxingCfCodeProvider.EnumUnboxingValueOfCfCodeProvider(
                appView, utilityClassType, enumType, enumsToUnbox.getEnumValueInfoMap(enumType))
            .generateCfCode();
    return new DexEncodedMethod(
        method,
        synthesizedMethodAccessFlags(false),
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        cfCode,
        REQUIRED_CLASS_FILE_VERSION,
        true);
  }

  // TODO(b/150178516): Add a test for this case.
  private boolean utilityClassInMainDexList() {
    for (DexType toUnbox : enumsToUnbox.enumSet()) {
      if (appView.appInfo().isInMainDexList(toUnbox)) {
        return true;
      }
    }
    return false;
  }

  private DexEncodedMethod synthesizeOrdinalMethod() {
    CfCode cfCode =
        EnumUnboxingCfMethods.EnumUnboxingMethods_ordinal(appView.options(), ordinalUtilityMethod);
    return synthesizeUtilityMethod(cfCode, ordinalUtilityMethod, false);
  }

  private DexEncodedMethod synthesizeValuesUtilityMethod() {
    CfCode cfCode =
        EnumUnboxingCfMethods.EnumUnboxingMethods_values(appView.options(), valuesUtilityMethod);
    return synthesizeUtilityMethod(cfCode, valuesUtilityMethod, false);
  }

  private DexEncodedMethod synthesizeUtilityMethod(CfCode cfCode, DexMethod method, boolean sync) {
    return new DexEncodedMethod(
        method,
        synthesizedMethodAccessFlags(sync),
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        cfCode,
        REQUIRED_CLASS_FILE_VERSION,
        true);
  }

  private MethodAccessFlags synthesizedMethodAccessFlags(boolean sync) {
    int access = Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC | Constants.ACC_STATIC;
    if (sync) {
      access = access | Constants.ACC_SYNCHRONIZED;
    }
    return MethodAccessFlags.fromSharedAccessFlags(access, false);
  }
}
