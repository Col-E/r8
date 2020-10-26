// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.PlainStackTraceVisitor.firstNonWhiteSpaceCharacterFromIndex;
import static com.android.tools.r8.retrace.internal.RetraceUtils.methodDescriptionFromRetraceMethod;
import static com.android.tools.r8.retrace.internal.StackTraceElementStringProxy.StringIndex.noIndex;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetracedClass;
import com.android.tools.r8.retrace.RetracedField;
import com.android.tools.r8.retrace.RetracedType;
import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.internal.StackTraceElementProxyRetracerImpl.RetraceStackTraceProxyImpl;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.android.tools.r8.utils.TriFunction;
import java.util.ArrayList;
import java.util.List;

public final class StackTraceElementStringProxy extends StackTraceElementProxy<String> {

  private final String line;
  private final List<StringIndex> orderedIndices;
  private final ClassStringIndex className;
  private final StringIndex methodName;
  private final StringIndex sourceFile;
  private final StringIndex lineNumber;
  private final StringIndex fieldName;
  private final StringIndex fieldOrReturnType;
  private final StringIndex methodArguments;

  private StackTraceElementStringProxy(
      String line,
      List<StringIndex> orderedIndices,
      ClassStringIndex className,
      StringIndex methodName,
      StringIndex sourceFile,
      StringIndex lineNumber,
      StringIndex fieldName,
      StringIndex fieldOrReturnType,
      StringIndex methodArguments) {
    this.line = line;
    this.orderedIndices = orderedIndices;
    this.className = className;
    this.methodName = methodName;
    this.sourceFile = sourceFile;
    this.lineNumber = lineNumber;
    this.fieldName = fieldName;
    this.fieldOrReturnType = fieldOrReturnType;
    this.methodArguments = methodArguments;
  }

  static StackTraceElementStringProxyBuilder builder(String line) {
    return new StackTraceElementStringProxyBuilder(line);
  }

  @Override
  public boolean hasClassName() {
    return className.hasIndex();
  }

  @Override
  public boolean hasMethodName() {
    return methodName.hasIndex();
  }

  @Override
  public boolean hasFileName() {
    return sourceFile.hasIndex();
  }

  @Override
  public boolean hasLineNumber() {
    return lineNumber.hasIndex();
  }

  @Override
  public boolean hasFieldName() {
    return fieldName.hasIndex();
  }

  @Override
  public boolean hasFieldOrReturnType() {
    return fieldOrReturnType.hasIndex();
  }

  @Override
  public boolean hasMethodArguments() {
    return methodArguments.hasIndex();
  }

  @Override
  public ClassReference getClassReference() {
    return hasClassName() ? className.getReference(line) : null;
  }

  @Override
  public String getMethodName() {
    return hasMethodName() ? getEntryInLine(methodName) : null;
  }

  @Override
  public String getFileName() {
    return hasFileName() ? getEntryInLine(sourceFile) : null;
  }

  @Override
  public int getLineNumber() {
    if (!hasLineNumber()) {
      return -1;
    }
    try {
      return Integer.parseInt(getEntryInLine(lineNumber));
    } catch (NumberFormatException nfe) {
      return -1;
    }
  }

  @Override
  public String getFieldName() {
    return hasFieldName() ? getEntryInLine(fieldName) : null;
  }

  @Override
  public String getFieldOrReturnType() {
    return hasFieldOrReturnType() ? getEntryInLine(fieldOrReturnType) : null;
  }

  @Override
  public String getMethodArguments() {
    return hasMethodArguments() ? getEntryInLine(methodArguments) : null;
  }

  public String toRetracedItem(
      RetraceStackTraceProxyImpl<StackTraceElementStringProxy> retracedProxy,
      boolean printAmbiguous,
      boolean verbose) {
    StringBuilder sb = new StringBuilder();
    int lastSeenIndex = 0;
    if (retracedProxy.isAmbiguous() && printAmbiguous) {
      lastSeenIndex = firstNonWhiteSpaceCharacterFromIndex(line, 0);
      sb.append(line, 0, lastSeenIndex);
      sb.append("<OR> ");
    }
    for (StringIndex index : orderedIndices) {
      sb.append(line, lastSeenIndex, index.startIndex);
      sb.append(index.retracedString.apply(retracedProxy, this, verbose));
      lastSeenIndex = index.endIndex;
    }
    sb.append(line, lastSeenIndex, line.length());
    return sb.toString();
  }

  public String lineNumberAsString() {
    return getEntryInLine(lineNumber);
  }

  private String getEntryInLine(StringIndex index) {
    assert index != noIndex();
    return line.substring(index.startIndex, index.endIndex);
  }

  public enum ClassNameType {
    BINARY,
    TYPENAME
  }

  public static class StackTraceElementStringProxyBuilder {

    private final String line;
    private final List<StringIndex> orderedIndices = new ArrayList<>();
    private ClassStringIndex className = noIndex();
    private StringIndex methodName = noIndex();
    private StringIndex sourceFile = noIndex();
    private StringIndex lineNumber = noIndex();
    private StringIndex fieldName = noIndex();
    private StringIndex fieldOrReturnType = noIndex();
    private StringIndex methodArguments = noIndex();
    private int lastSeenStartIndex = -1;

    private StackTraceElementStringProxyBuilder(String line) {
      this.line = line;
    }

    public StackTraceElementStringProxyBuilder registerClassName(
        int startIndex, int endIndex, ClassNameType classNameType) {
      ensureLineIndexIncreases(startIndex);
      className =
          new ClassStringIndex(
              startIndex,
              endIndex,
              (retraced, original, verbose) -> {
                assert retraced.hasRetracedClass();
                RetracedClass retracedClass = retraced.getRetracedClass();
                return classNameType == ClassNameType.BINARY
                    ? retracedClass.getBinaryName()
                    : retracedClass.getTypeName();
              },
              classNameType);
      orderedIndices.add(className);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerMethodName(int startIndex, int endIndex) {
      methodName =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original, verbose) -> {
                if (!retraced.hasRetracedMethod()) {
                  return original.getMethodName();
                }
                return methodDescriptionFromRetraceMethod(
                    retraced.getRetracedMethod(), false, verbose);
              });
      orderedIndices.add(methodName);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerSourceFile(int startIndex, int endIndex) {
      sourceFile =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original, verbose) ->
                  retraced.hasSourceFile() ? retraced.getSourceFile() : original.getFileName());
      orderedIndices.add(sourceFile);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerLineNumber(int startIndex, int endIndex) {
      lineNumber =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original, verbose) ->
                  retraced.hasLineNumber()
                      ? retraced.getLineNumber() + ""
                      : original.lineNumberAsString());
      orderedIndices.add(lineNumber);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerFieldName(int startIndex, int endIndex) {
      fieldName =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original, verbose) -> {
                if (!retraced.hasRetracedField()) {
                  return original.getFieldName();
                }
                RetracedField retracedField = retraced.getRetracedField();
                if (!verbose || retracedField.isUnknown()) {
                  return retracedField.getFieldName();
                }
                return retracedField.asKnown().getFieldType().getTypeName()
                    + " "
                    + retracedField.getFieldName();
              });
      orderedIndices.add(fieldName);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerFieldOrReturnType(
        int startIndex, int endIndex) {
      fieldOrReturnType =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original, verbose) -> {
                if (!retraced.hasFieldOrReturnType()) {
                  return original.getFieldOrReturnType();
                }
                return retraced.getRetracedFieldOrReturnType().isVoid()
                    ? "void"
                    : retraced.getRetracedFieldOrReturnType().getTypeName();
              });
      orderedIndices.add(fieldOrReturnType);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerMethodArguments(
        int startIndex, int endIndex) {
      methodArguments =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original, verbose) -> {
                if (!retraced.hasMethodArguments()) {
                  return original.getMethodArguments();
                }
                return StringUtils.join(
                    retraced.getMethodArguments(), ",", BraceType.NONE, RetracedType::getTypeName);
              });
      orderedIndices.add(methodArguments);
      return this;
    }

    public StackTraceElementStringProxy build() {
      return new StackTraceElementStringProxy(
          line,
          orderedIndices,
          className,
          methodName,
          sourceFile,
          lineNumber,
          fieldName,
          fieldOrReturnType,
          methodArguments);
    }

    private void ensureLineIndexIncreases(int newStartIndex) {
      if (lastSeenStartIndex >= newStartIndex) {
        throw new RuntimeException("Parsing has to be incremental in the order of characters.");
      }
      lastSeenStartIndex = newStartIndex;
    }
  }

  static class StringIndex {

    private static final ClassStringIndex NO_INDEX =
        new ClassStringIndex(-1, -1, null, ClassNameType.TYPENAME);

    static ClassStringIndex noIndex() {
      return NO_INDEX;
    }

    protected final int startIndex;
    protected final int endIndex;
    private final TriFunction<
            RetraceStackTraceProxyImpl<StackTraceElementStringProxy>,
            StackTraceElementStringProxy,
            Boolean,
            String>
        retracedString;

    private StringIndex(
        int startIndex,
        int endIndex,
        TriFunction<
                RetraceStackTraceProxyImpl<StackTraceElementStringProxy>,
                StackTraceElementStringProxy,
                Boolean,
                String>
            retracedString) {
      this.startIndex = startIndex;
      this.endIndex = endIndex;
      this.retracedString = retracedString;
    }

    boolean hasIndex() {
      return this != NO_INDEX;
    }
  }

  static final class ClassStringIndex extends StringIndex {

    private final ClassNameType classNameType;

    private ClassStringIndex(
        int startIndex,
        int endIndex,
        TriFunction<
                RetraceStackTraceProxyImpl<StackTraceElementStringProxy>,
                StackTraceElementStringProxy,
                Boolean,
                String>
            retracedString,
        ClassNameType classNameType) {
      super(startIndex, endIndex, retracedString);
      this.classNameType = classNameType;
    }

    ClassReference getReference(String line) {
      String className = line.substring(startIndex, endIndex);
      return classNameType == ClassNameType.BINARY
          ? Reference.classFromBinaryName(className)
          : Reference.classFromTypeName(className);
    }
  }
}
