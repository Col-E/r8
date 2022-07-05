// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

public class JavaStringBuilder {

  private final StringBuilder builder = new StringBuilder();
  private int indentation = 0;

  public JavaStringBuilder append(char c) {
    builder.append(c);
    return this;
  }

  public JavaStringBuilder append(int i) {
    builder.append(i);
    return this;
  }

  public JavaStringBuilder append(String string) {
    builder.append(string);
    return this;
  }

  public JavaStringBuilder appendLine() {
    return append('\n');
  }

  public JavaStringBuilder appendLine(char c) {
    return append(c).appendLine();
  }

  public JavaStringBuilder appendLine(String string) {
    return append(string).appendLine();
  }

  public JavaStringBuilder appendOpeningBrace() {
    return appendLine('{').indent(2);
  }

  public JavaStringBuilder appendClosingBrace() {
    return indent(-2).startLine().appendLine('}');
  }

  public JavaStringBuilder appendOpeningArrayBrace() {
    return appendLine('{').indent(4);
  }

  public JavaStringBuilder appendClosingArrayBrace() {
    return indent(-4).startLine().appendLine("};");
  }

  public JavaStringBuilder appendOpeningMultiLineParenthesis() {
    return appendLine('(').indent(4);
  }

  public JavaStringBuilder appendClosingMultiLineParenthesis() {
    return append(')').indent(-4);
  }

  public JavaStringBuilder indent(int change) {
    indentation += change;
    return this;
  }

  public JavaStringBuilder startLine() {
    for (int i = 0; i < indentation; i++) {
      append(' ');
    }
    return this;
  }

  @Override
  public String toString() {
    return builder.toString();
  }
}
