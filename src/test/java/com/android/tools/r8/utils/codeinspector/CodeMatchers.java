// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class CodeMatchers {

  public static Matcher<MethodSubject> accessesField(FieldSubject targetSubject) {
    if (!targetSubject.isPresent()) {
      throw new IllegalArgumentException();
    }
    DexField target = targetSubject.getField().field;
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        if (!subject.isPresent()) {
          return false;
        }
        if (!subject.getMethod().hasCode()) {
          return false;
        }
        return subject.streamInstructions().anyMatch(isFieldAccessWithTarget(target));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("accesses field `" + target.toSourceString() + "`");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> invokesMethod(MethodSubject targetSubject) {
    if (!targetSubject.isPresent()) {
      throw new IllegalArgumentException();
    }
    DexMethod target = targetSubject.getMethod().method;
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        if (!subject.isPresent()) {
          return false;
        }
        if (!subject.getMethod().hasCode()) {
          return false;
        }
        return subject.streamInstructions().anyMatch(isInvokeWithTarget(target));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("invokes method `" + target.toSourceString() + "`");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> invokesMethod(
      String returnType, String holderType, String methodName, List<String> parameterTypes) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        if (!subject.isPresent()) {
          return false;
        }
        if (!subject.getMethod().hasCode()) {
          return false;
        }
        return subject
            .streamInstructions()
            .anyMatch(isInvokeWithTarget(returnType, holderType, methodName, parameterTypes));
      }

      @Override
      public void describeTo(Description description) {
        StringBuilder text =
            new StringBuilder("invokes method `")
                .append(returnType != null ? returnType : "*")
                .append(" ")
                .append(holderType != null ? holderType : "*")
                .append(".")
                .append(methodName != null ? methodName : "*")
                .append("(");
        if (parameterTypes != null) {
          text.append(
              parameterTypes.stream()
                  .map(parameterType -> parameterType != null ? parameterType : "*")
                  .collect(Collectors.joining(", ")));
        } else {
          text.append("...");
        }
        text.append(")`");
        description.appendText(text.toString());
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> invokesMethodWithName(String name) {
    return invokesMethod(null, null, name, null);
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(DexMethod target) {
    return instruction -> instruction.isInvoke() && instruction.getMethod() == target;
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(
      String returnType, String holderType, String methodName, List<String> parameterTypes) {
    return instruction -> {
      if (!instruction.isInvoke()) {
        return false;
      }
      DexMethod invokedMethod = instruction.getMethod();
      if (returnType != null
          && !invokedMethod.getReturnType().toSourceString().equals(returnType)) {
        return false;
      }
      if (holderType != null
          && !invokedMethod.getHolderType().toSourceString().equals(holderType)) {
        return false;
      }
      if (methodName != null && !invokedMethod.getName().toSourceString().equals(methodName)) {
        return false;
      }
      if (parameterTypes != null) {
        if (parameterTypes.size() != invokedMethod.getArity()) {
          return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
          String parameterType = parameterTypes.get(i);
          if (parameterType != null
              && !invokedMethod.getParameter(i).toSourceString().equals(parameterType)) {
            return false;
          }
        }
      }
      return true;
    };
  }

  public static Predicate<InstructionSubject> isFieldAccessWithTarget(DexField target) {
    return instruction -> instruction.isFieldAccess() && instruction.getField() == target;
  }
}
