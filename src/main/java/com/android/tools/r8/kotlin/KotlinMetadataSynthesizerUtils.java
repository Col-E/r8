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
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kotlinx.metadata.KmTypeParameter;
import kotlinx.metadata.KmTypeVisitor;
import kotlinx.metadata.KmVariance;

class KotlinMetadataSynthesizerUtils {

  static void populateKmTypeFromSignature(
      FieldTypeSignature typeSignature,
      Supplier<KmTypeVisitor> typeVisitor,
      List<KmTypeParameter> allTypeParameters,
      DexItemFactory factory) {
    if (typeSignature.isClassTypeSignature()) {
      populateKmTypeFromClassTypeSignature(
          typeSignature.asClassTypeSignature(), typeVisitor, allTypeParameters, factory);
    } else if (typeSignature.isArrayTypeSignature()) {
      populateKmTypeFromArrayTypeSignature(
          typeSignature.asArrayTypeSignature(), typeVisitor, allTypeParameters, factory);
    } else if (typeSignature.isTypeVariableSignature()) {
      populateKmTypeFromTypeVariableSignature(
          typeSignature.asTypeVariableSignature(), typeVisitor, allTypeParameters);
    } else {
      assert typeSignature.isStar();
    }
  }

  private static void populateKmTypeFromTypeVariableSignature(
      TypeVariableSignature typeSignature,
      Supplier<KmTypeVisitor> visitor,
      List<KmTypeParameter> allTypeParameters) {
    for (KmTypeParameter typeParameter : allTypeParameters) {
      if (typeParameter
          .getName()
          .equals(typeSignature.asTypeVariableSignature().getTypeVariable())) {
        visitor.get().visitTypeParameter(typeParameter.getId());
        return;
      }
    }
  }

  private static void populateKmTypeFromArrayTypeSignature(
      ArrayTypeSignature typeSignature,
      Supplier<KmTypeVisitor> visitor,
      List<KmTypeParameter> allTypeParameters,
      DexItemFactory factory) {
    ArrayTypeSignature arrayTypeSignature = typeSignature.asArrayTypeSignature();
    if (!arrayTypeSignature.elementSignature().isFieldTypeSignature()) {
      return;
    }
    KmTypeVisitor kmType = visitor.get();
    kmType.visitClass(NAME + "/Array");
    populateKmTypeFromSignature(
        arrayTypeSignature.elementSignature().asFieldTypeSignature(),
        () -> kmType.visitArgument(flagsOf(), KmVariance.INVARIANT),
        allTypeParameters,
        factory);
  }

  private static void populateKmTypeFromClassTypeSignature(
      ClassTypeSignature typeSignature,
      Supplier<KmTypeVisitor> visitor,
      List<KmTypeParameter> allTypeParameters,
      DexItemFactory factory) {
    // No need to record the trivial argument.
    if (factory.objectType == typeSignature.type()) {
      return;
    }
    KmTypeVisitor kmType = visitor.get();
    kmType.visitClass(toClassifier(typeSignature.type(), factory));
    for (FieldTypeSignature typeArgument : typeSignature.typeArguments()) {
      populateKmTypeFromSignature(
          typeArgument,
          () -> kmType.visitArgument(flagsOf(), KmVariance.INVARIANT),
          allTypeParameters,
          factory);
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
   * @param parameters
   * @param factory
   * @param addedFromParameters
   * @return
   */
  static List<KmTypeParameter> convertFormalTypeParameters(
      List<KmTypeParameter> classTypeParameters,
      List<FormalTypeParameter> parameters,
      DexItemFactory factory,
      Consumer<KmTypeParameter> addedFromParameters) {
    if (parameters.isEmpty()) {
      return classTypeParameters;
    }
    ImmutableList.Builder<KmTypeParameter> builder = ImmutableList.builder();
    builder.addAll(classTypeParameters);
    int idCounter = classTypeParameters.size();
    // Assign type-variables ids to names. All generic signatures has been minified at this point,
    // but it may be that type-variables are used before we can see them (is that allowed?).
    for (FormalTypeParameter parameter : parameters) {
      KmTypeParameter element =
          new KmTypeParameter(flagsOf(), parameter.getName(), idCounter++, KmVariance.INVARIANT);
      builder.add(element);
      addedFromParameters.accept(element);
    }
    idCounter = 0;
    ImmutableList<KmTypeParameter> allTypeParameters = builder.build();
    for (FormalTypeParameter parameter : parameters) {
      KmTypeParameter kmTypeParameter =
          allTypeParameters.get(classTypeParameters.size() + idCounter++);
      visitUpperBound(parameter.getClassBound(), allTypeParameters, kmTypeParameter, factory);
      if (parameter.getInterfaceBounds() != null) {
        for (FieldTypeSignature interfaceBound : parameter.getInterfaceBounds()) {
          visitUpperBound(interfaceBound, allTypeParameters, kmTypeParameter, factory);
        }
      }
    }
    return allTypeParameters;
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
        typeSignature, () -> parameter.visitUpperBound(flagsOf()), allTypeParameters, factory);
  }
}
