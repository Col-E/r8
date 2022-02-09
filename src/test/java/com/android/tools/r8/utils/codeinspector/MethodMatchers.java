// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.utils.StringUtils;
import java.util.Arrays;
import java.util.List;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class MethodMatchers {

  public static Matcher<MethodSubject> hasParameters(TypeSubject... expectedParameters) {
    return hasParameters(Arrays.asList(expectedParameters));
  }

  public static Matcher<MethodSubject> hasParameters(List<TypeSubject> expectedParameters) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject methodSubject) {
        if (!methodSubject.isPresent()) {
          return false;
        }
        if (methodSubject.getParameters().size() != expectedParameters.size()) {
          return false;
        }
        for (int i = 0; i < expectedParameters.size(); i++) {
          TypeSubject actualParameter = methodSubject.getParameter(i);
          TypeSubject expectedParameter = expectedParameters.get(i);
          assertEquals(expectedParameter, actualParameter);
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "has parameters ("
                + StringUtils.join(", ", expectedParameters, TypeSubject::getTypeName)
                + ")");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }
}
