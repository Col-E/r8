// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceMethodResult;
import com.android.tools.r8.retrace.RetraceMethodResult.Element;
import com.android.tools.r8.utils.Box;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
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
    } else if (subject instanceof AnnotationSubject) {
      type = "annotation";
    } else if (subject instanceof KmClassSubject) {
      type = "@Metadata.KmClass";
    } else if (subject instanceof KmPackageSubject) {
      type = "@Metadata.KmPackage";
    } else if (subject instanceof KmFunctionSubject) {
      type = "@Metadata.KmFunction";
    } else if (subject instanceof KmPropertySubject) {
      type = "@Metadata.KmProperty";
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
    } else if (subject instanceof AnnotationSubject) {
      name = ((AnnotationSubject) subject).getAnnotation().type.toSourceString();
    } else if (subject instanceof KmClassSubject) {
      name = ((KmClassSubject) subject).getDexClass().toSourceString();
    } else if (subject instanceof KmPackageSubject) {
      name = ((KmPackageSubject) subject).getDexClass().toSourceString();
    } else if (subject instanceof KmFunctionSubject) {
      name = ((KmFunctionSubject) subject).toString();
    } else if (subject instanceof KmPropertySubject) {
      name = ((KmPropertySubject) subject).toString();
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

  public static Matcher<Subject> isRenamed(boolean isRenamed) {
    return isRenamed ? isRenamed() : isNotRenamed();
  }

  public static Matcher<MemberSubject> isStatic() {
    return new TypeSafeMatcher<MemberSubject>() {
      @Override
      public boolean matchesSafely(final MemberSubject subject) {
        return subject.isPresent() && subject.isStatic();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText(" present");
      }

      @Override
      public void describeMismatchSafely(final MemberSubject subject, Description description) {
        description
            .appendText(type(subject) + " ")
            .appendValue(name(subject))
            .appendText(" was not");
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

  public static Matcher<KmFunctionSubject> isExtensionFunction() {
    return new TypeSafeMatcher<KmFunctionSubject>() {
      @Override
      protected boolean matchesSafely(KmFunctionSubject kmFunction) {
        return kmFunction.isPresent() && kmFunction.isExtension();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is extension function");
      }

      @Override
      public void describeMismatchSafely(
          final KmFunctionSubject kmFunction, Description description) {
        description
            .appendText("kmFunction ")
            .appendValue(kmFunction)
            .appendText(" was not");
      }
    };
  }

  public static Matcher<KmPropertySubject> isExtensionProperty() {
    return new TypeSafeMatcher<KmPropertySubject>() {
      @Override
      protected boolean matchesSafely(KmPropertySubject kmProperty) {
        return kmProperty.isPresent() && kmProperty.isExtension();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is extension property");
      }

      @Override
      public void describeMismatchSafely(
          final KmPropertySubject kmProperty, Description description) {
        description
            .appendText("kmProperty ")
            .appendValue(kmProperty)
            .appendText(" was not");
      }
    };
  }

  public static Matcher<RetraceMethodResult> isInlineFrame() {
    return new TypeSafeMatcher<RetraceMethodResult>() {
      @Override
      protected boolean matchesSafely(RetraceMethodResult item) {
        return !item.isAmbiguous() && item.stream().count() > 1;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is not an inline frame");
      }
    };
  }

  public static Matcher<RetraceMethodResult> isInlineStack(InlinePosition startPosition) {
    return new TypeSafeMatcher<RetraceMethodResult>() {
      @Override
      protected boolean matchesSafely(RetraceMethodResult item) {
        Box<InlinePosition> currentPosition = new Box<>(startPosition);
        Box<Boolean> returnValue = new Box<>();
        item.forEach(
            element -> {
              boolean sameMethod;
              InlinePosition currentInline = currentPosition.get();
              if (currentInline == null) {
                returnValue.set(false);
                return;
              }
              sameMethod = element.getMethodReference().equals(currentInline.methodReference);
              boolean samePosition =
                  element.getOriginalLineNumber(currentInline.minifiedPosition)
                      == currentInline.originalPosition;
              if (!returnValue.isSet() || returnValue.get()) {
                returnValue.set(sameMethod & samePosition);
              }
              currentPosition.set(currentInline.caller);
            });
        return returnValue.isSet() && returnValue.get();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is not matching the inlining stack");
      }
    };
  }

  public static Matcher<RetraceMethodResult> isTopOfStackTrace(
      StackTrace stackTrace, List<Integer> minifiedPositions) {
    return new TypeSafeMatcher<RetraceMethodResult>() {
      @Override
      protected boolean matchesSafely(RetraceMethodResult item) {
        List<Element> retraceElements = item.stream().collect(Collectors.toList());
        if (retraceElements.size() > stackTrace.size()
            || retraceElements.size() != minifiedPositions.size()) {
          return false;
        }
        for (int i = 0; i < retraceElements.size(); i++) {
          Element retraceElement = retraceElements.get(i);
          StackTraceLine stackTraceLine = stackTrace.get(i);
          MethodReference methodReference = retraceElement.getMethodReference();
          if (!stackTraceLine.methodName.equals(methodReference.getMethodName())
              || !stackTraceLine.className.equals(methodReference.getHolderClass().getTypeName())
              || stackTraceLine.lineNumber
                  != retraceElement.getOriginalLineNumber(minifiedPositions.get(i))) {
            return false;
          }
        }
        return true;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is not matching the stack trace");
      }
    };
  }

  public static Matcher<StackTrace> containsInlinePosition(InlinePosition inlinePosition) {
    return new TypeSafeMatcher<StackTrace>() {
      @Override
      protected boolean matchesSafely(StackTrace item) {
        return containsInlineStack(item, 0, inlinePosition);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("cannot be found in stack trace");
      }

      private boolean containsInlineStack(
          StackTrace stackTrace, int index, InlinePosition currentPosition) {
        if (currentPosition == null) {
          return true;
        }
        if (index >= stackTrace.size()) {
          return false;
        }
        StackTraceLine stackTraceLine = stackTrace.get(index);
        boolean resultHere =
            stackTraceLine.className.equals(currentPosition.getClassName())
                && stackTraceLine.methodName.equals(currentPosition.getMethodName())
                && stackTraceLine.lineNumber == currentPosition.originalPosition;
        if (resultHere && containsInlineStack(stackTrace, index + 1, currentPosition.caller)) {
          return true;
        }
        // Maybe the inline position starts from the top on the next position.
        return containsInlineStack(stackTrace, index + 1, inlinePosition);
      }
    };
  }

  public static class InlinePosition {
    private final MethodReference methodReference;
    private final int minifiedPosition;
    private final int originalPosition;

    private InlinePosition caller;

    private InlinePosition(
        MethodReference methodReference, int minifiedPosition, int originalPosition) {
      this.methodReference = methodReference;
      this.minifiedPosition = minifiedPosition;
      this.originalPosition = originalPosition;
    }

    public static InlinePosition create(
        MethodReference methodReference, int minifiedPosition, int originalPosition) {
      return new InlinePosition(methodReference, minifiedPosition, originalPosition);
    }

    public static InlinePosition create(
        FoundMethodSubject methodSubject, int minifiedPosition, int originalPosition) {
      return create(methodSubject.asMethodReference(), minifiedPosition, originalPosition);
    }

    public static InlinePosition stack(InlinePosition... stack) {
      setCaller(1, stack);
      return stack[0];
    }

    private static void setCaller(int index, InlinePosition... stack) {
      assert index > 0;
      if (index >= stack.length) {
        return;
      }
      stack[index - 1].caller = stack[index];
      setCaller(index + 1, stack);
    }

    String getMethodName() {
      return methodReference.getMethodName();
    }

    String getClassName() {
      return methodReference.getHolderClass().getTypeName();
    }
  }
}
