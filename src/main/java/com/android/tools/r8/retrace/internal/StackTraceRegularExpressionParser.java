// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.retrace.StackTraceLineParser;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy.ClassNameType;
import com.android.tools.r8.retrace.internal.StackTraceElementStringProxy.StackTraceElementStringProxyBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StackTraceRegularExpressionParser
    implements StackTraceLineParser<String, StackTraceElementStringProxy> {

  // This is a slight modification of the default regular expression shown for proguard retrace
  // that allow for retracing classes in the form <class>: lorem ipsum...
  // Seems like Proguard retrace is expecting the form "Caused by: <class>".
  public static final String DEFAULT_REGULAR_EXPRESSION =
      "(?:.*?\\bat\\s+%c\\.%m\\s*\\(%S\\)\\p{Z}*(?:~\\[.*\\])?)"
          + "|(?:(?:(?:%c|.*)?[:\"]\\s+)?%c(?::.*)?)";

  private final Pattern compiledPattern;

  private static final int NO_MATCH = -1;

  private final SourceFileLineNumberGroup sourceFileLineNumberGroup =
      new SourceFileLineNumberGroup();
  private final List<RegularExpressionGroupHandler> handlers;
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

  public StackTraceRegularExpressionParser() {
    this(DEFAULT_REGULAR_EXPRESSION);
  }

  public StackTraceRegularExpressionParser(String regularExpression) {
    handlers = new ArrayList<>();
    StringBuilder refinedRegularExpressionBuilder = new StringBuilder();
    registerGroups(
        regularExpression, refinedRegularExpressionBuilder, handlers, FIRST_CAPTURE_GROUP_INDEX);
    compiledPattern = Pattern.compile(refinedRegularExpressionBuilder.toString());
  }

  @Override
  public StackTraceElementStringProxy parse(String stackTraceLine) {
    StackTraceElementStringProxyBuilder proxyBuilder =
        StackTraceElementStringProxy.builder(stackTraceLine);
    Matcher matcher = compiledPattern.matcher(stackTraceLine);
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
    return proxyBuilder.build();
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
  //   Should we relax this to be something like: [^\\s\\[;/<]+
  private static final String javaIdentifierSegment =
      "[-\\p{javaJavaIdentifierStart}][-\\p{javaJavaIdentifierPart}]*";

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
      String anyNonDigitNonColonCharNonWhiteSpace = "[^\\d:\\s]";
      String anyNonColonChar = "[^:]";
      String colonsWithNonDigitOrWhiteSpaceSuffix = ":+" + anyNonDigitNonColonCharNonWhiteSpace;
      return "(?:" + colonsWithNonDigitOrWhiteSpaceSuffix + "|" + anyNonColonChar + "*)*";
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
        boolean insertSeparatorForRetraced = false;
        // We need to include ':' in the group since we may want to rewrite '(SourceFile:0)` into
        // (SourceFile) and not (SourceFile:)
        if (startOfGroup > 0 && builder.getLine().charAt(startOfGroup - 1) == ':') {
          startOfGroup = startOfGroup - 1;
          insertSeparatorForRetraced = true;
        }
        int end = matcher.end(captureGroup);
        builder.registerLineNumber(startOfGroup, end, insertSeparatorForRetraced);
        return true;
      };
    }
  }

  private static class SourceFileLineNumberGroup extends RegularExpressionGroup {

    @Override
    String subExpression() {
      return ".*";
    }

    @Override
    RegularExpressionGroupHandler createHandler(String captureGroup) {
      return (builder, matcher) -> {
        int startOfGroup = matcher.start(captureGroup);
        if (startOfGroup == NO_MATCH) {
          return false;
        }
        int endOfSourceFileInGroup = findEndOfSourceFile(matcher.group(captureGroup));
        int sourceFileEnd = startOfGroup + endOfSourceFileInGroup;
        builder.registerSourceFile(startOfGroup, sourceFileEnd);
        int endOfMatch = matcher.end(captureGroup);
        // We need to include ':' in the group since we may want to rewrite '(SourceFile:0)` into
        // (SourceFile) and not (SourceFile:). We fix this by setting the start of the linenumber
        // group to the end of the SourceFile group and then force inserting ':'.
        builder.registerLineNumber(Integer.min(sourceFileEnd, endOfMatch), endOfMatch, true);
        return true;
      };
    }

    private int findEndOfSourceFile(String group) {
      int index = group.length();
      while (index > 0) {
        char currentChar = group.charAt(index - 1);
        if (currentChar == ':' && index < group.length()) {
          // Subtract the ':' from the length.
          return index - 1;
        }
        if (!Character.isDigit(currentChar)) {
          return group.length();
        }
        index--;
      }
      return group.length();
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
