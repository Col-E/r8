// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static org.hamcrest.CoreMatchers.anything;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CollectorsUtils;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.Visibility;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Predicate;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class Matchers {

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
    } else if (subject instanceof KmTypeParameterSubject) {
      type = "@Metadata.KmTypeParameter";
    } else if (subject instanceof KmClassifierSubject) {
      type = "@Metadata.KmClassifier";
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
    } else if (subject instanceof KmTypeParameterSubject) {
      name = ((KmTypeParameterSubject) subject).getId() + "";
    } else if (subject instanceof KmClassifierSubject) {
      name = subject.toString();
    }
    return name;
  }

  public static Matcher<MethodSubject> isBridge() {
    return isMethodSatisfying("bridge", FoundMethodSubject::isBridge);
  }

  public static Matcher<MethodSubject> isInstanceInitializer() {
    return isMethodSatisfying("instance initializer", FoundMethodSubject::isInstanceInitializer);
  }

  private static Matcher<MethodSubject> isMethodSatisfying(
      String helperText, Predicate<FoundMethodSubject> predicate) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        return subject.isPresent() && predicate.test(subject.asFoundMethodSubject());
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(" " + helperText);
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

  public static Matcher<ClassSubject> isInterface() {
    return new TypeSafeMatcher<ClassSubject>() {
      @Override
      public boolean matchesSafely(ClassSubject subject) {
        return subject.isPresent() && subject.getAccessFlags().isInterface();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("interface");
      }

      @Override
      public void describeMismatchSafely(ClassSubject subject, Description description) {
        description
            .appendText(type(subject) + " ")
            .appendValue(name(subject))
            .appendText(" was not");
      }
    };
  }

  public static Matcher<Subject> isAbsent() {
    return not(isPresent());
  }

  public static Matcher<Subject> isAbsentIf(boolean condition) {
    return onlyIf(condition, isAbsent());
  }

  public static Matcher<Subject> isPresent() {
    return new TypeSafeMatcher<Subject>() {
      @Override
      public boolean matchesSafely(final Subject subject) {
        return subject.isPresent();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("present");
      }

      @Override
      public void describeMismatchSafely(final Subject subject, Description description) {
        if (subject instanceof ClassSubject || subject instanceof MemberSubject) {
          description
              .appendText(type(subject) + " ")
              .appendValue(name(subject))
              .appendText(" was not");
        } else {
          description.appendText(type(subject) + " ").appendText(" was not found");
        }
      }
    };
  }

  public static Matcher<Subject> isPresentIf(boolean condition) {
    return onlyIf(condition, isPresent());
  }

  public static Matcher<Subject> isPresentAndRenamed() {
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

  public static Matcher<Subject> isPresentAndNotRenamed() {
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

  public static Matcher<Subject> isPresentAndRenamed(boolean isRenamed) {
    return isRenamed ? isPresentAndRenamed() : isPresentAndNotRenamed();
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

  public static Matcher<Diagnostic> typeVariableNotInScope() {
    return DiagnosticsMatcher.diagnosticMessage(containsString("A type variable is not in scope"));
  }

  public static Matcher<Diagnostic> invalidGenericArgumentApplicationCount() {
    return DiagnosticsMatcher.diagnosticMessage(
        containsString(
            "The applied generic arguments have different count than the expected formals"));
  }

  public static Matcher<Diagnostic> proguardConfigurationRuleDoesNotMatch() {
    return DiagnosticsMatcher.diagnosticMessage(
        containsString("Proguard configuration rule does not match anything"));
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

  public static Matcher<Subject> isCompilerSynthesized() {
    return new TypeSafeMatcher<Subject>() {
      @Override
      protected boolean matchesSafely(Subject subject) {
        return subject.isPresent() && subject.isCompilerSynthesized();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(" compiler synthesized");
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

  public static Matcher<ClassOrMemberSubject> isFinal() {
    return new TypeSafeMatcher<ClassOrMemberSubject>() {
      @Override
      public boolean matchesSafely(ClassOrMemberSubject subject) {
        return subject.isPresent() && subject.isFinal();
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is final");
      }

      @Override
      public void describeMismatchSafely(ClassOrMemberSubject subject, Description description) {
        description.appendText("subject ").appendValue(subject.toString()).appendText(" was not");
      }
    };
  }

  public static <T extends MemberSubject> Matcher<T> isPrivate() {
    return hasVisibility(Visibility.PRIVATE);
  }

  public static <T extends MemberSubject> Matcher<T> isPackagePrivate() {
    return hasVisibility(Visibility.PACKAGE_PRIVATE);
  }

  public static <T extends ClassOrMemberSubject> Matcher<T> isPublic() {
    return hasVisibility(Visibility.PUBLIC);
  }

  private static <T extends ClassOrMemberSubject> Matcher<T> hasVisibility(Visibility visibility) {
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
        description.appendText("item ").appendValue(subject.getOriginalName()).appendText(" was ");
        if (subject.isPresent()) {
          AccessFlags<?> accessFlags = subject.getAccessFlags();
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
          description.appendText("absent");
        }
      }
    };
  }

  public static <T extends MethodSubject> Matcher<T> isNative() {
    return new TypeSafeMatcher<T>() {
      @Override
      public boolean matchesSafely(final T subject) {
        return subject.isPresent() && subject.isNative();
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("native method");
      }

      @Override
      public void describeMismatchSafely(final T subject, Description description) {
        description.appendText("item ").appendValue(subject.getOriginalName()).appendText(" was ");
        if (subject.isPresent()) {
          description.appendText("not native");
        } else {
          description.appendText("absent");
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

  public static Matcher<KmTypeSubject> isDexClass(DexClass clazz) {
    return new TypeSafeMatcher<KmTypeSubject>() {
      @Override
      protected boolean matchesSafely(KmTypeSubject item) {
        String descriptor = item.descriptor();
        if (descriptor == null) {
          return false;
        }
        return descriptor.equals(clazz.type.toDescriptorString());
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is class");
      }

      @Override
      protected void describeMismatchSafely(KmTypeSubject item, Description mismatchDescription) {
        mismatchDescription
            .appendText(item.descriptor())
            .appendText(" is not " + clazz.type.toDescriptorString());
      }
    };
  }

  public static Matcher<ClassSubject> isExtending(ClassSubject superSubject) {
    assertThat(superSubject, isPresent());
    assertThat(superSubject, not(isInterface()));
    return new TypeSafeMatcher<ClassSubject>() {
      @Override
      public boolean matchesSafely(ClassSubject subject) {
        return subject.isPresent() && subject.isExtending(superSubject);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("extends ").appendText(superSubject.getOriginalName());
      }
    };
  }

  public static Matcher<FieldSubject> isFieldOfType(DexType type) {
    return new TypeSafeMatcher<FieldSubject>() {
      @Override
      protected boolean matchesSafely(FieldSubject fieldSubject) {
        return fieldSubject.getDexField().type == type;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is field of type");
      }

      @Override
      protected void describeMismatchSafely(FieldSubject item, Description mismatchDescription) {
        mismatchDescription
            .appendText(item.getOriginalSignature().toString())
            .appendText(" is not of type ")
            .appendText(type.toSourceString());
      }
    };
  }

  public static Matcher<FieldSubject> isFieldOfArrayType(
      CodeInspector codeInspector, DexType type) {
    return new TypeSafeMatcher<FieldSubject>() {
      @Override
      protected boolean matchesSafely(FieldSubject fieldSubject) {
        return fieldSubject.getDexField().type.isArrayType()
            && fieldSubject.getDexField().type.toBaseType(codeInspector.getFactory()) == type;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is field of type");
      }

      @Override
      protected void describeMismatchSafely(FieldSubject item, Description mismatchDescription) {
        mismatchDescription
            .appendText(item.getOriginalSignature().toString())
            .appendText(" is not an array of type ")
            .appendText(type.toSourceString());
      }
    };
  }

  public static Matcher<ClassSubject> isImplementing(ClassSubject interfaceSubject) {
    assertThat(interfaceSubject, isPresent());
    assertThat(interfaceSubject, isInterface());
    return new TypeSafeMatcher<ClassSubject>() {
      @Override
      public boolean matchesSafely(ClassSubject subject) {
        return subject.isPresent() && subject.isImplementing(interfaceSubject);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("implements ").appendText(interfaceSubject.getOriginalName());
      }
    };
  }

  public static Matcher<MethodSubject> hasLineNumberTable() {
    return isMethodSatisfying("line number table", method -> method.getLineNumberTable() != null);
  }

  public static Matcher<RetraceFrameResult> isInlineFrame() {
    return new TypeSafeMatcher<RetraceFrameResult>() {
      @Override
      protected boolean matchesSafely(RetraceFrameResult item) {
        return !item.isAmbiguous()
            && item.stream().mapToLong(method -> method.getOuterFrames().size()).sum() > 0;
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is not an inline frame");
      }
    };
  }

  public static Matcher<RetraceFrameResult> isInlineStack(LinePosition startPosition) {
    return new TypeSafeMatcher<RetraceFrameResult>() {
      @Override
      protected boolean matchesSafely(RetraceFrameResult item) {
        RetraceFrameElement single = item.stream().collect(CollectorsUtils.toSingle());
        Box<LinePosition> currentPosition = new Box<>(startPosition);
        Box<Boolean> returnValue = new Box<>();
        single.stream()
            .forEach(
                frame -> {
                  LinePosition currentInline = currentPosition.get();
                  if (currentInline == null) {
                    returnValue.set(false);
                    return;
                  }
                  RetracedMethodReference method = frame.getMethodReference();
                  if (method.isUnknown()) {
                    returnValue.set(false);
                    return;
                  }
                  boolean sameMethod =
                      method.asKnown().getMethodReference().equals(currentInline.methodReference);
                  boolean samePosition =
                      method.getOriginalPositionOrDefault(currentInline.minifiedPosition)
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

  public static Matcher<RetraceFrameResult> isTopOfStackTrace(
      StackTrace stackTrace, List<Integer> minifiedPositions) {
    return new TypeSafeMatcher<RetraceFrameResult>() {
      @Override
      protected boolean matchesSafely(RetraceFrameResult item) {
        RetraceFrameElement single = item.stream().collect(CollectorsUtils.toSingle());
        return !single.stream()
            .anyMatch(
                frame -> {
                  StackTraceLine stackTraceLine = stackTrace.get(frame.getIndex());
                  RetracedMethodReference method = frame.getMethodReference();
                  return !stackTraceLine.methodName.equals(method.getMethodName())
                      || !stackTraceLine.className.equals(method.getHolderClass().getTypeName())
                      || stackTraceLine.lineNumber
                          != method.getOriginalPositionOrDefault(
                              minifiedPositions.get(frame.getIndex()));
                });
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("is not matching the stack trace");
      }
    };
  }

  public static Matcher<StackTrace> containsLinePositions(LinePosition linePosition) {
    return new TypeSafeMatcher<StackTrace>() {
      @Override
      protected boolean matchesSafely(StackTrace item) {
        return containsLinePosition(item, 0, linePosition);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(linePosition + " cannot be found in stack trace");
      }

      private boolean containsLinePosition(
          StackTrace stackTrace, int index, LinePosition linePosition) {
        if (linePosition == null) {
          return true;
        }
        Matcher<StackTraceLine> lineMatcher = Matchers.matchesLinePosition(linePosition);
        for (int i = index; i < stackTrace.getStackTraceLines().size(); i++) {
          StackTraceLine stackTraceLine = stackTrace.get(i);
          if (lineMatcher.matches(stackTraceLine)) {
            return containsLinePosition(stackTrace, index + 1, linePosition.caller);
          }
        }
        return false;
      }
    };
  }

  public static Matcher<StackTraceLine> matchesLinePosition(LinePosition linePosition) {
    return new TypeSafeMatcher<StackTraceLine>() {

      @Override
      protected boolean matchesSafely(StackTraceLine item) {
        return containsLinePosition(item, linePosition);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(linePosition + " cannot be found in stack trace");
      }

      private boolean containsLinePosition(
          StackTraceLine stackTraceLine, LinePosition currentPosition) {
        return stackTraceLine.className.equals(currentPosition.getClassName())
            && stackTraceLine.methodName.equals(currentPosition.getMethodName())
            && stackTraceLine.lineNumber == currentPosition.originalPosition
            && stackTraceLine.fileName.equals(currentPosition.filename);
      }
    };
  }

  public static Matcher<MethodSubject> writesInstanceField(DexField field) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject methodSubject) {
        return methodSubject
            .streamInstructions()
            .filter(InstructionSubject::isInstancePut)
            .map(InstructionSubject::getField)
            .anyMatch(field::equals);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "The field " + field.name.toSourceString() + " is not written by the method.");
      }
    };
  }

  public static Matcher<MethodSubject> readsInstanceField(DexField field) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject methodSubject) {
        return methodSubject
            .streamInstructions()
            .filter(InstructionSubject::isInstanceGet)
            .map(InstructionSubject::getField)
            .anyMatch(field::equals);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "The field " + field.name.toSourceString() + " is not read by the method.");
      }
    };
  }

  public static class LinePosition {
    private final MethodReference methodReference;
    private final int minifiedPosition;
    private final int originalPosition;
    private final String filename;

    private LinePosition caller;

    private LinePosition(
        MethodReference methodReference,
        int minifiedPosition,
        int originalPosition,
        String filename) {
      this.methodReference = methodReference;
      this.minifiedPosition = minifiedPosition;
      this.originalPosition = originalPosition;
      this.filename = filename;
    }

    public static LinePosition create(
        MethodReference methodReference,
        int minifiedPosition,
        int originalPosition,
        String filename) {
      return new LinePosition(methodReference, minifiedPosition, originalPosition, filename);
    }

    public static LinePosition create(
        FoundMethodSubject methodSubject,
        int minifiedPosition,
        int originalPosition,
        String filename) {
      return create(
          methodSubject.asMethodReference(), minifiedPosition, originalPosition, filename);
    }

    public static LinePosition stack(LinePosition... stack) {
      setCaller(1, stack);
      return stack[0];
    }

    private static void setCaller(int index, LinePosition... stack) {
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

    @Override
    public String toString() {
      return getClassName() + "." + getMethodName() + "(" + filename + ":" + originalPosition + ")";
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> Matcher<T> ifThen(boolean condition, Matcher<T> matcher) {
    if (condition) {
      return matcher;
    }
    return (Matcher<T>) anything();
  }

  public static <T> Matcher<T> onlyIf(boolean condition, Matcher<T> matcher) {
    return notIf(matcher, !condition);
  }

  public static <T> Matcher<T> notIf(Matcher<T> matcher, boolean condition) {
    if (condition) {
      return not(matcher);
    }
    return matcher;
  }
}
