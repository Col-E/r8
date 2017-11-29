// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;

/**
 * A location with a position in a text file.
 */
public class TextRangeLocation extends Location {

  /**
   * A position in a text file determined by line and column.
   * Line and column numbers start at 1.
   */
  public static class TextPosition {
    private final int line;
    private final int column;

    public TextPosition(int line, int column) {
      this.line = line;
      this.column = column;
    }

    /**
     * Return the line of this position.
     */
    public int getLine() {
      return line;
    }

    /**
     * Return the column of this position.
     * @return May return {@link #UNKNOWN_COLUMN} if column information is not available.
     */
    public int getColumn() {
      return column;
    }

    @Override
    public String toString() {
      return "Line: " + line + ", column: " + column;
    }

    @Override
    public final int hashCode() {
      return line ^ (column << 16);
    }

    @Override
    public final boolean equals(Object o) {
      if (o == this) {
        return true;
      }
      if (o instanceof TextPosition) {
        TextPosition other = (TextPosition) o;
        return line == other.line && column == other.column;
      }
      return false;
    }
  }

  /**
   * Line or column is unknown.
   */
  public static final int UNKNOWN_COLUMN = -1;

  private final TextPosition start;
  private final TextPosition end;

  public static Location get(Origin origin, int startLine, int startColumn) {
    return get(origin, startLine, startColumn, startLine, startColumn);
  }

  public static Location get(Origin origin, int startLine, int startColumn, int endLine,
      int endColumn) {
    if (origin == Origin.unknown()) {
      return Location.UNKNOWN;
    } else {
      assert startLine > 0
          && endLine >= startLine
          && ((startColumn == UNKNOWN_COLUMN && endColumn == UNKNOWN_COLUMN)
            || (startColumn > 0 && endColumn > 0));
      TextPosition start = new TextPosition(startLine, startColumn);
      TextPosition end;
      if (startLine == endLine && startColumn == endColumn) {
        end = start;
      } else {
        end = new TextPosition(endLine, endColumn);
      }
      return new TextRangeLocation(origin, start, end);
    }
  }

  private TextRangeLocation(Origin origin, TextPosition start, TextPosition end) {
    super(origin);
    this.start = start;
    this.end = end;
  }

  /**
   * Return the start position of this text range.
   */
  public TextPosition getStart() {
    return start;
  }

  /**
   * Return the end position of this text range.
   */
  public TextPosition getEnd() {
    return end;
  }

  @Override
  public String getDescription() {
    return super.getDescription() + " line " + getStart().getLine();
  }
}
