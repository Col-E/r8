// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

public abstract class ApiModelingTestHelper {

  public static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> setMockApiLevelForMethod(Method method, AndroidApiLevel apiLevel) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options
                .apiModelingOptions()
                .methodApiMapping
                .put(Reference.methodFromMethod(method), apiLevel);
          });
    };
  }

  static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> setMockApiLevelForDefaultInstanceInitializer(
          Class<?> clazz, AndroidApiLevel apiLevel) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options
                .apiModelingOptions()
                .methodApiMapping
                .put(
                    Reference.method(
                        Reference.classFromClass(clazz), "<init>", ImmutableList.of(), null),
                    apiLevel);
          });
    };
  }

  static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> setMockApiLevelForField(Field field, AndroidApiLevel apiLevel) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options
                .apiModelingOptions()
                .fieldApiMapping
                .put(Reference.fieldFromField(field), apiLevel);
          });
    };
  }

  static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> setMockApiLevelForClass(Class<?> clazz, AndroidApiLevel apiLevel) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options
                .apiModelingOptions()
                .classApiMapping
                .put(Reference.classFromClass(clazz), apiLevel);
          });
    };
  }

  static void enableApiCallerIdentification(TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> {
          options.apiModelingOptions().enableApiCallerIdentification = true;
        });
  }

  static void disableCheckAllApiReferencesAreNotUnknown(
      TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> {
          options.apiModelingOptions().checkAllApiReferencesAreSet = false;
        });
  }

  static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> addTracedApiReferenceLevelCallBack(
          BiConsumer<MethodReference, AndroidApiLevel> consumer) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options.apiModelingOptions().tracedMethodApiLevelCallback = consumer;
          });
    };
  }

  static ApiModelingMethodVerificationHelper verifyThat(TestParameters parameters, Method method) {
    return new ApiModelingMethodVerificationHelper(parameters, Reference.methodFromMethod(method));
  }

  public static class ApiModelingMethodVerificationHelper {

    private final MethodReference methodOfInterest;
    private final TestParameters parameters;

    private ApiModelingMethodVerificationHelper(
        TestParameters parameters, MethodReference methodOfInterest) {
      this.methodOfInterest = methodOfInterest;
      this.parameters = parameters;
    }

    public ApiModelingMethodVerificationHelper setHolder(FoundClassSubject classSubject) {
      return new ApiModelingMethodVerificationHelper(
          parameters,
          Reference.method(
              classSubject.getFinalReference(),
              methodOfInterest.getMethodName(),
              methodOfInterest.getFormalTypes(),
              methodOfInterest.getReturnType()));
    }

    ThrowingConsumer<CodeInspector, Exception> inlinedIntoFromApiLevel(
        Method method, AndroidApiLevel apiLevel) {
      return parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevel)
          ? inlinedInto(method)
          : notInlinedInto(method);
    }

    ThrowingConsumer<CodeInspector, Exception> notInlinedInto(Method method) {
      return inspector -> {
        MethodSubject candidate = inspector.method(methodOfInterest);
        assertThat(candidate, isPresent());
        MethodSubject target = inspector.method(method);
        assertThat(target, isPresent());
        assertThat(target, CodeMatchers.invokesMethod(candidate));
      };
    }

    ThrowingConsumer<CodeInspector, Exception> inlinedInto(Method method) {
      return inspector -> {
        MethodSubject candidate = inspector.method(methodOfInterest);
        if (!candidate.isPresent()) {
          return;
        }
        MethodSubject target = inspector.method(method);
        assertThat(target, isPresent());
        assertThat(target, not(CodeMatchers.invokesMethod(candidate)));
      };
    }
  }
}
