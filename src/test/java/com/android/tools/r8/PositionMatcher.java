// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public abstract class PositionMatcher extends TypeSafeMatcher<Position> {

  public static Matcher<Position> positionLine(int line) {
    return new PositionMatcher() {
      @Override
      protected boolean matchesSafely(Position position) {
        return position instanceof TextPosition && ((TextPosition) position).getLine() == line;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("with line " + line);
      }
    };
  }
}
