// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ReflectionHelper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kotlin.Pair;
import kotlinx.metadata.internal.metadata.ProtoBuf;
import kotlinx.metadata.internal.metadata.jvm.JvmProtoBuf;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import kotlinx.metadata.jvm.KotlinClassMetadata.FileFacade;
import kotlinx.metadata.jvm.KotlinClassMetadata.MultiFileClassPart;
import kotlinx.metadata.jvm.KotlinClassMetadata.SyntheticClass;

// Due to kotlin-metadata-jvm library synthesizing jvm method signatures from type-information
// we do an extra check to figure out if we should model the signature. If we model it, we will
// add name and descriptor information to the string pool for the proto message, so it is
// better to avoid it.
// https://github.com/Kotlin/kotlinx.reflect.lite/blob/46d47f118f9846166b6b8f8212bdbc822fe2f634/src/main/java/org/jetbrains/kotlin/serialization/jvm/JvmProtoBufUtil.kt

public class KotlinJvmSignatureExtensionInformation {

  private final Set<Integer> noExtensionIndicesForFunctions;
  private final Set<Integer> noExtensionIndicesForConstructors;

  private static final KotlinJvmSignatureExtensionInformation EMPTY = builder().build();

  private KotlinJvmSignatureExtensionInformation(
      Set<Integer> noExtensionIndicesForFunctions, Set<Integer> noExtensionIndicesForConstructors) {
    this.noExtensionIndicesForFunctions = noExtensionIndicesForFunctions;
    this.noExtensionIndicesForConstructors = noExtensionIndicesForConstructors;
  }

  public static KotlinJvmSignatureExtensionInformation readInformationFromMessage(
      FileFacade fileFacadeMetadata, InternalOptions options) {
    return readPackageDataFromMessage(fileFacadeMetadata, options);
  }

  public static KotlinJvmSignatureExtensionInformation readInformationFromMessage(
      MultiFileClassPart classPart, InternalOptions options) {
    return readPackageDataFromMessage(classPart, options);
  }

  private static KotlinJvmSignatureExtensionInformation readPackageDataFromMessage(
      Object object, InternalOptions options) {
    try {
      Pair<?, ProtoBuf.Package> kotlinPairData =
          ReflectionHelper.performReflection(
              object,
              ReflectionHelper.builder()
                  .readField("packageData$delegate")
                  .setSetAccessible(true)
                  .done()
                  .readMethod("getValue")
                  .setSetAccessible(true)
                  .done()
                  .build());
      return builder().visit(kotlinPairData.getSecond()).build();
    } catch (Exception e) {
      options.warningReadingKotlinMetadataReflective();
      return empty();
    }
  }

  public static KotlinJvmSignatureExtensionInformation readInformationFromMessage(
      SyntheticClass syntheticClass, InternalOptions options) {
    try {
      Pair<?, ProtoBuf.Function> kotlinPairData =
          ReflectionHelper.performReflection(
              syntheticClass,
              ReflectionHelper.builder()
                  .readField("functionData$delegate")
                  .setSetAccessible(true)
                  .done()
                  .readMethod("getValue")
                  .setSetAccessible(true)
                  .done()
                  .build());
      if (kotlinPairData == null) {
        return empty();
      }
      return builder().visit(kotlinPairData.getSecond(), 0).build();
    } catch (Exception e) {
      options.warningReadingKotlinMetadataReflective();
      return empty();
    }
  }

  public static KotlinJvmSignatureExtensionInformation readInformationFromMessage(
      KotlinClassMetadata.Class kMetadata, InternalOptions options) {
    try {
      Pair<?, ProtoBuf.Class> kotlinPairData =
          ReflectionHelper.performReflection(
              kMetadata,
              ReflectionHelper.builder()
                  .readField("classData$delegate")
                  .setSetAccessible(true)
                  .done()
                  .readMethod("getValue")
                  .setSetAccessible(true)
                  .done()
                  .build());
      return builder().visit(kotlinPairData.getSecond()).build();
    } catch (Exception e) {
      options.warningReadingKotlinMetadataReflective();
      return empty();
    }
  }

  public boolean hasJvmMethodSignatureExtensionForFunction(int index) {
    return !noExtensionIndicesForFunctions.contains(index);
  }

  public boolean hasJvmMethodSignatureExtensionForConstructor(int index) {
    return !noExtensionIndicesForConstructors.contains(index);
  }

  public static KotlinJvmSignatureExtensionInformationBuilder builder() {
    return new KotlinJvmSignatureExtensionInformationBuilder();
  }

  public static KotlinJvmSignatureExtensionInformation empty() {
    return EMPTY;
  }

  private static class KotlinJvmSignatureExtensionInformationBuilder {

    private final Set<Integer> noExtensionIndicesForFunctions = new HashSet<>();
    private final Set<Integer> noExtensionIndicesForConstructors = new HashSet<>();

    private KotlinJvmSignatureExtensionInformation build() {
      return new KotlinJvmSignatureExtensionInformation(
          noExtensionIndicesForFunctions, noExtensionIndicesForConstructors);
    }

    private KotlinJvmSignatureExtensionInformationBuilder visit(ProtoBuf.Class clazz) {
      visitFunctions(clazz.getFunctionList());
      visitConstructors(clazz.getConstructorList());
      return this;
    }

    private KotlinJvmSignatureExtensionInformationBuilder visit(ProtoBuf.Package pkg) {
      visitFunctions(pkg.getFunctionList());
      return this;
    }

    private void visitFunctions(List<ProtoBuf.Function> functions) {
      ListUtils.forEachWithIndex(functions, this::visit);
    }

    public KotlinJvmSignatureExtensionInformationBuilder visit(
        ProtoBuf.Function function, int index) {
      if (!function.hasExtension(JvmProtoBuf.methodSignature)) {
        noExtensionIndicesForFunctions.add(index);
      }
      return this;
    }

    private void visitConstructors(List<ProtoBuf.Constructor> constructors) {
      ListUtils.forEachWithIndex(
          constructors,
          (constructor, index) -> {
            if (!constructor.hasExtension(JvmProtoBuf.constructorSignature)) {
              noExtensionIndicesForConstructors.add(index);
            }
          });
    }
  }
}
