// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class SupportedMethodsWithAnnotations {

  public final Map<DexClass, List<DexEncodedMethod>> supportedMethods;
  public final Map<DexMethod, MethodAnnotation> annotatedMethods;
  // A fully supported class has no annotated methods, and all the methods from the latest
  // android.jar are supported.
  public final Map<DexType, ClassAnnotation> annotatedClasses;

  SupportedMethodsWithAnnotations(
      Map<DexClass, List<DexEncodedMethod>> supportedMethods,
      Map<DexMethod, MethodAnnotation> annotatedMethods,
      Map<DexType, ClassAnnotation> annotatedClasses) {
    this.supportedMethods = supportedMethods;
    this.annotatedMethods = annotatedMethods;
    this.annotatedClasses = annotatedClasses;
  }

  static Builder builder() {
    return new Builder();
  }

  static class Builder {

    Map<DexClass, List<DexEncodedMethod>> supportedMethods = new IdentityHashMap<>();
    Map<DexMethod, MethodAnnotation> annotatedMethods = new IdentityHashMap<>();
    Map<DexType, ClassAnnotation> annotatedClasses = new IdentityHashMap<>();

    void forEachClassAndMethods(BiConsumer<DexClass, List<DexEncodedMethod>> biConsumer) {
      supportedMethods.forEach(biConsumer);
    }

    void forEachClassAndMethod(BiConsumer<DexClass, DexEncodedMethod> biConsumer) {
      supportedMethods.forEach(
          (clazz, methods) -> {
            for (DexEncodedMethod method : methods) {
              biConsumer.accept(clazz, method);
            }
          });
    }

    void addSupportedMethod(DexClass holder, DexEncodedMethod method) {
      List<DexEncodedMethod> methods =
          supportedMethods.computeIfAbsent(holder, f -> new ArrayList<>());
      methods.add(method);
    }

    void annotateClass(DexType type, ClassAnnotation annotation) {
      annotatedClasses.put(type, annotation);
    }

    void annotateMethod(DexMethod method, MethodAnnotation annotation) {
      MethodAnnotation prev = annotatedMethods.getOrDefault(method, MethodAnnotation.getDefault());
      annotatedMethods.put(method, annotation.combine(prev));
    }

    SupportedMethodsWithAnnotations build() {
      supportedMethods.forEach(
          (k, v) -> v.sort(Comparator.comparing(DexEncodedMember::getReference)));
      return new SupportedMethodsWithAnnotations(
          ImmutableSortedMap.copyOf(supportedMethods, Comparator.comparing(DexClass::getType)),
          ImmutableMap.copyOf(annotatedMethods),
          ImmutableMap.copyOf(annotatedClasses));
    }
  }

  static class ClassAnnotation {

    final boolean fullySupported;
    // Methods in latest android.jar but unsupported.
    final List<DexMethod> unsupportedMethods;

    public ClassAnnotation(boolean fullySupported, List<DexMethod> unsupportedMethods) {
      this.fullySupported = fullySupported;
      unsupportedMethods.sort(Comparator.naturalOrder());
      this.unsupportedMethods = ImmutableList.copyOf(unsupportedMethods);
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
