// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.accessesField;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hamcrest.Matcher;

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

  public static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> setMockApiLevelForMethod(
          MethodReference method, AndroidApiLevel apiLevel) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options.apiModelingOptions().methodApiMapping.put(method, apiLevel);
          });
    };
  }

  public static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> setMockApiLevelForMethod(
          Constructor<?> constructor, AndroidApiLevel apiLevel) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options
                .apiModelingOptions()
                .methodApiMapping
                .put(Reference.methodFromMethod(constructor), apiLevel);
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

  public static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
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

  public static void enableApiCallerIdentification(
      TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> {
          options.apiModelingOptions().enableLibraryApiModeling = true;
          options.apiModelingOptions().enableApiCallerIdentification = true;
        });
  }

  public static void enableStubbingOfClassesAndDisableGlobalSyntheticCheck(
      TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> {
          options.apiModelingOptions().enableLibraryApiModeling = true;
          options.apiModelingOptions().enableStubbingOfClasses = true;
          // Our tests rely on us amending the library path with additional classes that are not
          // in the library.
          options.testing.globalSyntheticCreatedCallback = null;
        });
  }

  public static void enableStubbingOfClasses(TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> {
          options.apiModelingOptions().enableLibraryApiModeling = true;
          options.apiModelingOptions().enableStubbingOfClasses = true;
        });
  }

  public static void enableOutliningOfMethods(TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> {
          options.apiModelingOptions().enableLibraryApiModeling = true;
          options.apiModelingOptions().enableOutliningOfMethods = true;
        });
  }

  static void disableCheckAllApiReferencesAreNotUnknown(
      TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> {
          options.apiModelingOptions().enableApiCallerIdentification = true;
          options.apiModelingOptions().checkAllApiReferencesAreSet = false;
        });
  }

  public static void disableOutliningAndStubbing(
      TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    disableStubbingOfClasses(compilerBuilder);
    disableOutlining(compilerBuilder);
  }

  public static void disableStubbingOfClasses(TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> options.apiModelingOptions().enableStubbingOfClasses = false);
  }

  public static void disableOutlining(TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> options.apiModelingOptions().enableOutliningOfMethods = false);
  }

  public static void disableApiCallerIdentification(
      TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    compilerBuilder.addOptionsModification(
        options -> options.apiModelingOptions().enableApiCallerIdentification = false);
  }

  public static void disableApiModeling(TestCompilerBuilder<?, ?, ?, ?, ?> compilerBuilder) {
    disableOutliningAndStubbing(compilerBuilder);
    disableApiCallerIdentification(compilerBuilder);
    compilerBuilder.addOptionsModification(
        options -> options.apiModelingOptions().enableLibraryApiModeling = false);
  }

  static <T extends TestCompilerBuilder<?, ?, ?, ?, ?>>
      ThrowableConsumer<T> addTracedApiReferenceLevelCallBack(
          BiConsumer<MethodReference, AndroidApiLevel> consumer) {
    return compilerBuilder -> {
      compilerBuilder.addOptionsModification(
          options -> {
            options.apiModelingOptions().tracedMethodApiLevelCallback =
                (methodReference, computedApiLevel) -> {
                  consumer.accept(
                      methodReference,
                      computedApiLevel.isKnownApiLevel()
                          ? computedApiLevel.asKnownApiLevel().getApiLevel()
                          : null);
                };
          });
    };
  }

  public static ApiModelingClassVerificationHelper verifyThat(
      CodeInspector inspector, TestParameters parameters, Class<?> clazz) {
    return new ApiModelingClassVerificationHelper(inspector, parameters, clazz);
  }

  static ApiModelingMethodVerificationHelper verifyThat(
      CodeInspector inspector, TestParameters parameters, Method method) {
    return new ApiModelingMethodVerificationHelper(
        inspector, parameters, Reference.methodFromMethod(method));
  }

  static ApiModelingMethodVerificationHelper verifyThat(
      CodeInspector inspector, TestParameters parameters, MethodReference method) {
    return new ApiModelingMethodVerificationHelper(inspector, parameters, method);
  }

  static ApiModelingMethodVerificationHelper verifyThat(
      CodeInspector inspector, TestParameters parameters, Constructor method) {
    return new ApiModelingMethodVerificationHelper(
        inspector, parameters, Reference.methodFromMethod(method));
  }

  static ApiModelingFieldVerificationHelper verifyThat(
      CodeInspector inspector, TestParameters parameters, Field field) {
    return new ApiModelingFieldVerificationHelper(
        inspector, parameters, Reference.fieldFromField(field));
  }

  public static void assertNoSynthesizedClasses(CodeInspector inspector) {
    assertEquals(
        Collections.emptySet(),
        inspector.allClasses().stream()
            .filter(FoundClassSubject::isSynthetic)
            .collect(Collectors.toSet()));
  }

  public static class ApiModelingClassVerificationHelper {

    private final CodeInspector inspector;
    private final Class<?> classOfInterest;
    private final TestParameters parameters;

    public ApiModelingClassVerificationHelper(
        CodeInspector inspector, TestParameters parameters, Class<?> classOfInterest) {
      this.inspector = inspector;
      this.parameters = parameters;
      this.classOfInterest = classOfInterest;
    }

    public <E1 extends Exception, E2 extends Exception> ApiModelingClassVerificationHelper applyIf(
        boolean condition,
        ThrowingConsumer<ApiModelingClassVerificationHelper, E1> thenConsumer,
        ThrowingConsumer<ApiModelingClassVerificationHelper, E2> elseConsumer)
        throws E1, E2 {
      if (condition) {
        thenConsumer.accept(this);
      } else {
        elseConsumer.accept(this);
      }
      return this;
    }

    public void stubbedUntil(AndroidApiLevel finalApiLevel) {
      assertThat(
          inspector.clazz(classOfInterest),
          notIf(
              isPresent(),
              parameters.isCfRuntime()
                  || parameters.getApiLevel().isGreaterThanOrEqualTo(finalApiLevel)));
    }

    public void stubbedBetween(AndroidApiLevel startingApilevel, AndroidApiLevel endingApiLevel) {
      assertThat(
          inspector.clazz(classOfInterest),
          notIf(
              isPresent(),
              parameters.isCfRuntime()
                  || parameters.getApiLevel().isLessThan(startingApilevel)
                  || parameters.getApiLevel().isGreaterThanOrEqualTo(endingApiLevel)));
    }

    void hasCheckCastOutlinedFromUntil(Method method, AndroidApiLevel apiLevel) {
      if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(apiLevel)) {
        hasCheckCastOutlinedFrom(method);
      } else {
        hasNotCheckCastOutlinedFrom(method);
      }
    }

    public void hasCheckCastOutlinedFrom(Method method) {
      hasOutlinedInstructionWithClassReference(method, CodeMatchers::containsCheckCast);
    }

    public void hasNotCheckCastOutlinedFrom(Method method) {
      hasNotOulinedInstructionWithClassReference(method, CodeMatchers::containsCheckCast);
    }

    void hasInstanceOfOutlinedFromUntil(Method method, AndroidApiLevel apiLevel) {
      if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(apiLevel)) {
        hasInstanceOfOutlinedFrom(method);
      } else {
        hasNotInstanceOfOutlinedFrom(method);
      }
    }

    public void hasInstanceOfOutlinedFrom(Method method) {
      hasOutlinedInstructionWithClassReference(method, CodeMatchers::containsInstanceOf);
    }

    public void hasNotInstanceOfOutlinedFrom(Method method) {
      hasNotOulinedInstructionWithClassReference(method, CodeMatchers::containsInstanceOf);
    }

    public void hasConstClassOutlinedFromUntil(Method method, AndroidApiLevel apiLevel) {
      if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(apiLevel)) {
        hasConstClassOutlinedFrom(method);
      } else {
        hasNotConstClassOutlinedFrom(method);
      }
    }

    public void hasConstClassOutlinedFrom(Method method) {
      hasOutlinedInstructionWithClassReference(method, CodeMatchers::containsConstClass);
    }

    public void hasNotConstClassOutlinedFrom(Method method) {
      hasNotOulinedInstructionWithClassReference(method, CodeMatchers::containsConstClass);
    }

    private void hasOutlinedInstructionWithClassReference(
        Method method, Function<ClassReference, Matcher<? super MethodSubject>> matcher) {
      // Check that we call is in a synthetic class with an instance of.
      ClassReference classOfInterestReference = Reference.classFromClass(classOfInterest);
      Matcher<? super MethodSubject> instructionMatcher = matcher.apply(classOfInterestReference);
      List<FoundMethodSubject> outlinedMethod =
          inspector.allClasses().stream()
              .filter(
                  clazz ->
                      clazz
                          .getOriginalName()
                          .startsWith(
                              SyntheticItemsTestUtils.syntheticApiOutlineClassPrefix(
                                  method.getDeclaringClass())))
              .flatMap(clazz -> clazz.allMethods().stream())
              .filter(
                  methodSubject ->
                      methodSubject.isSynthetic() && instructionMatcher.matches(methodSubject))
              .collect(Collectors.toList());
      assertFalse(outlinedMethod.isEmpty());
      // Assert that method invokes the outline
      MethodSubject caller = inspector.method(method);
      assertThat(caller, isPresent());
      assertThat(caller, invokesMethod(outlinedMethod.get(0)));
    }

    private void hasNotOulinedInstructionWithClassReference(
        Method method, Function<ClassReference, Matcher<? super MethodSubject>> matcher) {
      ClassReference classOfInterestReference = Reference.classFromClass(classOfInterest);
      MethodSubject caller = inspector.method(method);
      assertThat(caller, isPresent());
      assertThat(caller, matcher.apply(classOfInterestReference));
    }
  }

  public static class ApiModelingFieldVerificationHelper {

    private final CodeInspector inspector;
    private final FieldReference fieldOfInterest;
    private final TestParameters parameters;

    private ApiModelingFieldVerificationHelper(
        CodeInspector inspector, TestParameters parameters, FieldReference fieldOfInterest) {
      this.inspector = inspector;
      this.fieldOfInterest = fieldOfInterest;
      this.parameters = parameters;
    }

    void isOutlinedFromUntil(Method method, AndroidApiLevel apiLevel) {
      if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(apiLevel)) {
        isOutlinedFrom(method);
      } else {
        isNotOutlinedFrom(method);
      }
    }

    void isOutlinedFrom(Method method) {
      // Check that the call is in a synthetic class.
      List<FoundMethodSubject> outlinedMethod =
          inspector.allClasses().stream()
              .flatMap(clazz -> clazz.allMethods().stream())
              .filter(
                  methodSubject ->
                      methodSubject.isSynthetic()
                          && accessesField(fieldOfInterest).matches(methodSubject))
              .collect(Collectors.toList());
      assertFalse(outlinedMethod.isEmpty());
      // Assert that method invokes the outline
      MethodSubject caller = inspector.method(method);
      assertThat(caller, isPresent());
      assertThat(caller, invokesMethod(outlinedMethod.get(0)));
    }

    void isNotOutlinedFrom(Method method) {
      MethodSubject caller = inspector.method(method);
      assertThat(caller, isPresent());
      assertThat(caller, accessesField(fieldOfInterest));
    }
  }

  public static class ApiModelingMethodVerificationHelper {

    private final CodeInspector inspector;
    private final MethodReference methodOfInterest;
    private final TestParameters parameters;

    private ApiModelingMethodVerificationHelper(
        CodeInspector inspector, TestParameters parameters, MethodReference methodOfInterest) {
      this.inspector = inspector;
      this.methodOfInterest = methodOfInterest;
      this.parameters = parameters;
    }

    public ApiModelingMethodVerificationHelper setHolder(FoundClassSubject classSubject) {
      return new ApiModelingMethodVerificationHelper(
          inspector,
          parameters,
          Reference.method(
              classSubject.getFinalReference(),
              methodOfInterest.getMethodName(),
              methodOfInterest.getFormalTypes(),
              methodOfInterest.getReturnType()));
    }

    void inlinedIntoFromApiLevel(Method method, AndroidApiLevel apiLevel) {
      if (parameters.isDexRuntime() && parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevel)) {
        inlinedInto(method);
      } else {
        notInlinedInto(method);
      }
    }

    void notInlinedInto(Method method) {
      MethodSubject candidate = inspector.method(methodOfInterest);
      assertThat(candidate, isPresent());
      MethodSubject target = inspector.method(method);
      assertThat(target, isPresent());
      assertThat(target, CodeMatchers.invokesMethod(candidate));
    }

    void inlinedInto(Method method) {
      MethodSubject candidate = inspector.method(methodOfInterest);
      if (!candidate.isPresent()) {
        return;
      }
      MethodSubject target = inspector.method(method);
      assertThat(target, isPresent());
      assertThat(target, not(CodeMatchers.invokesMethod(candidate)));
    }

    void isOutlinedFromUntil(Executable method, AndroidApiLevel apiLevel) {
      if (parameters.isDexRuntime() && parameters.getApiLevel().isLessThan(apiLevel)) {
        isOutlinedFrom(method);
      } else {
        isNotOutlinedFrom(method);
      }
    }

    void isOutlinedFromBetween(
        Executable method, AndroidApiLevel lowerBoundInclusive, AndroidApiLevel upperBound) {
      if (parameters.isDexRuntime()
          && parameters.getApiLevel().isLessThan(upperBound)
          && parameters.getApiLevel().isGreaterThanOrEqualTo(lowerBoundInclusive)) {
        isOutlinedFrom(method);
      } else {
        isNotOutlinedFrom(method);
      }
    }

    void isOutlinedFromUntilAlsoForCf(Executable method, AndroidApiLevel apiLevel) {
      if (parameters.getApiLevel().isLessThan(apiLevel)) {
        isOutlinedFrom(method);
      } else {
        isNotOutlinedFrom(method);
      }
    }

    void isOutlinedFrom(Executable method) {
      // Check that the call is in a synthetic class.
      List<FoundMethodSubject> outlinedMethod =
          inspector.allClasses().stream()
              .flatMap(clazz -> clazz.allMethods().stream())
              .filter(
                  methodSubject ->
                      methodSubject.isSynthetic()
                          && invokesMethodWithHolderAndName(
                                  methodOfInterest.getHolderClass().getTypeName(),
                                  methodOfInterest.getMethodName())
                              .matches(methodSubject))
              .collect(Collectors.toList());
      assertEquals(1, outlinedMethod.size());
      // Assert that method invokes the outline
      MethodSubject caller = inspector.method(Reference.methodFromMethod(method));
      assertThat(caller, isPresent());
      assertThat(caller, invokesMethod(outlinedMethod.get(0)));
    }

    void isNotOutlinedFrom(Executable method) {
      MethodSubject caller = inspector.method(Reference.methodFromMethod(method));
      assertThat(caller, isPresent());
      assertThat(caller, invokesMethodWithName(methodOfInterest.getMethodName()));
    }
  }
}
