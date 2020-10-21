// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.PlainStackTraceVisitor.firstNonWhiteSpaceCharacterFromIndex;
import static com.android.tools.r8.retrace.internal.StackTraceElementStringProxy.StringIndex.noIndex;

import com.android.tools.r8.retrace.StackTraceElementProxy;
import com.android.tools.r8.retrace.internal.StackTraceElementProxyRetracerImpl.RetraceStackTraceProxyImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public final class StackTraceElementStringProxy extends StackTraceElementProxy<String> {

  private final String line;
  private final List<StringIndex> orderedIndices;
  private final StringIndex className;
  private final StringIndex methodName;
  private final StringIndex sourceFile;
  private final StringIndex lineNumber;

  private StackTraceElementStringProxy(
      String line,
      List<StringIndex> orderedIndices,
      StringIndex className,
      StringIndex methodName,
      StringIndex sourceFile,
      StringIndex lineNumber) {
    this.line = line;
    this.orderedIndices = orderedIndices;
    this.className = className;
    this.methodName = methodName;
    this.sourceFile = sourceFile;
    this.lineNumber = lineNumber;
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
  public String className() {
    return hasClassName() ? getEntryInLine(className) : null;
  }

  @Override
  public String methodName() {
    return hasMethodName() ? getEntryInLine(methodName) : null;
  }

  @Override
  public String fileName() {
    return hasFileName() ? getEntryInLine(sourceFile) : null;
  }

  @Override
  public int lineNumber() {
    if (!hasLineNumber()) {
      return -1;
    }
    try {
      return Integer.parseInt(getEntryInLine(lineNumber));
    } catch (NumberFormatException nfe) {
      return -1;
    }
  }

  public String toRetracedItem(
      RetraceStackTraceProxyImpl<StackTraceElementStringProxy> retracedProxy,
      boolean printAmbiguous) {
    StringBuilder sb = new StringBuilder();
    int lastSeenIndex = 0;
    if (retracedProxy.isAmbiguous() && printAmbiguous) {
      lastSeenIndex = firstNonWhiteSpaceCharacterFromIndex(line, 0);
      sb.append(line, 0, lastSeenIndex);
      sb.append("<OR> ");
    }
    for (StringIndex index : orderedIndices) {
      sb.append(line, lastSeenIndex, index.startIndex);
      sb.append(index.retracedString.apply(retracedProxy, this));
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

  public static class StackTraceElementStringProxyBuilder {

    private final String line;
    private final List<StringIndex> orderedIndices = new ArrayList<>();
    private StringIndex className = noIndex();
    private StringIndex methodName = noIndex();
    private StringIndex sourceFile = noIndex();
    private StringIndex lineNumber = noIndex();
    private int lastSeenStartIndex = -1;

    private StackTraceElementStringProxyBuilder(String line) {
      this.line = line;
    }

    public StackTraceElementStringProxyBuilder registerClassName(int startIndex, int endIndex) {
      ensureLineIndexIncreases(startIndex);
      className =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original) -> {
                assert retraced.hasRetracedClass();
                return retraced.getRetracedClass().getTypeName();
              });
      orderedIndices.add(className);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerMethodName(int startIndex, int endIndex) {
      methodName =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original) ->
                  retraced.hasRetracedMethod()
                      ? retraced.getRetracedMethod().getMethodName()
                      : original.methodName());
      orderedIndices.add(methodName);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerSourceFile(int startIndex, int endIndex) {
      sourceFile =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original) ->
                  retraced.hasSourceFile() ? retraced.getSourceFile() : original.fileName());
      orderedIndices.add(sourceFile);
      return this;
    }

    public StackTraceElementStringProxyBuilder registerLineNumber(int startIndex, int endIndex) {
      lineNumber =
          new StringIndex(
              startIndex,
              endIndex,
              (retraced, original) ->
                  retraced.hasLineNumber()
                      ? retraced.getLineNumber() + ""
                      : original.lineNumberAsString());
      orderedIndices.add(lineNumber);
      return this;
    }

    public StackTraceElementStringProxy build() {
      return new StackTraceElementStringProxy(
          line, orderedIndices, className, methodName, sourceFile, lineNumber);
    }

    private void ensureLineIndexIncreases(int newStartIndex) {
      if (lastSeenStartIndex >= newStartIndex) {
        throw new RuntimeException("Parsing has to be incremental in the order of characters.");
      }
      lastSeenStartIndex = newStartIndex;
    }
  }

  static final class StringIndex {

    private static final StringIndex NO_INDEX = new StringIndex(-1, -1, null);

    static StringIndex noIndex() {
      return NO_INDEX;
    }

    private final int startIndex;
    private final int endIndex;
    private final BiFunction<
            RetraceStackTraceProxyImpl<StackTraceElementStringProxy>,
            StackTraceElementStringProxy,
            String>
        retracedString;

    private StringIndex(
        int startIndex,
        int endIndex,
        BiFunction<
                RetraceStackTraceProxyImpl<StackTraceElementStringProxy>,
                StackTraceElementStringProxy,
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
}
