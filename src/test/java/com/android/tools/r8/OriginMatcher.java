// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public abstract class OriginMatcher extends TypeSafeMatcher<Origin> {

  public static Matcher<Origin> hasParent(Origin parent) {
    return new OriginMatcher() {
      @Override
      protected boolean matchesSafely(Origin origin) {
        Origin current = origin;
        do {
          if (current == parent) {
            return true;
          }
          current = current.parent();
        } while (current != null);
        return false;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("not a parent " + parent);
      }
    };
  }

  public static Matcher<Origin> hasPart(String part) {
    return new OriginMatcher() {
      @Override
      protected boolean matchesSafely(Origin origin) {
        return origin.part().equals(part);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("part is not " + part);
      }
    };
  }
}
