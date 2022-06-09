// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup.utils;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.StringUtils;
import java.util.Collection;
import java.util.Iterator;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class StartupTestingMatchers {

  public static Matcher<Collection<DexProgramClass>> isEqualToClassDataLayout(
      Collection<ClassReference> expectedLayout) {
    return new TypeSafeMatcher<Collection<DexProgramClass>>() {
      @Override
      protected boolean matchesSafely(Collection<DexProgramClass> actualLayout) {
        if (actualLayout.size() != expectedLayout.size()) {
          return false;
        }
        Iterator<ClassReference> expectedLayoutIterator = expectedLayout.iterator();
        for (DexProgramClass clazz : actualLayout) {
          if (!clazz.getClassReference().equals(expectedLayoutIterator.next())) {
            return false;
          }
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "is equal to ["
                + StringUtils.join(", ", expectedLayout, TypeReference::getTypeName)
                + "]");
      }

      @Override
      public void describeMismatchSafely(
          Collection<DexProgramClass> actualLayout, Description description) {
        description
            .appendText("class data layout was not: ")
            .appendText("[")
            .appendText(StringUtils.join(", ", actualLayout, DexClass::getTypeName))
            .appendText("]");
      }
    };
  }
}
