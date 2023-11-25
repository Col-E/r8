// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Public utility for printing R8/D8 command line flags.
 *
 * <p>This utility can be used to support wrapping the compilers command-line interface.
 */
@KeepForApi
public class ParseFlagPrinter {

  private final List<ParseFlagInfo> flags = new ArrayList<>();

  private String prefix = "  ";
  private int helpColumn = 25;
  private String helpSeparator = " # ";

  // Formatting state.
  private StringBuilder builder = null;
  private int currentColumn = -1;

  // We use -1 to denote the unstarted line, otherwise we can't distinguish it when prefix is empty.
  private boolean isLineStarted() {
    return currentColumn >= 0;
  }

  private void append(String string) {
    assert isLineStarted();
    builder.append(string);
    currentColumn += string.length();
  }

  private void space(int space) {
    assert isLineStarted();
    for (int i = 0; i < space; i++) {
      builder.append(' ');
    }
    currentColumn += space;
  }

  private void endLine() {
    assert isLineStarted();
    builder.append(StringUtils.LINE_SEPARATOR);
    currentColumn = -1;
  }

  private void startLine() {
    assert !isLineStarted();
    currentColumn = 0;
    append(prefix);
  }

  private void addFlagLine(String flagLine) {
    if (isLineStarted()) {
      endLine();
    }
    startLine();
    append(flagLine);
  }

  private void addHelpLine(String helpLine) {
    // If the current line is already past the point for printing help implicitly end the line.
    if (currentColumn > helpColumn) {
      endLine();
    }
    if (!isLineStarted()) {
      startLine();
    }
    int distanceToHelpColum = helpColumn - currentColumn;
    space(distanceToHelpColum);
    append(helpSeparator);
    append(helpLine);
    endLine();
  }

  private void formatParseFlags() {
    for (ParseFlagInfo flag : flags) {
      addFlagLine(flag.getFlagFormat());
      flag.getFlagFormatAlternatives().forEach(this::addFlagLine);
      flag.getFlagHelp().forEach(this::addHelpLine);
    }
  }

  public ParseFlagPrinter addFlags(List<ParseFlagInfo> flags) {
    this.flags.addAll(flags);
    return this;
  }

  /** Set a prefix which will be prepended to each line (flags and help lines). */
  public ParseFlagPrinter setPrefix(String prefix) {
    this.prefix = prefix;
    return this;
  }

  /** Convenience method to set the prefix to be 'indent' number of spaces. */
  public ParseFlagPrinter setIndent(int indent) {
    return setPrefix(" ".repeat(indent));
  }

  /**
   * Set the column at which the help information should start.
   *
   * <p>If a flag header extends past the end of the help column, then the help will start on a new
   * line at the point of the specified help column.
   */
  public ParseFlagPrinter setHelpColumn(int helpColumn) {
    this.helpColumn = helpColumn;
    return this;
  }

  /**
   * Set the separator to use to split the help information from the flag info.
   *
   * <p>This is prefixed to every help line and thus extends the size of each help line. The help
   * separator will always start at exactly the help column ({@see setHelpColumn(int)}.
   */
  public ParseFlagPrinter setHelpSeparator(String helpSeparator) {
    this.helpSeparator = helpSeparator;
    return this;
  }

  public void appendLinesToBuilder(StringBuilder builder) {
    assert this.builder == null;
    assert this.currentColumn == -1;
    this.builder = builder;
    formatParseFlags();
    this.builder = null;
    this.currentColumn = -1;
  }

  public static void main(String[] args) {
    D8.main(new String[] {"--help"});
  }
}
