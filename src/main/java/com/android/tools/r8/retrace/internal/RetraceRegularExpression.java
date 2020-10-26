// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy.ClassNameType;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy.StackTraceElementStringProxyBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RetraceRegularExpression implements StackTraceVisitor<StackTraceElementStringProxy> {

  private final RetracerImpl retracer;
  private final List<String> stackTrace;
  private final String regularExpression;

  private static final int NO_MATCH = -1;

  private final SourceFileLineNumberGroup sourceFileLineNumberGroup =
      new SourceFileLineNumberGroup();
  private final TypeNameGroup typeNameGroup = new TypeNameGroup();
  private final BinaryNameGroup binaryNameGroup = new BinaryNameGroup();
  private final SourceFileGroup sourceFileGroup = new SourceFileGroup();
  private final LineNumberGroup lineNumberGroup = new LineNumberGroup();
  private final FieldOrReturnTypeGroup fieldOrReturnTypeGroup = new FieldOrReturnTypeGroup();
  private final MethodArgumentsGroup methodArgumentsGroup = new MethodArgumentsGroup();
  private final MethodNameGroup methodNameGroup = new MethodNameGroup();
  private final FieldNameGroup fieldNameGroup = new FieldNameGroup();

  private static final String CAPTURE_GROUP_PREFIX = "captureGroup";
  private static final int FIRST_CAPTURE_GROUP_INDEX = 0;

  public RetraceRegularExpression(
      RetracerImpl retracer, List<String> stackTrace, String regularExpression) {
    this.retracer = retracer;
    this.stackTrace = stackTrace;
    this.regularExpression = regularExpression;
  }

  @Override
  public void forEach(Consumer<StackTraceElementStringProxy> consumer) {
    List<RegularExpressionGroupHandler> handlers = new ArrayList<>();
    StringBuilder refinedRegularExpressionBuilder = new StringBuilder();
    registerGroups(
        this.regularExpression,
        refinedRegularExpressionBuilder,
        handlers,
        FIRST_CAPTURE_GROUP_INDEX);
    String refinedRegularExpression = refinedRegularExpressionBuilder.toString();
    Pattern compiledPattern = Pattern.compile(refinedRegularExpression);
    for (String string : stackTrace) {
      StackTraceElementStringProxyBuilder proxyBuilder =
          StackTraceElementStringProxy.builder(string);
      Matcher matcher = compiledPattern.matcher(string);
      if (matcher.matches()) {
        boolean seenMatchedClassHandler = false;
        for (RegularExpressionGroupHandler handler : handlers) {
          if (seenMatchedClassHandler && handler.isClassHandler()) {
            continue;
          }
          if (handler.matchHandler(proxyBuilder, matcher)) {
            seenMatchedClassHandler |= handler.isClassHandler();
          }
        }
      }
      consumer.accept(proxyBuilder.build());
    }
  }

  private int registerGroups(
      String regularExpression,
      StringBuilder refinedRegularExpression,
      List<RegularExpressionGroupHandler> handlers,
      int captureGroupIndex) {
    int lastCommittedIndex = 0;
    boolean seenPercentage = false;
    boolean escaped = false;
    for (int i = 0; i < regularExpression.length(); i++) {
      if (seenPercentage) {
        assert !escaped;
        final RegularExpressionGroup group = getGroupFromVariable(regularExpression.charAt(i));
        refinedRegularExpression.append(regularExpression, lastCommittedIndex, i - 1);
        if (group.isSynthetic()) {
          captureGroupIndex =
              registerGroups(
                  group.subExpression(), refinedRegularExpression, handlers, captureGroupIndex);
        } else {
          String captureGroupName = CAPTURE_GROUP_PREFIX + (captureGroupIndex++);
          refinedRegularExpression
              .append("(?<")
              .append(captureGroupName)
              .append(">")
              .append(group.subExpression())
              .append(")");
          handlers.add(group.createHandler(captureGroupName));
        }
        lastCommittedIndex = i + 1;
        seenPercentage = false;
      } else {
        seenPercentage = !escaped && regularExpression.charAt(i) == '%';
        escaped = !escaped && regularExpression.charAt(i) == '\\';
      }
    }
    refinedRegularExpression.append(
        regularExpression, lastCommittedIndex, regularExpression.length());
    return captureGroupIndex;
  }

  private boolean isTypeOrBinarySeparator(String regularExpression, int startIndex, int endIndex) {
    assert endIndex < regularExpression.length();
    if (startIndex + 1 != endIndex) {
      return false;
    }
    if (regularExpression.charAt(startIndex) != '\\') {
      return false;
    }
    return regularExpression.charAt(startIndex + 1) == '.'
        || regularExpression.charAt(startIndex + 1) == '/';
  }

  private RegularExpressionGroup getGroupFromVariable(char variable) {
    switch (variable) {
      case 'c':
        return typeNameGroup;
      case 'C':
        return binaryNameGroup;
      case 'm':
        return methodNameGroup;
      case 'f':
        return fieldNameGroup;
      case 's':
        return sourceFileGroup;
      case 'l':
        return lineNumberGroup;
      case 'S':
        return sourceFileLineNumberGroup;
      case 't':
        return fieldOrReturnTypeGroup;
      case 'a':
        return methodArgumentsGroup;
      default:
        throw new Unreachable("Unexpected variable: " + variable);
    }
  }

  private interface RegularExpressionGroupHandler {

    boolean matchHandler(StackTraceElementStringProxyBuilder builder, Matcher matcher);

    default boolean isClassHandler() {
      return false;
    }
  }

  private abstract static class RegularExpressionGroup {

    abstract String subExpression();

    abstract RegularExpressionGroupHandler createHandler(String captureGroup);

    boolean isSynthetic() {
      return false;
    }
  }

  // TODO(b/145731185): Extend support for identifiers with strings inside back ticks.
  private static final String javaIdentifierSegment =
      "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";

  private static final String METHOD_NAME_REGULAR_EXPRESSION =
      "(?:(" + javaIdentifierSegment + "|\\<init\\>|\\<clinit\\>))";

  abstract static class ClassNameGroup extends RegularExpressionGroup {

    abstract ClassNameType getClassNameType();

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return new RegularExpressionGroupHandler() {
        @Override
        public boolean matchHandler(StackTraceElementStringProxyBuilder builder, Matcher matcher) {
          int startOfGroup = matcher.start(captureGroup);
          if (startOfGroup == NO_MATCH) {
            return false;
          }
          String typeName = matcher.group(captureGroup);
          if (typeName.equals("Suppressed")) {
            // Ensure we do not map supressed.
            return false;
          }
          builder.registerClassName(startOfGroup, matcher.end(captureGroup), getClassNameType());
          return true;
        }

        @Override
        public boolean isClassHandler() {
          return true;
        }
      };
    }
  }

  private static class TypeNameGroup extends ClassNameGroup {

    @Override
    String subExpression() {
      return "(" + javaIdentifierSegment + "\\.)*" + javaIdentifierSegment;
    }

    @Override
    ClassNameType getClassNameType() {
      return ClassNameType.TYPENAME;
    }
  }

  private static class BinaryNameGroup extends ClassNameGroup {

    @Override
    String subExpression() {
      return "(?:" + javaIdentifierSegment + "\\/)*" + javaIdentifierSegment;
    }

    @Override
    ClassNameType getClassNameType() {
      return ClassNameType.BINARY;
    }
  }

  private static class MethodNameGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return METHOD_NAME_REGULAR_EXPRESSION;
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return (builder, matcher) -> {
        int startOfGroup = matcher.start(captureGroup);
        if (startOfGroup == NO_MATCH) {
          return false;
        }
        builder.registerMethodName(startOfGroup, matcher.end(captureGroup));
        return true;
      };
    }
  }

  private static class FieldNameGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return javaIdentifierSegment;
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return (builder, matcher) -> {
        int startOfGroup = matcher.start(captureGroup);
        if (startOfGroup == NO_MATCH) {
          return false;
        }
        builder.registerFieldName(startOfGroup, matcher.end(captureGroup));
        return true;
      };
    }
  }

  private static class SourceFileGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return "(?:(\\w*[\\. ])?(\\w*)?)";
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return (builder, matcher) -> {
        int startOfGroup = matcher.start(captureGroup);
        if (startOfGroup == NO_MATCH) {
          return false;
        }
        builder.registerSourceFile(startOfGroup, matcher.end(captureGroup));
        return true;
      };
    }
  }

  private static class LineNumberGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return "\\d*";
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return (builder, matcher) -> {
        int startOfGroup = matcher.start(captureGroup);
        if (startOfGroup == NO_MATCH) {
          return false;
        }
        builder.registerLineNumber(startOfGroup, matcher.end(captureGroup));
        return true;
      };
    }
  }

  private static class SourceFileLineNumberGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return "%s(?::%l)?";
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      throw new Unreachable("Should never be called");
    }

    @Override
    boolean isSynthetic() {
      return true;
    }
  }

  private static final String JAVA_TYPE_REGULAR_EXPRESSION =
      "(" + javaIdentifierSegment + "\\.)*" + javaIdentifierSegment + "[\\[\\]]*";

  private static class FieldOrReturnTypeGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return JAVA_TYPE_REGULAR_EXPRESSION;
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return (builder, matcher) -> {
        int startOfGroup = matcher.start(captureGroup);
        if (startOfGroup == NO_MATCH) {
          return false;
        }
        builder.registerFieldOrReturnType(startOfGroup, matcher.end(captureGroup));
        return true;
      };
    }
  }

  private static class MethodArgumentsGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return "((" + JAVA_TYPE_REGULAR_EXPRESSION + "\\,)*" + JAVA_TYPE_REGULAR_EXPRESSION + ")?";
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return (builder, matcher) -> {
        int startOfGroup = matcher.start(captureGroup);
        if (startOfGroup == NO_MATCH) {
          return false;
        }
        builder.registerMethodArguments(startOfGroup, matcher.end(captureGroup));
        return true;
      };
    }
  }
}

