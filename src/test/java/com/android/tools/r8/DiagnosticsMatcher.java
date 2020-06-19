// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public abstract class DiagnosticsMatcher extends TypeSafeMatcher<Diagnostic> {

  public static Matcher<Diagnostic> diagnosticMessage(Matcher<String> messageMatcher) {
    return new DiagnosticsMatcher() {
      @Override
      protected boolean eval(Diagnostic diagnostic) {
        return messageMatcher.matches(diagnostic.getDiagnosticMessage());
      }

      @Override
      protected void explain(Description description) {
        description.appendText("message with ");
        messageMatcher.describeTo(description);
      }
    };
  }

  public static Matcher<Diagnostic> diagnosticType(Class<? extends Diagnostic> type) {
    return new DiagnosticsMatcher() {
      @Override
      protected boolean eval(Diagnostic diagnostic) {
        return type.isInstance(diagnostic);
      }

      @Override
      protected void explain(Description description) {
        description.appendText("type ").appendText(type.getSimpleName());
      }
    };
  }

  public static Matcher<Diagnostic> diagnosticException(Class<? extends Throwable> exception) {
    return new DiagnosticsMatcher() {
      @Override
      protected boolean eval(Diagnostic diagnostic) {
        return diagnostic instanceof ExceptionDiagnostic
            && exception.isInstance(((ExceptionDiagnostic) diagnostic).getCause());
      }

      @Override
      protected void explain(Description description) {
        description.appendText("exception type ").appendText(exception.getSimpleName());
      }
    };
  }

  public static Matcher<Diagnostic> diagnosticOrigin(Origin origin) {
    return new DiagnosticsMatcher() {
      @Override
      protected boolean eval(Diagnostic diagnostic) {
        return diagnostic.getOrigin().equals(origin);
      }

      @Override
      protected void explain(Description description) {
        description.appendText("origin ").appendText(origin.toString());
      }
    };
  }

  public static Matcher<Diagnostic> diagnosticPosition(Position position) {
    return diagnosticPosition(CoreMatchers.equalTo(position));
  }

  public static Matcher<Diagnostic> diagnosticPosition(Matcher<Position> positionMatcher) {
    return new DiagnosticsMatcher() {
      @Override
      protected boolean eval(Diagnostic diagnostic) {
        return positionMatcher.matches(diagnostic.getPosition());
      }

      @Override
      protected void explain(Description description) {
        description.appendText("position ");
        positionMatcher.describeTo(description);
      }
    };
  }

  @Override
  protected boolean matchesSafely(Diagnostic diagnostic) {
    return eval(diagnostic);
  }

  @Override
  public void describeTo(Description description) {
    explain(description.appendText("a diagnostic "));
  }

  protected abstract boolean eval(Diagnostic diagnostic);

  protected abstract void explain(Description description);
}
