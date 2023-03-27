// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class AnnotationMatchers {

  @SafeVarargs
  public static Matcher<MethodSubject> hasParameterAnnotationTypes(
      List<TypeSubject>... typeSubjects) {
    return hasParameterAnnotationTypes(Arrays.asList(typeSubjects));
  }

  public static Matcher<MethodSubject> hasParameterAnnotationTypes(
      List<List<TypeSubject>> typeSubjects) {
    return new TypeSafeMatcher<MethodSubject>() {

      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        List<List<FoundAnnotationSubject>> parameterAnnotations = subject.getParameterAnnotations();
        if (parameterAnnotations.size() != typeSubjects.size()) {
          return false;
        }
        for (int parameterIndex = 0;
            parameterIndex < parameterAnnotations.size();
            parameterIndex++) {
          List<FoundAnnotationSubject> parameterAnnotationsForParameter =
              parameterAnnotations.get(parameterIndex);
          List<TypeSubject> typeSubjectsForParameter = typeSubjects.get(parameterIndex);
          if (!hasAnnotationTypes(typeSubjectsForParameter)
              .matches(parameterAnnotationsForParameter)) {
            return false;
          }
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "has parameter annotation types ["
                + StringUtils.join(
                    "; ",
                    typeSubjects,
                    typeSubjectsForParameter ->
                        StringUtils.join(", ", typeSubjectsForParameter, TypeSubject::getTypeName))
                + "]");
      }

      @Override
      public void describeMismatchSafely(MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<List<FoundAnnotationSubject>> hasAnnotationTypes(
      TypeSubject... typeSubjects) {
    return hasAnnotationTypes(Arrays.asList(typeSubjects));
  }

  public static Matcher<List<FoundAnnotationSubject>> hasAnnotationTypes(
      List<TypeSubject> typeSubjects) {
    return new TypeSafeMatcher<List<FoundAnnotationSubject>>() {

      @Override
      protected boolean matchesSafely(List<FoundAnnotationSubject> subjects) {
        if (subjects.size() != typeSubjects.size()) {
          return false;
        }
        for (int i = 0; i < subjects.size(); i++) {
          FoundAnnotationSubject subject = subjects.get(i);
          TypeSubject typeSubject = typeSubjects.get(i);
          if (!subject.getType().equals(typeSubject)) {
            return false;
          }
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "has annotation types ["
                + StringUtils.join(", ", typeSubjects, TypeSubject::getTypeName)
                + "]");
      }

      @Override
      public void describeMismatchSafely(
          List<FoundAnnotationSubject> subjects, Description description) {
        description.appendText("annotations did not");
      }
    };
  }

  @SafeVarargs
  public static Matcher<FoundAnnotationSubject> hasElements(
      Pair<String, String>... expectedElements) {
    return new TypeSafeMatcher<FoundAnnotationSubject>() {

      @Override
      protected boolean matchesSafely(FoundAnnotationSubject subject) {
        if (expectedElements.length != subject.getAnnotation().elements.length) {
          return false;
        }
        for (int i = 0; i < expectedElements.length; i++) {
          DexAnnotationElement element = subject.getAnnotation().elements[i];
          if (!element.name.toString().equals(expectedElements[i].getFirst())
              || !element.value.isDexValueString()
              || !element
                  .value
                  .asDexValueString()
                  .getValue()
                  .toString()
                  .equals(expectedElements[i].getSecond())) {
            return false;
          }
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        StringBuilder builder = new StringBuilder("has elements ");
        for (Pair<String, String> expectedElement : expectedElements) {
          builder
              .append(expectedElement.getFirst())
              .append(" = ")
              .append(expectedElement.getSecond())
              .append(", ");
        }
        description.appendText(builder.toString());
      }

      @Override
      public void describeMismatchSafely(FoundAnnotationSubject subject, Description description) {
        description.appendText("annotation did not");
      }
    };
  }
}
