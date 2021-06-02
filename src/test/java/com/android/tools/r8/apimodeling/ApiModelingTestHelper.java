// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodeling;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public abstract class ApiModelingTestHelper {

  static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
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

  static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>> ThrowableConsumer<T> setMockApiLevelForType(
      Class<?> clazz, AndroidApiLevel apiLevel) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options
                .apiModelingOptions()
                .typeApiMapping
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

  static ApiModelingMethodVerificationHelper verifyThat(TestParameters parameters, Method method) {
    return new ApiModelingMethodVerificationHelper(parameters, method);
  }

  public static class ApiModelingMethodVerificationHelper {

    private final Method methodOfInterest;
    private final TestParameters parameters;

    public ApiModelingMethodVerificationHelper(TestParameters parameters, Method methodOfInterest) {
      this.methodOfInterest = methodOfInterest;
      this.parameters = parameters;
    }

    protected ThrowingConsumer<CodeInspector, Exception> inlinedIntoFromApiLevel(
        Method method, AndroidApiLevel apiLevel) {
      return parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevel)
          ? inlinedInto(method)
          : notInlinedInto(method);
    }

    private ThrowingConsumer<CodeInspector, Exception> notInlinedInto(Method method) {
      return inspector -> {
        MethodSubject candidate = inspector.method(methodOfInterest);
        assertThat(candidate, isPresent());
        MethodSubject target = inspector.method(method);
        assertThat(target, isPresent());
        assertThat(target, CodeMatchers.invokesMethod(candidate));
      };
    }

    public ThrowingConsumer<CodeInspector, Exception> inlinedInto(Method method) {
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
