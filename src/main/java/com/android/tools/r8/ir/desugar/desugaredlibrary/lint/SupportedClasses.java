// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SupportedClasses {
  private final Map<DexType, SupportedClass> supportedClasses;

  public void forEachClass(Consumer<SupportedClass> consumer) {
    supportedClasses.values().forEach(consumer);
  }

  SupportedClasses(Map<DexType, SupportedClass> supportedClasses) {
    this.supportedClasses = supportedClasses;
  }

  public static class SupportedClass {

    private final DexClass clazz;
    private final ClassAnnotation classAnnotation;
    private final List<DexEncodedMethod> supportedMethods;
    private final Map<DexMethod, MethodAnnotation> methodAnnotations;

    private SupportedClass(
        DexClass clazz,
        ClassAnnotation classAnnotation,
        List<DexEncodedMethod> supportedMethods,
        Map<DexMethod, MethodAnnotation> methodAnnotations) {
      this.clazz = clazz;
      this.classAnnotation = classAnnotation;
      this.supportedMethods = supportedMethods;
      this.methodAnnotations = methodAnnotations;
    }

    public DexType getType() {
      return clazz.type;
    }

    public DexClass getClazz() {
      return clazz;
    }

    public ClassAnnotation getClassAnnotation() {
      return classAnnotation;
    }

    public List<DexEncodedMethod> getSupportedMethods() {
      return supportedMethods;
    }

    public void forEachMethodAndAnnotation(
        BiConsumer<DexEncodedMethod, MethodAnnotation> biConsumer) {
      for (DexEncodedMethod supportedMethod : supportedMethods) {
        biConsumer.accept(supportedMethod, getMethodAnnotation(supportedMethod.getReference()));
      }
    }

    public MethodAnnotation getMethodAnnotation(DexMethod method) {
      return methodAnnotations.get(method);
    }

    static Builder builder(DexClass clazz) {
      return new Builder(clazz);
    }

    private static class Builder {

      private final DexClass clazz;
      private ClassAnnotation classAnnotation;
      private final List<DexEncodedMethod> supportedMethods = new ArrayList<>();
      private final Map<DexMethod, MethodAnnotation> methodAnnotations = new HashMap<>();

      private Builder(DexClass clazz) {
        this.clazz = clazz;
      }

      void forEachMethods(BiConsumer<DexClass, List<DexEncodedMethod>> biConsumer) {
        biConsumer.accept(clazz, supportedMethods);
      }

      void forEachMethod(BiConsumer<DexClass, DexEncodedMethod> biConsumer) {
        for (DexEncodedMethod dexEncodedMethod : supportedMethods) {
          biConsumer.accept(clazz, dexEncodedMethod);
        }
      }

      void addSupportedMethod(DexEncodedMethod method) {
        assert method.getHolderType() == clazz.type;
        supportedMethods.add(method);
      }

      void annotateClass(ClassAnnotation annotation) {
        assert annotation != null;
        assert classAnnotation == null;
        classAnnotation = annotation;
      }

      void annotateMethod(DexMethod method, MethodAnnotation annotation) {
        assert method.getHolderType() == clazz.type;
        MethodAnnotation prev =
            methodAnnotations.getOrDefault(method, MethodAnnotation.getDefault());
        methodAnnotations.put(method, annotation.combine(prev));
      }

      MethodAnnotation getMethodAnnotation(DexMethod method) {
        return methodAnnotations.get(method);
      }

      SupportedClass build() {
        supportedMethods.sort(Comparator.comparing(DexEncodedMethod::getReference));
        return new SupportedClass(
            clazz, classAnnotation, ImmutableList.copyOf(supportedMethods), methodAnnotations);
      }
    }
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    Map<DexType, SupportedClass.Builder> supportedClassBuilders = new IdentityHashMap<>();

    void forEachClassAndMethods(BiConsumer<DexClass, List<DexEncodedMethod>> biConsumer) {
      supportedClassBuilders
          .values()
          .forEach(classBuilder -> classBuilder.forEachMethods(biConsumer));
    }

    void forEachClassAndMethod(BiConsumer<DexClass, DexEncodedMethod> biConsumer) {
      supportedClassBuilders
          .values()
          .forEach(classBuilder -> classBuilder.forEachMethod(biConsumer));
    }

    void addSupportedMethod(DexClass holder, DexEncodedMethod method) {
      SupportedClass.Builder classBuilder =
          supportedClassBuilders.computeIfAbsent(
              holder.type, clazz -> SupportedClass.builder(holder));
      classBuilder.addSupportedMethod(method);
    }

    void annotateClass(DexType type, ClassAnnotation annotation) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(type);
      assert classBuilder != null;
      classBuilder.annotateClass(annotation);
    }

    void annotateMethod(DexMethod method, MethodAnnotation annotation) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(method.getHolderType());
      assert classBuilder != null;
      classBuilder.annotateMethod(method, annotation);
    }

    void annotateMethodIfPresent(DexMethod method, MethodAnnotation annotation) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(method.getHolderType());
      if (classBuilder == null) {
        return;
      }
      annotateMethod(method, annotation);
    }

    MethodAnnotation getMethodAnnotation(DexMethod method) {
      SupportedClass.Builder classBuilder = supportedClassBuilders.get(method.getHolderType());
      assert classBuilder != null;
      return classBuilder.getMethodAnnotation(method);
    }

    SupportedClasses build() {
      Map<DexType, SupportedClass> map = new IdentityHashMap<>();
      supportedClassBuilders.forEach(
          (type, classBuilder) -> {
            map.put(type, classBuilder.build());
          });
      return new SupportedClasses(ImmutableSortedMap.copyOf(map));
    }
  }

  static class ClassAnnotation {

    private final boolean fullySupported;
    // Methods in latest android.jar but unsupported.
    private final List<DexMethod> unsupportedMethods;

    public ClassAnnotation(boolean fullySupported, List<DexMethod> unsupportedMethods) {
      this.fullySupported = fullySupported;
      unsupportedMethods.sort(Comparator.naturalOrder());
      this.unsupportedMethods = ImmutableList.copyOf(unsupportedMethods);
    }

    public boolean isFullySupported() {
      return fullySupported;
    }

    public List<DexMethod> getUnsupportedMethods() {
      return unsupportedMethods;
    }
  }

  public static class MethodAnnotation {

    private static final MethodAnnotation COVARIANT_RETURN_SUPPORTED =
        new MethodAnnotation(false, false, true, false, -1, -1);
    private static final MethodAnnotation DEFAULT =
        new MethodAnnotation(false, false, false, false, -1, -1);
    private static final MethodAnnotation PARALLEL_STREAM_METHOD =
        new MethodAnnotation(true, false, false, false, -1, -1);
    private static final MethodAnnotation MISSING_FROM_LATEST_ANDROID_JAR =
        new MethodAnnotation(false, true, false, false, -1, -1);

    // ParallelStream methods are not supported when the runtime api level is strictly below 21.
    final boolean parallelStreamMethod;
    // Methods not in the latest android jar but still fully supported.
    final boolean missingFromLatestAndroidJar;
    // Methods not supported in a given min api range.
    final boolean unsupportedInMinApiRange;
    final boolean covariantReturnSupported;
    final int minRange;
    final int maxRange;

    MethodAnnotation(
        boolean parallelStreamMethod,
        boolean missingFromLatestAndroidJar,
        boolean covariantReturnSupported,
        boolean unsupportedInMinApiRange,
        int minRange,
        int maxRange) {
      this.parallelStreamMethod = parallelStreamMethod;
      this.missingFromLatestAndroidJar = missingFromLatestAndroidJar;
      this.covariantReturnSupported = covariantReturnSupported;
      this.unsupportedInMinApiRange = unsupportedInMinApiRange;
      this.minRange = minRange;
      this.maxRange = maxRange;
    }

    public static MethodAnnotation getCovariantReturnSupported() {
      return COVARIANT_RETURN_SUPPORTED;
    }

    public static MethodAnnotation getDefault() {
      return DEFAULT;
    }

    public static MethodAnnotation getParallelStreamMethod() {
      return PARALLEL_STREAM_METHOD;
    }

    public static MethodAnnotation getMissingFromLatestAndroidJar() {
      return MISSING_FROM_LATEST_ANDROID_JAR;
    }

    public static MethodAnnotation createMissingInMinApi(int api) {
      return new MethodAnnotation(false, false, false, true, api, api);
    }

    public boolean isUnsupportedInMinApiRange() {
      return unsupportedInMinApiRange;
    }

    public int getMinRange() {
      return minRange;
    }

    public int getMaxRange() {
      return maxRange;
    }

    public boolean isCovariantReturnSupported() {
      return covariantReturnSupported;
    }

    public MethodAnnotation combine(MethodAnnotation other) {
      if (this == getDefault()) {
        return other;
      }
      if (other == getDefault()) {
        return this;
      }
      int newMin, newMax;
      if (!unsupportedInMinApiRange && !other.unsupportedInMinApiRange) {
        newMin = newMax = -1;
      } else if (!unsupportedInMinApiRange || !other.unsupportedInMinApiRange) {
        newMin = unsupportedInMinApiRange ? minRange : other.minRange;
        newMax = unsupportedInMinApiRange ? maxRange : other.maxRange;
      } else {
        // Merge ranges if contiguous or throw.
        if (maxRange == other.minRange - 1) {
          newMin = minRange;
          newMax = other.maxRange;
        } else if (other.maxRange == minRange - 1) {
          newMin = other.minRange;
          newMax = maxRange;
        } else {
          // 20 is missing, so if maxRange or minRange are 19 the following is 21.
          if (maxRange == 19 && other.minRange == 21) {
            newMin = minRange;
            newMax = other.maxRange;
          } else if (other.maxRange == 19 && minRange == 21) {
            newMin = other.minRange;
            newMax = maxRange;
          } else {
            throw new RuntimeException("Cannot merge ranges.");
          }
        }
      }
      return new MethodAnnotation(
          parallelStreamMethod || other.parallelStreamMethod,
          missingFromLatestAndroidJar || other.missingFromLatestAndroidJar,
          covariantReturnSupported || other.covariantReturnSupported,
          unsupportedInMinApiRange || other.unsupportedInMinApiRange,
          newMin,
          newMax);
    }
  }
}
