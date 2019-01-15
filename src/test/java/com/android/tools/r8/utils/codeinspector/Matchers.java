// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessFlags;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class Matchers {

  private enum Visibility {
    PUBLIC,
    PROTECTED,
    PRIVATE,
    PACKAGE_PRIVATE;

    @Override
    public String toString() {
      switch (this) {
        case PUBLIC:
          return "public";

        case PROTECTED:
          return "protected";

        case PRIVATE:
          return "private";

        case PACKAGE_PRIVATE:
          return "package-private";

        default:
          throw new Unreachable("Unexpected visibility");
      }
    }
  }

  private static String type(Subject subject) {
    String type = "<unknown subject type>";
    if (subject instanceof ClassSubject) {
      type = "class";
    } else if (subject instanceof MethodSubject) {
      type = "method";
    } else if (subject instanceof FieldSubject) {
      type = "field";
    }
    return type;
  }

  private static String name(Subject subject) {
    String name = "<unknown>";
    if (subject instanceof ClassSubject) {
      name = ((ClassSubject) subject).getOriginalName();
    } else if (subject instanceof MethodSubject) {
      name = ((MethodSubject) subject).getOriginalName();
    } else if (subject instanceof FieldSubject) {
      name = ((FieldSubject) subject).getOriginalName();
    }
    return name;
  }

  public static Matcher<MethodSubject> isBridge() {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        return subject.isPresent() && subject.isBridge();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(" bridge");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description
            .appendText(type(subject) + " ")
            .appendValue(name(subject))
            .appendText(" was not");
      }
    };
  }

  public static Matcher<Subject> isPresent() {
    return new TypeSafeMatcher<Subject>() {
      @Override
      public boolean matchesSafely(final Subject subject) {
        return subject.isPresent();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText(" present");
      }

      @Override
      public void describeMismatchSafely(final Subject subject, Description description) {
        description
            .appendText(type(subject) + " ").appendValue(name(subject)).appendText(" was not");
      }
    };
  }

  public static Matcher<Subject> isRenamed() {
    return new TypeSafeMatcher<Subject>() {
      @Override
      protected boolean matchesSafely(Subject subject) {
        return subject.isPresent() && subject.isRenamed();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(" renamed");
      }

      @Override
      public void describeMismatchSafely(final Subject subject, Description description) {
        description
            .appendText(type(subject) + " ").appendValue(name(subject)).appendText(" was not");
      }
    };
  }

  public static Matcher<Subject> isNotRenamed() {
    return new TypeSafeMatcher<Subject>() {
      @Override
      protected boolean matchesSafely(Subject subject) {
        return subject.isPresent() && !subject.isRenamed();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(" not renamed");
      }

      @Override
      public void describeMismatchSafely(final Subject subject, Description description) {
        description
            .appendText(type(subject) + " ").appendValue(name(subject)).appendText(" was");
      }
    };
  }

  public static Matcher<Subject> isSynthetic() {
    return new TypeSafeMatcher<Subject>() {
      @Override
      protected boolean matchesSafely(Subject subject) {
        return subject.isPresent() && subject.isSynthetic();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(" synthetic");
      }

      @Override
      public void describeMismatchSafely(final Subject subject, Description description) {
        description
            .appendText(type(subject) + " ")
            .appendValue(name(subject))
            .appendText(" was not");
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

  public static Matcher<ClassSubject> isMemberClass() {
    return new TypeSafeMatcher<ClassSubject>() {
      @Override
      public boolean matchesSafely(final ClassSubject clazz) {
        return clazz.isMemberClass();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("is member class");
      }

      @Override
      public void describeMismatchSafely(final ClassSubject clazz, Description description) {
        description.appendText("class ").appendValue(clazz.getOriginalName()).appendText(" is not");
      }
    };
  }

  public static Matcher<MethodSubject> isAbstract() {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      public boolean matchesSafely(final MethodSubject method) {
        return method.isPresent() && method.isAbstract();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("method abstract");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject method, Description description) {
        description
            .appendText("method ").appendValue(method.getOriginalName()).appendText(" was not");
      }
    };
  }

  public static Matcher<MethodSubject> isFinal() {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      public boolean matchesSafely(final MethodSubject method) {
        return method.isPresent() && method.isFinal();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("is final");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject method, Description description) {
        description
            .appendText("method ")
            .appendValue(method.getOriginalName())
            .appendText(" was not");
      }
    };
  }

  public static <T extends MemberSubject> Matcher<T> isPrivate() {
    return hasVisibility(Visibility.PRIVATE);
  }

  public static <T extends MemberSubject> Matcher<T> isPublic() {
    return hasVisibility(Visibility.PUBLIC);
  }

  private static <T extends MemberSubject> Matcher<T> hasVisibility(Visibility visibility) {
    return new TypeSafeMatcher<T>() {
      @Override
      public boolean matchesSafely(final T subject) {
        if (subject.isPresent()) {
          switch (visibility) {
            case PUBLIC:
              return subject.isPublic();

            case PROTECTED:
              return subject.isProtected();

            case PRIVATE:
              return subject.isPrivate();

            case PACKAGE_PRIVATE:
              return subject.isPackagePrivate();

            default:
              throw new Unreachable("Unexpected visibility: " + visibility);
          }
        }
        return false;
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("method " + visibility);
      }

      @Override
      public void describeMismatchSafely(final T subject, Description description) {
        description
            .appendText("method ")
            .appendValue(subject.getOriginalName())
            .appendText(" was ");
        if (subject.isPresent()) {
          AccessFlags accessFlags =
              subject.isMethodSubject()
                  ? subject.asMethodSubject().getMethod().accessFlags
                  : subject.asFieldSubject().getField().accessFlags;
          if (accessFlags.isPublic()) {
            description.appendText("public");
          } else if (accessFlags.isProtected()) {
            description.appendText("protected");
          } else if (accessFlags.isPrivate()) {
            description.appendText("private");
          } else {
            description.appendText("package-private");
          }
        } else {
          description.appendText(" was absent");
        }
      }
    };
  }
}
