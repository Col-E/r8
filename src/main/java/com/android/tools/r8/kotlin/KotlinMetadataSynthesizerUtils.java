// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.Kotlin.NAME;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToKotlinClassifier;
import static kotlinx.metadata.FlagsKt.flagsOf;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.ArrayTypeSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.TypeVariableSignature;
import com.android.tools.r8.kotlin.Kotlin.ClassClassifiers;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.KmVariance;

class KotlinMetadataSynthesizerUtils {

  // The KmVisitorOption is used for star projections, otherwise we will have late-init failures
  // due to visitStarProjection adding an argument.
  enum KmVisitorOption {
    VISIT_NEW,
    VISIT_PARENT
  }

  // The AddKotlinAnyType is carrying around information regarding the need for adding the trivial
  // type Kotlin/Any. The information is not consistently added, for example, for upper bounds
  // the trivial bound is not recorded.
  public enum AddKotlinAnyType {
    ADD,
    DISREGARD
  }

  static void populateKmTypeFromSignature(
      FieldTypeSignature typeSignature,
      KotlinTypeInfo originalTypeInfo,
      Function<KmVisitorOption, KmTypeVisitor> typeVisitor,
      List<KmTypeParameter> allTypeParameters,
      DexItemFactory factory,
      AddKotlinAnyType addAny) {
    if (typeSignature.isClassTypeSignature()) {
      populateKmTypeFromClassTypeSignature(
          typeSignature.asClassTypeSignature(),
          originalTypeInfo,
          typeVisitor,
          allTypeParameters,
          factory,
          addAny);
    } else if (typeSignature.isArrayTypeSignature()) {
      populateKmTypeFromArrayTypeSignature(
          typeSignature.asArrayTypeSignature(),
          originalTypeInfo,
          typeVisitor,
          allTypeParameters,
          factory,
          addAny);
    } else if (typeSignature.isTypeVariableSignature()) {
      populateKmTypeFromTypeVariableSignature(
          typeSignature.asTypeVariableSignature(), typeVisitor, allTypeParameters);
    } else {
      assert typeSignature.isStar();
      typeVisitor.apply(KmVisitorOption.VISIT_PARENT).visitStarProjection();
    }
  }

  private static void populateKmTypeFromTypeVariableSignature(
      TypeVariableSignature typeSignature,
      Function<KmVisitorOption, KmTypeVisitor> typeVisitor,
      List<KmTypeParameter> allTypeParameters) {
    for (KmTypeParameter typeParameter : allTypeParameters) {
      if (typeParameter
          .getName()
          .equals(typeSignature.asTypeVariableSignature().getTypeVariable())) {
        typeVisitor.apply(KmVisitorOption.VISIT_NEW).visitTypeParameter(typeParameter.getId());
        return;
      }
    }
  }

  private static void populateKmTypeFromArrayTypeSignature(
      ArrayTypeSignature typeSignature,
      KotlinTypeInfo originalTypeInfo,
      Function<KmVisitorOption, KmTypeVisitor> typeVisitor,
      List<KmTypeParameter> allTypeParameters,
      DexItemFactory factory,
      AddKotlinAnyType addAny) {
    ArrayTypeSignature arrayTypeSignature = typeSignature.asArrayTypeSignature();
    if (!arrayTypeSignature.elementSignature().isFieldTypeSignature()) {
      return;
    }
    KmTypeVisitor kmType = typeVisitor.apply(KmVisitorOption.VISIT_NEW);
    kmType.visitClass(ClassClassifiers.arrayBinaryName);
    KotlinTypeProjectionInfo projectionInfo =
        originalTypeInfo == null ? null : originalTypeInfo.getArgumentOrNull(0);
    populateKmTypeFromSignature(
        arrayTypeSignature.elementSignature().asFieldTypeSignature(),
        projectionInfo == null ? null : projectionInfo.typeInfo,
        (kmVisitorOption) -> {
          if (kmVisitorOption == KmVisitorOption.VISIT_PARENT) {
            assert originalTypeInfo.getArguments().size() == 1
                && originalTypeInfo.getArguments().get(0).isStarProjection();
            return kmType;
          } else {
            return kmType.visitArgument(
                flagsOf(), projectionInfo == null ? KmVariance.INVARIANT : projectionInfo.variance);
          }
        },
        allTypeParameters,
        factory,
        addAny);
  }

  private static void populateKmTypeFromClassTypeSignature(
      ClassTypeSignature typeSignature,
      KotlinTypeInfo originalTypeInfo,
      Function<KmVisitorOption, KmTypeVisitor> typeVisitor,
      List<KmTypeParameter> allTypeParameters,
      DexItemFactory factory,
      AddKotlinAnyType addAny) {
    // No need to record the trivial argument.
    if (addAny == AddKotlinAnyType.DISREGARD && factory.objectType == typeSignature.type()) {
      return;
    }
    KmTypeVisitor kmType = typeVisitor.apply(KmVisitorOption.VISIT_NEW);
    kmType.visitClass(toClassifier(typeSignature.type(), factory));
    for (int i = 0; i < typeSignature.typeArguments().size(); i++) {
      FieldTypeSignature typeArgument = typeSignature.typeArguments().get(i);
      KotlinTypeProjectionInfo projectionInfo =
          originalTypeInfo == null ? null : originalTypeInfo.getArgumentOrNull(i);
      populateKmTypeFromSignature(
          typeArgument,
          projectionInfo == null ? null : projectionInfo.typeInfo,
          (kmVisitorOption) -> {
            if (kmVisitorOption == KmVisitorOption.VISIT_PARENT) {
              assert projectionInfo == null || projectionInfo.isStarProjection();
              return kmType;
            } else {
              return kmType.visitArgument(
                  flagsOf(),
                  projectionInfo == null ? KmVariance.INVARIANT : projectionInfo.variance);
            }
          },
          allTypeParameters,
          factory,
          addAny);
    }
  }

  static String toClassifier(DexType type, DexItemFactory factory) {
    // E.g., V -> kotlin/Unit, J -> kotlin/Long, [J -> kotlin/LongArray
    if (factory.kotlin.knownTypeConversion.containsKey(type)) {
      DexType convertedType = factory.kotlin.knownTypeConversion.get(type);
      assert convertedType != null;
      return descriptorToKotlinClassifier(convertedType.toDescriptorString());
    }
    // E.g., [Ljava/lang/String; -> kotlin/Array
    if (type.isArrayType()) {
      return NAME + "/Array";
    }
    return descriptorToKotlinClassifier(type.toDescriptorString());
  }

  /**
   * Utility method building up all type-parameters from {@code classTypeParameters} combined with
   * {@code parameters}, where the consumer {@code addedFromParameters} is called for every
   * conversion of {@Code FormalTypeParameter} to {@Code KmTypeParameter}.
   *
   * <pre>
   *  classTypeParameters: [KmTypeParameter(T), KmTypeParameter(S)]
   *  parameters: [FormalTypeParameter(R)]
   *  result: [KmTypeParameter(T), KmTypeParameter(S), KmTypeParameter(R)].
   * </pre>
   *
   * @param classTypeParameters
   * @param originalTypeParameterInfo
   * @param parameters
   * @param factory
   * @param addedFromParameters
   * @return
   */
  static List<KmTypeParameter> convertFormalTypeParameters(
      List<KmTypeParameter> classTypeParameters,
      List<KotlinTypeParameterInfo> originalTypeParameterInfo,
      List<FormalTypeParameter> parameters,
      DexItemFactory factory,
      Consumer<KmTypeParameter> addedFromParameters) {
    if (parameters.isEmpty()) {
      return classTypeParameters;
    }
    ImmutableList.Builder<KmTypeParameter> builder = ImmutableList.builder();
    builder.addAll(classTypeParameters);
    // Assign type-variables ids to names. All generic signatures has been minified at this point,
    // but it may be that type-variables are used before we can see them (is that allowed?).
    for (int i = 0; i < parameters.size(); i++) {
      FormalTypeParameter parameter = parameters.get(i);
      int flags =
          originalTypeParameterInfo.size() > i
              ? originalTypeParameterInfo.get(i).getFlags()
              : flagsOf();
      KmVariance variance =
          originalTypeParameterInfo.size() > i
              ? originalTypeParameterInfo.get(i).getVariance()
              : KmVariance.INVARIANT;
      KmTypeParameter element =
          new KmTypeParameter(
              flags,
              parameter.getName(),
              getNewId(originalTypeParameterInfo, parameter.getName(), i),
              variance);
      builder.add(element);
      addedFromParameters.accept(element);
    }
    ImmutableList<KmTypeParameter> allTypeParameters = builder.build();
    for (int i = 0; i < parameters.size(); i++) {
      FormalTypeParameter parameter = parameters.get(i);
      KmTypeParameter kmTypeParameter = allTypeParameters.get(classTypeParameters.size() + i);
      visitUpperBound(parameter.getClassBound(), allTypeParameters, kmTypeParameter, factory);
      if (parameter.getInterfaceBounds() != null) {
        for (FieldTypeSignature interfaceBound : parameter.getInterfaceBounds()) {
          visitUpperBound(interfaceBound, allTypeParameters, kmTypeParameter, factory);
        }
      }
    }
    return allTypeParameters;
  }

  // Tries to pick the id from the type-parameter name. If no such exist, compute the highest id and
  // add the index of the current argument (to ensure unique naming in sequence).
  private static int getNewId(
      List<KotlinTypeParameterInfo> typeParameterInfos, String typeVariable, int currentId) {
    int maxId = -1;
    for (KotlinTypeParameterInfo typeParameterInfo : typeParameterInfos) {
      if (typeParameterInfo.getName().equals(typeVariable)) {
        return typeParameterInfo.getId();
      }
      maxId = Math.max(maxId, typeParameterInfo.getId());
    }
    return maxId + 1 + currentId;
  }

  private static void visitUpperBound(
      FieldTypeSignature typeSignature,
      List<KmTypeParameter> allTypeParameters,
      KmTypeParameter parameter,
      DexItemFactory factory) {
    if (typeSignature.isUnknown()) {
      return;
    }
    populateKmTypeFromSignature(
        typeSignature,
        null,
        (kmVisitorOption) -> {
          assert kmVisitorOption == KmVisitorOption.VISIT_NEW;
          return parameter.visitUpperBound(flagsOf());
        },
        allTypeParameters,
        factory,
        AddKotlinAnyType.DISREGARD);
  }
}
