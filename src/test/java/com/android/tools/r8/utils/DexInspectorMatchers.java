// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class DexInspectorMatchers {

  public static Matcher<ClassSubject> isPresent() {
    return new TypeSafeMatcher<ClassSubject>() {
      @Override
      public boolean matchesSafely(final ClassSubject clazz) {
        return clazz.isPresent();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("class present");
      }

      @Override
      public void describeMismatchSafely(final ClassSubject clazz, Description description) {
        description
            .appendText("class ").appendValue(clazz.getOriginalName()).appendText(" was not");
      }
    };
  }

  public static Matcher<ClassSubject> hasDefaultConstructor() {
    return new TypeSafeMatcher<ClassSubject>() {
      @Override
      public boolean matchesSafely(final ClassSubject clazz) {
        return clazz.init(ImmutableList.of()).isPresent();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("class having default constructor");
      }

      @Override
      public void describeMismatchSafely(final ClassSubject clazz, Description description) {
        description
            .appendText("class ").appendValue(clazz.getOriginalName()).appendText(" did not");
      }
    };
  }
}
