// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.ArrayAccess;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.android.tools.r8.ir.synthetic.EnumUnboxingCfCodeProvider;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class EnumUnboxingRewriter {

  public static final String ENUM_UNBOXING_UTILITY_METHOD_PREFIX = "$enumboxing$";
  private static final CfVersion REQUIRED_CLASS_FILE_VERSION = CfVersion.V1_8;

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  private final EnumDataMap unboxedEnumsData;
  private final UnboxedEnumMemberRelocator relocator;
  private EnumUnboxingLens enumUnboxingLens;

  private final Map<DexMethod, DexEncodedMethod> utilityMethods = new ConcurrentHashMap<>();

  private final DexMethod ordinalUtilityMethod;
  private final DexMethod equalsUtilityMethod;
  private final DexMethod compareToUtilityMethod;
  private final DexMethod valuesUtilityMethod;
  private final DexMethod zeroCheckMethod;
  private final DexMethod zeroCheckMessageMethod;

  EnumUnboxingRewriter(
      AppView<AppInfoWithLiveness> appView,
      EnumDataMap unboxedEnumsInstanceFieldData,
      UnboxedEnumMemberRelocator relocator) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.unboxedEnumsData = unboxedEnumsInstanceFieldData;
    this.relocator = relocator;

    // Custom methods for java.lang.Enum methods ordinal, equals and compareTo.
    DexType defaultEnumUnboxingUtility = relocator.getDefaultEnumUnboxingUtility();
    this.ordinalUtilityMethod =
        factory.createMethod(
            defaultEnumUnboxingUtility,
            factory.createProto(factory.intType, factory.intType),
            ENUM_UNBOXING_UTILITY_METHOD_PREFIX + "ordinal");
    this.equalsUtilityMethod =
        factory.createMethod(
            defaultEnumUnboxingUtility,
            factory.createProto(factory.booleanType, factory.intType, factory.intType),
            ENUM_UNBOXING_UTILITY_METHOD_PREFIX + "equals");
    this.compareToUtilityMethod =
        factory.createMethod(
            defaultEnumUnboxingUtility,
            factory.createProto(factory.intType, factory.intType, factory.intType),
            ENUM_UNBOXING_UTILITY_METHOD_PREFIX + "compareTo");
    // Custom methods for generated field $VALUES initialization.
    this.valuesUtilityMethod =
        factory.createMethod(
            defaultEnumUnboxingUtility,
            factory.createProto(factory.intArrayType, factory.intType),
            ENUM_UNBOXING_UTILITY_METHOD_PREFIX + "values");
    // Custom methods for Object#getClass without outValue and Objects.requireNonNull.
    this.zeroCheckMethod =
        factory.createMethod(
            defaultEnumUnboxingUtility,
            factory.createProto(factory.voidType, factory.intType),
            ENUM_UNBOXING_UTILITY_METHOD_PREFIX + "zeroCheck");
    this.zeroCheckMessageMethod =
        factory.createMethod(
            defaultEnumUnboxingUtility,
            factory.createProto(factory.voidType, factory.intType, factory.stringType),
            ENUM_UNBOXING_UTILITY_METHOD_PREFIX + "zeroCheckMessage");
  }

  public void setEnumUnboxingLens(EnumUnboxingLens enumUnboxingLens) {
    this.enumUnboxingLens = enumUnboxingLens;
  }

  Set<Phi> rewriteCode(IRCode code) {
    // We should not process the enum methods, they will be removed and they may contain invalid
    // rewriting rules.
    if (unboxedEnumsData.isEmpty()) {
      return Sets.newIdentityHashSet();
    }
    assert code.isConsistentSSABeforeTypesAreCorrect();
    ProgramMethod context = code.context();
    Map<Instruction, DexType> convertedEnums = new IdentityHashMap<>();
    Set<Phi> affectedPhis = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blocks = code.listIterator();
    Value zeroConstValue = null;
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      zeroConstValue = fixNullsInBlockPhis(code, block, zeroConstValue);
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        // Rewrites specific enum methods, such as ordinal, into their corresponding enum unboxed
        // counterpart. The rewriting (== or match) is based on the following:
        // - name, ordinal and compareTo are final and implemented only on java.lang.Enum,
        // - equals, hashCode are final and implemented in java.lang.Enum and java.lang.Object,
        // - getClass is final and implemented only in java.lang.Object,
        // - toString is non-final, implemented in java.lang.Object, java.lang.Enum and possibly
        //   also in the unboxed enum class.
        if (instruction.isInvokeMethodWithReceiver()) {
          InvokeMethodWithReceiver invokeMethod = instruction.asInvokeMethodWithReceiver();
          DexType enumType = getEnumTypeOrNull(invokeMethod.getReceiver(), convertedEnums);
          if (enumType != null) {
            DexMethod invokedMethod = invokeMethod.getInvokedMethod();
            if (invokedMethod == factory.enumMembers.ordinalMethod
                || invokedMethod.match(factory.enumMembers.hashCode)) {
              replaceEnumInvoke(
                  iterator, invokeMethod, ordinalUtilityMethod, m -> synthesizeOrdinalMethod());
              continue;
            } else if (invokedMethod.match(factory.enumMembers.equals)) {
              replaceEnumInvoke(
                  iterator, invokeMethod, equalsUtilityMethod, m -> synthesizeEqualsMethod());
              continue;
            } else if (invokedMethod == factory.enumMembers.compareTo) {
              replaceEnumInvoke(
                  iterator, invokeMethod, compareToUtilityMethod, m -> synthesizeCompareToMethod());
              continue;
            } else if (invokedMethod == factory.enumMembers.nameMethod) {
              rewriteNameMethod(iterator, invokeMethod, enumType);
              continue;
            } else if (invokedMethod.match(factory.enumMembers.toString)) {
              DexMethod lookupMethod = enumUnboxingLens.lookupMethod(invokedMethod);
              // If the lookupMethod is different, then a toString method was on the enumType
              // class, which was moved, and the lens code rewriter will rewrite the invoke to
              // that method.
              if (invokeMethod.isInvokeSuper() || lookupMethod == invokedMethod) {
                rewriteNameMethod(iterator, invokeMethod, enumType);
                continue;
              }
            } else if (invokedMethod == factory.objectMembers.getClass) {
              assert !invokeMethod.hasOutValue() || !invokeMethod.outValue().hasAnyUsers();
              replaceEnumInvoke(
                  iterator, invokeMethod, zeroCheckMethod, m -> synthesizeZeroCheckMethod());
            }
          }
        } else if (instruction.isInvokeStatic()) {
          InvokeStatic invokeStatic = instruction.asInvokeStatic();
          DexClassAndMethod singleTarget = invokeStatic.lookupSingleTarget(appView, context);
          if (singleTarget == null) {
            continue;
          }
          DexMethod invokedMethod = singleTarget.getReference();
          if (invokedMethod == factory.enumMembers.valueOf
              && invokeStatic.getArgument(0).isConstClass()) {
            DexType enumType =
                invokeStatic.getArgument(0).getConstInstruction().asConstClass().getValue();
            if (unboxedEnumsData.isUnboxedEnum(enumType)) {
              DexMethod valueOfMethod = computeValueOfUtilityMethod(enumType);
              Value outValue = invokeStatic.outValue();
              Value rewrittenOutValue = null;
              if (outValue != null) {
                rewrittenOutValue = code.createValue(TypeElement.getInt());
                affectedPhis.addAll(outValue.uniquePhiUsers());
              }
              InvokeStatic invoke =
                  new InvokeStatic(
                      valueOfMethod,
                      rewrittenOutValue,
                      Collections.singletonList(invokeStatic.inValues().get(1)));
              iterator.replaceCurrentInstruction(invoke);
              convertedEnums.put(invoke, enumType);
              continue;
            }
          } else if (invokedMethod == factory.javaLangSystemMethods.identityHashCode) {
            assert invokeStatic.arguments().size() == 1;
            Value argument = invokeStatic.getArgument(0);
            DexType enumType = getEnumTypeOrNull(argument, convertedEnums);
            if (enumType != null) {
              invokeStatic.outValue().replaceUsers(argument);
              iterator.removeOrReplaceByDebugLocalRead();
            }
          } else if (invokedMethod == factory.stringMembers.valueOf) {
            assert invokeStatic.arguments().size() == 1;
            Value argument = invokeStatic.getArgument(0);
            DexType enumType = getEnumTypeOrNull(argument, convertedEnums);
            if (enumType != null) {
              DexMethod stringValueOfMethod = computeStringValueOfUtilityMethod(enumType);
              iterator.replaceCurrentInstruction(
                  new InvokeStatic(
                      stringValueOfMethod, invokeStatic.outValue(), invokeStatic.arguments()));
              continue;
            }
          } else if (invokedMethod == factory.objectsMethods.requireNonNull) {
            assert invokeStatic.arguments().size() == 1;
            Value argument = invokeStatic.getArgument(0);
            DexType enumType = getEnumTypeOrNull(argument, convertedEnums);
            if (enumType != null) {
              replaceEnumInvoke(
                  iterator, invokeStatic, zeroCheckMethod, m -> synthesizeZeroCheckMethod());
            }
          } else if (invokedMethod == factory.objectsMethods.requireNonNullWithMessage) {
            assert invokeStatic.arguments().size() == 2;
            Value argument = invokeStatic.getArgument(0);
            DexType enumType = getEnumTypeOrNull(argument, convertedEnums);
            if (enumType != null) {
              replaceEnumInvoke(
                  iterator,
                  invokeStatic,
                  zeroCheckMessageMethod,
                  m -> synthesizeZeroCheckMessageMethod());
            }
          }
        }
        if (instruction.isStaticGet()) {
          StaticGet staticGet = instruction.asStaticGet();
          DexField field = staticGet.getField();
          DexType holder = field.holder;
          if (unboxedEnumsData.isUnboxedEnum(holder)) {
            if (staticGet.outValue() == null) {
              iterator.removeOrReplaceByDebugLocalRead();
              continue;
            }
            affectedPhis.addAll(staticGet.outValue().uniquePhiUsers());
            if (unboxedEnumsData.matchesValuesField(field)) {
              utilityMethods.computeIfAbsent(
                  valuesUtilityMethod, m -> synthesizeValuesUtilityMethod());
              DexField fieldValues = createValuesField(holder);
              DexMethod methodValues = createValuesMethod(holder);
              utilityMethods.computeIfAbsent(
                  methodValues,
                  m ->
                      computeValuesEncodedMethod(
                          m, fieldValues, unboxedEnumsData.getValuesSize(holder)));
              Value rewrittenOutValue =
                  code.createValue(
                      ArrayTypeElement.create(TypeElement.getInt(), definitelyNotNull()));
              InvokeStatic invoke =
                  new InvokeStatic(methodValues, rewrittenOutValue, ImmutableList.of());
              iterator.replaceCurrentInstruction(invoke);
              convertedEnums.put(invoke, holder);
            } else {
              // Replace by ordinal + 1 for null check (null is 0).
              assert unboxedEnumsData.hasUnboxedValueFor(field)
                  : "Invalid read to " + field.name + ", error during enum analysis";
              ConstNumber intConstant =
                  code.createIntConstant(unboxedEnumsData.getUnboxedValue(field));
              iterator.replaceCurrentInstruction(intConstant);
              convertedEnums.put(intConstant, holder);
            }
          }
        }

        if (instruction.isInstanceGet()) {
          InstanceGet instanceGet = instruction.asInstanceGet();
          DexType holder = instanceGet.getField().holder;
          if (unboxedEnumsData.isUnboxedEnum(holder)) {
            DexMethod fieldMethod = computeInstanceFieldMethod(instanceGet.getField());
            Value rewrittenOutValue =
                code.createValue(
                    TypeElement.fromDexType(
                        fieldMethod.proto.returnType, Nullability.maybeNull(), appView));
            InvokeStatic invoke =
                new InvokeStatic(
                    fieldMethod, rewrittenOutValue, ImmutableList.of(instanceGet.object()));
            iterator.replaceCurrentInstruction(invoke);
            if (unboxedEnumsData.isUnboxedEnum(instanceGet.getField().type)) {
              convertedEnums.put(invoke, instanceGet.getField().type);
            }
          }
        }

        // Rewrite array accesses from MyEnum[] (OBJECT) to int[] (INT).
        if (instruction.isArrayAccess()) {
          ArrayAccess arrayAccess = instruction.asArrayAccess();
          DexType enumType = getEnumTypeOrNull(arrayAccess);
          if (enumType != null) {
            if (arrayAccess.hasOutValue()) {
              affectedPhis.addAll(arrayAccess.outValue().uniquePhiUsers());
            }
            instruction = arrayAccess.withMemberType(MemberType.INT);
            iterator.replaceCurrentInstruction(instruction);
            convertedEnums.put(instruction, enumType);
          }
          assert validateArrayAccess(arrayAccess);
        }
      }
    }
    assert code.isConsistentSSABeforeTypesAreCorrect();
    return affectedPhis;
  }

  private void rewriteNameMethod(
      InstructionListIterator iterator, InvokeMethodWithReceiver invokeMethod, DexType enumType) {
    DexMethod toStringMethod =
        computeInstanceFieldUtilityMethod(enumType, factory.enumMembers.nameField);
    iterator.replaceCurrentInstruction(
        new InvokeStatic(toStringMethod, invokeMethod.outValue(), invokeMethod.arguments()));
  }

  private Value fixNullsInBlockPhis(IRCode code, BasicBlock block, Value zeroConstValue) {
    for (Phi phi : block.getPhis()) {
      if (getEnumTypeOrNull(phi.getType()) != null) {
        for (int i = 0; i < phi.getOperands().size(); i++) {
          Value operand = phi.getOperand(i);
          if (operand.getType().isNullType()) {
            if (zeroConstValue == null) {
              zeroConstValue = insertConstZero(code);
            }
            phi.replaceOperandAt(i, zeroConstValue);
          }
        }
      }
    }
    return zeroConstValue;
  }

  private Value insertConstZero(IRCode code) {
    InstructionListIterator iterator = code.entryBlock().listIterator(code);
    while (iterator.hasNext() && iterator.peekNext().isArgument()) {
      iterator.next();
    }
    return iterator.insertConstNumberInstruction(code, appView.options(), 0, TypeElement.getInt());
  }

  private DexMethod computeInstanceFieldMethod(DexField field) {
    EnumInstanceFieldKnownData enumFieldKnownData =
        unboxedEnumsData.getInstanceFieldData(field.holder, field);
    if (enumFieldKnownData.isOrdinal()) {
      utilityMethods.computeIfAbsent(ordinalUtilityMethod, m -> synthesizeOrdinalMethod());
      return ordinalUtilityMethod;
    }
    return computeInstanceFieldUtilityMethod(field.holder, field);
  }

  private void replaceEnumInvoke(
      InstructionListIterator iterator,
      InvokeMethod invoke,
      DexMethod method,
      Function<DexMethod, DexEncodedMethod> synthesizor) {
    utilityMethods.computeIfAbsent(method, synthesizor);
    InvokeStatic replacement =
        new InvokeStatic(
            method, invoke.hasUnusedOutValue() ? null : invoke.outValue(), invoke.arguments());
    assert !replacement.hasOutValue()
        || !replacement.getInvokedMethod().getReturnType().isVoidType();
    iterator.replaceCurrentInstruction(replacement);
  }

  private boolean validateArrayAccess(ArrayAccess arrayAccess) {
    ArrayTypeElement arrayType = arrayAccess.array().getType().asArrayType();
    if (arrayType == null) {
      assert arrayAccess.array().getType().isNullType();
      return true;
    }
    assert arrayAccess.getMemberType() != MemberType.OBJECT
        || arrayType.getNesting() > 1
        || arrayType.getBaseType().isReferenceType();
    return true;
  }

  private DexType getEnumTypeOrNull(Value receiver, Map<Instruction, DexType> convertedEnums) {
    TypeElement type = receiver.getType();
    if (type.isInt()) {
      return convertedEnums.get(receiver.definition);
    }
    return getEnumTypeOrNull(type);
  }

  private DexType getEnumTypeOrNull(TypeElement type) {
    if (!type.isClassType()) {
      return null;
    }
    DexType enumType = type.asClassType().getClassType();
    return unboxedEnumsData.isUnboxedEnum(enumType) ? enumType : null;
  }

  public static String compatibleName(DexType type) {
    return type.toSourceString().replace('.', '$');
  }

  private DexField createValuesField(DexType enumType) {
    return createValuesField(enumType, relocator.getNewMemberLocationFor(enumType), factory);
  }

  static DexField createValuesField(
      DexType enumType, DexType enumUtilityClass, DexItemFactory dexItemFactory) {
    return dexItemFactory.createField(
        enumUtilityClass,
        dexItemFactory.intArrayType,
        "$$values$$field$" + compatibleName(enumType));
  }

  private DexMethod createValuesMethod(DexType enumType) {
    return factory.createMethod(
        relocator.getNewMemberLocationFor(enumType),
        factory.createProto(factory.intArrayType),
        "$$values$$method$" + compatibleName(enumType));
  }

  private DexEncodedMethod computeValuesEncodedMethod(
      DexMethod method, DexField fieldValues, int numEnumInstances) {
    CfCode cfCode =
        new EnumUnboxingCfCodeProvider.EnumUnboxingValuesCfCodeProvider(
                appView, method.holder, fieldValues, numEnumInstances, valuesUtilityMethod)
            .generateCfCode();
    return synthesizeUtilityMethod(cfCode, method, true);
  }

  private DexMethod computeInstanceFieldUtilityMethod(DexType enumType, DexField field) {
    assert unboxedEnumsData.isUnboxedEnum(enumType);
    assert field.holder == enumType || field.holder == factory.enumType;
    String methodName =
        "get"
            + (enumType == field.holder ? "" : "Enum$")
            + field.name
            + "$$"
            + compatibleName(enumType);
    DexMethod fieldMethod =
        factory.createMethod(
            relocator.getNewMemberLocationFor(enumType),
            factory.createProto(field.type, factory.intType),
            methodName);
    utilityMethods.computeIfAbsent(
        fieldMethod, m -> synthesizeInstanceFieldMethod(m, enumType, field, null));
    return fieldMethod;
  }

  private DexMethod computeStringValueOfUtilityMethod(DexType enumType) {
    // TODO(b/167994636): remove duplication between instance field name read and this method.
    assert unboxedEnumsData.isUnboxedEnum(enumType);
    String methodName = "string$valueOf$" + compatibleName(enumType);
    DexMethod fieldMethod =
        factory.createMethod(
            relocator.getNewMemberLocationFor(enumType),
            factory.createProto(factory.stringType, factory.intType),
            methodName);
    AbstractValue nullString =
        appView.abstractValueFactory().createSingleStringValue(factory.createString("null"));
    utilityMethods.computeIfAbsent(
        fieldMethod,
        m -> synthesizeInstanceFieldMethod(m, enumType, factory.enumMembers.nameField, nullString));
    return fieldMethod;
  }

  private DexMethod computeValueOfUtilityMethod(DexType enumType) {
    assert unboxedEnumsData.isUnboxedEnum(enumType);
    DexMethod valueOf =
        factory.createMethod(
            relocator.getNewMemberLocationFor(enumType),
            factory.createProto(factory.intType, factory.stringType),
            "valueOf" + compatibleName(enumType));
    utilityMethods.computeIfAbsent(valueOf, m -> synthesizeValueOfUtilityMethod(m, enumType));
    return valueOf;
  }

  private DexType getEnumTypeOrNull(ArrayAccess arrayAccess) {
    ArrayTypeElement arrayType = arrayAccess.array().getType().asArrayType();
    if (arrayType == null) {
      assert arrayAccess.array().getType().isNullType();
      return null;
    }
    if (arrayType.getNesting() != 1) {
      return null;
    }
    TypeElement baseType = arrayType.getBaseType();
    if (!baseType.isClassType()) {
      return null;
    }
    DexType classType = baseType.asClassType().getClassType();
    return unboxedEnumsData.isUnboxedEnum(classType) ? classType : null;
  }

  void synthesizeEnumUnboxingUtilityMethods(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    // Append to the various utility classes, in deterministic order, the utility methods and
    // fields required.
    Map<DexType, List<DexEncodedMethod>> methodMap = triageEncodedMembers(utilityMethods.values());
    if (methodMap.isEmpty()) {
      return;
    }
    SortedProgramMethodSet wave = SortedProgramMethodSet.create();
    methodMap.forEach(
        (type, methodsSorted) -> {
          DexProgramClass utilityClass = appView.definitionFor(type).asProgramClass();
          assert utilityClass != null;
          utilityClass.addDirectMethods(methodsSorted);
          for (DexEncodedMethod dexEncodedMethod : methodsSorted) {
            wave.add(new ProgramMethod(utilityClass, dexEncodedMethod));
          }
        });
    converter.processMethodsConcurrently(wave, executorService);
  }

  <R extends DexMember<T, R>, T extends DexEncodedMember<T, R>>
      Map<DexType, List<T>> triageEncodedMembers(Collection<T> encodedMembers) {
    if (encodedMembers.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<DexType, List<T>> encodedMembersMap = new IdentityHashMap<>();
    // We compute encodedMembers by types.
    for (T encodedMember : encodedMembers) {
      List<T> members =
          encodedMembersMap.computeIfAbsent(
              encodedMember.getHolderType(), ignored -> new ArrayList<>());
      members.add(encodedMember);
    }
    // We make the order deterministic.
    for (List<T> value : encodedMembersMap.values()) {
      value.sort((m1, m2) -> m1.getReference().compareTo(m2.getReference()));
    }
    return encodedMembersMap;
  }

  private DexEncodedMethod synthesizeInstanceFieldMethod(
      DexMethod method, DexType enumType, DexField field, AbstractValue nullValue) {
    assert method.proto.returnType == field.type;
    assert unboxedEnumsData.getInstanceFieldData(enumType, field).isMapping();
    CfCode cfCode =
        new EnumUnboxingCfCodeProvider.EnumUnboxingInstanceFieldCfCodeProvider(
                appView,
                method.holder,
                field.type,
                unboxedEnumsData.getInstanceFieldData(enumType, field).asEnumFieldMappingData(),
                nullValue)
            .generateCfCode();
    return synthesizeUtilityMethod(cfCode, method, false);
  }

  private DexEncodedMethod synthesizeValueOfUtilityMethod(DexMethod method, DexType enumType) {
    assert method.proto.returnType == factory.intType;
    assert unboxedEnumsData
        .getInstanceFieldData(enumType, factory.enumMembers.nameField)
        .isMapping();
    CfCode cfCode =
        new EnumUnboxingCfCodeProvider.EnumUnboxingValueOfCfCodeProvider(
                appView,
                method.holder,
                enumType,
                unboxedEnumsData
                    .getInstanceFieldData(enumType, factory.enumMembers.nameField)
                    .asEnumFieldMappingData())
            .generateCfCode();
    return synthesizeUtilityMethod(cfCode, method, false);
  }

  private DexEncodedMethod synthesizeZeroCheckMethod() {
    CfCode cfCode =
        EnumUnboxingCfMethods.EnumUnboxingMethods_zeroCheck(appView.options(), zeroCheckMethod);
    return synthesizeUtilityMethod(cfCode, zeroCheckMethod, false);
  }

  private DexEncodedMethod synthesizeZeroCheckMessageMethod() {
    CfCode cfCode =
        EnumUnboxingCfMethods.EnumUnboxingMethods_zeroCheckMessage(
            appView.options(), zeroCheckMessageMethod);
    return synthesizeUtilityMethod(cfCode, zeroCheckMessageMethod, false);
  }

  private DexEncodedMethod synthesizeOrdinalMethod() {
    CfCode cfCode =
        EnumUnboxingCfMethods.EnumUnboxingMethods_ordinal(appView.options(), ordinalUtilityMethod);
    return synthesizeUtilityMethod(cfCode, ordinalUtilityMethod, false);
  }

  private DexEncodedMethod synthesizeEqualsMethod() {
    CfCode cfCode =
        EnumUnboxingCfMethods.EnumUnboxingMethods_equals(appView.options(), equalsUtilityMethod);
    return synthesizeUtilityMethod(cfCode, equalsUtilityMethod, false);
  }

  private DexEncodedMethod synthesizeCompareToMethod() {
    CfCode cfCode =
        EnumUnboxingCfMethods.EnumUnboxingMethods_compareTo(
            appView.options(), compareToUtilityMethod);
    return synthesizeUtilityMethod(cfCode, compareToUtilityMethod, false);
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
        MethodTypeSignature.noSignature(),
        DexAnnotationSet.empty(),
        ParameterAnnotationsList.empty(),
        cfCode,
        true,
        REQUIRED_CLASS_FILE_VERSION);
  }

  private MethodAccessFlags synthesizedMethodAccessFlags(boolean sync) {
    int access = Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC | Constants.ACC_STATIC;
    if (sync) {
      access = access | Constants.ACC_SYNCHRONIZED;
    }
    return MethodAccessFlags.fromSharedAccessFlags(access, false);
  }
}
