// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.google.common.base.Predicates.not;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class RetraceCore {

  public static class StackTraceNode {

    private final List<StackTraceLine> lines;

    StackTraceNode(List<StackTraceLine> lines) {
      this.lines = lines;
      assert !lines.isEmpty();
      assert lines.size() == 1 || lines.stream().allMatch(StackTraceLine::isAtLine);
    }

    public void append(List<String> strings) {
      assert !lines.isEmpty();
      if (lines.size() == 1) {
        strings.add(lines.get(0).toString());
        return;
      }
      // We must have an ambiguous match here, thus all lines are at-lines.
      lines.sort(new AtStackTraceLineComparator());
      String previousClazz = "";
      for (StackTraceLine line : lines) {
        assert line.isAtLine();
        AtLine atLine = line.asAtLine();
        strings.add(atLine.toString(previousClazz.isEmpty() ? atLine.at : "or ", previousClazz));
        previousClazz = atLine.clazz;
      }
    }
  }

  static class AtStackTraceLineComparator implements Comparator<StackTraceLine> {

    @Override
    public int compare(StackTraceLine o1, StackTraceLine o2) {
      AtLine a1 = (AtLine) o1;
      AtLine a2 = (AtLine) o2;
      int compare = a1.clazz.compareTo(a2.clazz);
      if (compare != 0) {
        return compare;
      }
      compare = a1.method.compareTo(a2.method);
      if (compare != 0) {
        return compare;
      }
      compare = a1.fileName.compareTo(a2.fileName);
      if (compare != 0) {
        return compare;
      }
      return Integer.compare(a1.linePosition, a2.linePosition);
    }
  }

  static class RetraceResult {

    private final List<StackTraceNode> nodes;

    RetraceResult(List<StackTraceNode> nodes) {
      this.nodes = nodes;
    }

    List<String> toListOfStrings() {
      List<String> strings = new ArrayList<>(nodes.size());
      for (StackTraceNode node : nodes) {
        node.append(strings);
      }
      return strings;
    }
  }

  private final ClassNameMapper classNameMapper;
  private final List<String> stackTrace;
  private final DiagnosticsHandler diagnosticsHandler;

  RetraceCore(
      ClassNameMapper classNameMapper,
      List<String> stackTrace,
      DiagnosticsHandler diagnosticsHandler) {
    this.classNameMapper = classNameMapper;
    this.stackTrace = stackTrace;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  public RetraceResult retrace() {
    ArrayList<StackTraceNode> result = new ArrayList<>();
    retraceLine(stackTrace, 0, result);
    return new RetraceResult(result);
  }

  private void retraceLine(List<String> stackTrace, int index, List<StackTraceNode> result) {
    if (stackTrace.size() <= index) {
      return;
    }
    StackTraceLine stackTraceLine = parseLine(index + 1, stackTrace.get(index));
    List<StackTraceLine> retraced = stackTraceLine.retrace(classNameMapper);
    StackTraceNode node = new StackTraceNode(retraced);
    result.add(node);
    retraceLine(stackTrace, index + 1, result);
  }

  abstract static class StackTraceLine {
    abstract List<StackTraceLine> retrace(ClassNameMapper mapper);

    static int firstNonWhiteSpaceCharacterFromIndex(String line, int index) {
      return firstFromIndex(line, index, not(Character::isWhitespace));
    }

    static int firstCharFromIndex(String line, int index, char ch) {
      return firstFromIndex(line, index, c -> c == ch);
    }

    static int firstFromIndex(String line, int index, Predicate<Character> predicate) {
      for (int i = index; i < line.length(); i++) {
        if (predicate.test(line.charAt(i))) {
          return i;
        }
      }
      return line.length();
    }

    AtLine asAtLine() {
      return null;
    }

    boolean isAtLine() {
      return false;
    }
  }

  /**
   * Captures a stack trace line of the following formats:
   *
   * <ul>
   *   <li>com.android.r8.R8Exception
   *   <li>com.android.r8.R8Exception: Problem when compiling program
   *   <li>Caused by: com.android.r8.R8InnerException: You have to write the program first
   *   <li>com.android.r8.R8InnerException: You have to write the program first
   * </ul>
   *
   * <p>This will also contains false positives, such as
   *
   * <pre>
   *   W( 8207) VFY: unable to resolve static method 11: Lprivateinterfacemethods/I$-CC;....
   * </pre>
   *
   * <p>The only invalid chars for type-identifiers for a java type-name is ';', '[' and '/', so we
   * cannot really disregard the above line.
   *
   * <p>Caused by and Suppressed seems to not change based on locale, so we use these as markers.
   */
  static class ExceptionLine extends StackTraceLine {

    private static final String CAUSED_BY = "Caused by: ";
    private static final String SUPPRESSED = "Suppressed: ";

    private final String initialWhiteSpace;
    private final String description;
    private final String exceptionClass;
    private final String message;

    ExceptionLine(
        String initialWhiteSpace, String description, String exceptionClass, String message) {
      this.initialWhiteSpace = initialWhiteSpace;
      this.description = description;
      this.exceptionClass = exceptionClass;
      this.message = message;
    }

    static ExceptionLine tryParse(String line) {
      if (line.isEmpty()) {
        return null;
      }
      int firstNonWhiteSpaceChar = firstNonWhiteSpaceCharacterFromIndex(line, 0);
      String description = "";
      if (line.startsWith(CAUSED_BY, firstNonWhiteSpaceChar)) {
        description = CAUSED_BY;
      } else if (line.startsWith(SUPPRESSED, firstNonWhiteSpaceChar)) {
        description = SUPPRESSED;
      }
      int exceptionStartIndex = firstNonWhiteSpaceChar + description.length();
      int messageStartIndex = firstCharFromIndex(line, exceptionStartIndex, ':');
      String className = line.substring(exceptionStartIndex, messageStartIndex);
      if (!DescriptorUtils.isValidJavaType(className)) {
        return null;
      }
      return new ExceptionLine(
          line.substring(0, firstNonWhiteSpaceChar),
          description,
          className,
          line.substring(messageStartIndex));
    }

    @Override
    List<StackTraceLine> retrace(ClassNameMapper mapper) {
      ClassNamingForNameMapper classNaming = mapper.getClassNaming(exceptionClass);
      String retracedExceptionClass = exceptionClass;
      if (classNaming != null) {
        retracedExceptionClass = classNaming.originalName;
      }
      return ImmutableList.of(
          new ExceptionLine(initialWhiteSpace, description, retracedExceptionClass, message));
    }

    @Override
    public String toString() {
      return initialWhiteSpace + description + exceptionClass + message;
    }
  }

  /**
   * Captures a stack trace line on the following form
   *
   * <ul>
   *   <li>at dalvik.system.NativeStart.main(NativeStart.java:99)
   *   <li>at dalvik.system.NativeStart.main(:99)
   *   <li>dalvik.system.NativeStart.main(Foo.java:)
   *   <li>at dalvik.system.NativeStart.main(Native Method)
   * </ul>
   *
   * <p>Empirical evidence suggests that the "at" string is never localized.
   */
  static class AtLine extends StackTraceLine {

    private static final Set<String> UNKNOWN_SOURCEFILE_NAMES =
        Sets.newHashSet("", "SourceFile", "Unknown", "Unknown Source");

    private static final int NO_POSITION = -2;
    private static final int INVALID_POSITION = -1;

    private final String startingWhitespace;
    private final String at;
    private final String clazz;
    private final String method;
    private final String fileName;
    private final int linePosition;

    private AtLine(
        String startingWhitespace,
        String at,
        String clazz,
        String method,
        String fileName,
        int linePosition) {
      this.startingWhitespace = startingWhitespace;
      this.at = at;
      this.clazz = clazz;
      this.method = method;
      this.fileName = fileName;
      this.linePosition = linePosition;
    }

    static AtLine tryParse(String line) {
      // Check that the line is indented with some amount of white space.
      if (line.length() == 0 || !Character.isWhitespace(line.charAt(0))) {
        return null;
      }
      // Find the first non-white space character and check that we have the sequence 'a', 't', ' '.
      int firstNonWhiteSpace = firstNonWhiteSpaceCharacterFromIndex(line, 0);
      if (firstNonWhiteSpace + 2 >= line.length()
          || line.charAt(firstNonWhiteSpace) != 'a'
          || line.charAt(firstNonWhiteSpace + 1) != 't'
          || line.charAt(firstNonWhiteSpace + 2) != ' ') {
        return null;
      }
      int classStartIndex = firstNonWhiteSpaceCharacterFromIndex(line, firstNonWhiteSpace + 2);
      if (classStartIndex >= line.length() || classStartIndex != firstNonWhiteSpace + 3) {
        return null;
      }
      int parensStart = firstCharFromIndex(line, classStartIndex, '(');
      if (parensStart >= line.length()) {
        return null;
      }
      int parensEnd = firstCharFromIndex(line, parensStart, ')');
      if (parensEnd >= line.length()) {
        return null;
      }
      if (firstNonWhiteSpaceCharacterFromIndex(line, parensEnd) == line.length()) {
        return null;
      }
      int methodSeparator = line.lastIndexOf('.', parensStart);
      if (methodSeparator <= classStartIndex) {
        return null;
      }
      // Check if we have a filename and position.
      String fileName = "";
      int position = NO_POSITION;
      int separatorIndex = firstCharFromIndex(line, parensStart, ':');
      if (separatorIndex < parensEnd) {
        fileName = line.substring(parensStart + 1, separatorIndex);
        try {
          String positionAsString = line.substring(separatorIndex + 1, parensEnd);
          position = Integer.parseInt(positionAsString);
        } catch (NumberFormatException e) {
          position = INVALID_POSITION;
        }
      } else {
        fileName = line.substring(parensStart + 1, parensEnd);
      }
      return new AtLine(
          line.substring(0, firstNonWhiteSpace),
          line.substring(firstNonWhiteSpace, classStartIndex),
          line.substring(classStartIndex, methodSeparator),
          line.substring(methodSeparator + 1, parensStart),
          fileName,
          position);
    }

    @Override
    List<StackTraceLine> retrace(ClassNameMapper mapper) {
      ClassNamingForNameMapper classNaming = mapper.getClassNaming(clazz);
      List<StackTraceLine> lines = new ArrayList<>();
      if (classNaming == null) {
        lines.add(
            new AtLine(
                startingWhitespace, at, clazz, method, retracedFileName(null), linePosition));
        return lines;
      }
      String retraceClazz = classNaming.originalName;
      MappedRangesOfName mappedRangesOfName = classNaming.mappedRangesByRenamedName.get(method);
      if (mappedRangesOfName == null) {
        lines.add(
            new AtLine(
                startingWhitespace,
                at,
                retraceClazz,
                method,
                retracedFileName(retraceClazz),
                linePosition));
        return lines;
      }
      List<MappedRange> mappedRanges =
          linePosition >= 0
              ? mappedRangesOfName.allRangesForLine(linePosition)
              : mappedRangesOfName.getMappedRanges();
      if (mappedRanges == null || mappedRanges.isEmpty()) {
        lines.add(
            new AtLine(
                startingWhitespace,
                at,
                retraceClazz,
                method,
                retracedFileName(retraceClazz),
                linePosition));
        return lines;
      }
      for (MappedRange mappedRange : mappedRanges) {
        String mappedClazz = retraceClazz;
        String mappedMethod = mappedRange.signature.name;
        if (mappedRange.signature.isQualified()) {
          mappedClazz = mappedRange.signature.toUnqualifiedHolder();
          mappedMethod = mappedRange.signature.toUnqualifiedName();
        }
        int retracedLinePosition = linePosition;
        if (linePosition > 0) {
          retracedLinePosition = mappedRange.getOriginalLineNumber(linePosition);
        }
        lines.add(
            new AtLine(
                startingWhitespace,
                at,
                mappedClazz,
                mappedMethod,
                retracedFileName(mappedClazz),
                retracedLinePosition));
      }
      assert !lines.isEmpty();
      return lines;
    }

    private String retracedFileName(String retracedClazz) {
      if (retracedClazz == null) {
        // We have no new information, only rewrite filename if it is empty or SourceFile.
        // PG-retrace will always rewrite the filename, but that seems a bit to harsh to do.
        if (UNKNOWN_SOURCEFILE_NAMES.contains(fileName)) {
          return getClassSimpleName(clazz) + ".java";
        }
        return fileName;
      }
      String newFileName = getClassSimpleName(retracedClazz);
      String extension = Files.getFileExtension(fileName);
      if (extension.isEmpty()) {
        extension = "java";
      }
      return newFileName + "." + extension;
    }

    private String getClassSimpleName(String clazz) {
      int lastIndexOfPeriod = clazz.lastIndexOf('.');
      if (lastIndexOfPeriod > -1) {
        // Check if we can find a subclass separator.
        int endIndex = firstCharFromIndex(clazz, lastIndexOfPeriod, '$');
        return clazz.substring(lastIndexOfPeriod + 1, endIndex);
      } else {
        return clazz;
      }
    }

    @Override
    public String toString() {
      return toString(at, "");
    }

    protected String toString(String at, String previousClass) {
      StringBuilder sb = new StringBuilder(startingWhitespace);
      sb.append(at);
      String commonPrefix = Strings.commonPrefix(clazz, previousClass);
      if (commonPrefix.length() == clazz.length()) {
        sb.append(Strings.repeat(" ", clazz.length() + 1));
      } else {
        sb.append(Strings.padStart(clazz.substring(commonPrefix.length()), clazz.length(), ' '));
        sb.append(".");
      }
      sb.append(method);
      sb.append("(");
      sb.append(fileName);
      if (linePosition != NO_POSITION) {
        sb.append(":");
      }
      if (linePosition > INVALID_POSITION) {
        sb.append(linePosition);
      }
      sb.append(")");
      return sb.toString();
    }

    @Override
    boolean isAtLine() {
      return true;
    }

    @Override
    AtLine asAtLine() {
      return this;
    }
  }

  static class MoreLine extends StackTraceLine {
    private final String line;

    MoreLine(String line) {
      this.line = line;
    }

    static StackTraceLine tryParse(String line) {
      int dotsSeen = 0;
      boolean isWhiteSpaceAllowed = true;
      for (int i = 0; i < line.length(); i++) {
        char ch = line.charAt(i);
        if (Character.isWhitespace(ch) && isWhiteSpaceAllowed) {
          continue;
        }
        isWhiteSpaceAllowed = false;
        if (ch != '.') {
          return null;
        }
        if (++dotsSeen == 3) {
          return new MoreLine(line);
        }
      }
      return null;
    }

    @Override
    List<StackTraceLine> retrace(ClassNameMapper mapper) {
      return ImmutableList.of(new MoreLine(line));
    }

    @Override
    public String toString() {
      return line;
    }
  }

  static class UnknownLine extends StackTraceLine {
    private final String line;

    UnknownLine(String line) {
      this.line = line;
    }

    @Override
    List<StackTraceLine> retrace(ClassNameMapper mapper) {
      return ImmutableList.of(new UnknownLine(line));
    }

    @Override
    public String toString() {
      return line;
    }
  }

  private StackTraceLine parseLine(int lineNumber, String line) {
    if (line == null) {
      diagnosticsHandler.error(RetraceInvalidStackTraceLineDiagnostics.createNull(lineNumber));
      throw new Retrace.RetraceAbortException();
    }
    // Most lines are 'at lines' so attempt to parse it first.
    StackTraceLine parsedLine = AtLine.tryParse(line);
    if (parsedLine != null) {
      return parsedLine;
    }
    parsedLine = ExceptionLine.tryParse(line);
    if (parsedLine != null) {
      return parsedLine;
    }
    parsedLine = MoreLine.tryParse(line);
    if (parsedLine == null) {
      diagnosticsHandler.warning(
          RetraceInvalidStackTraceLineDiagnostics.createParse(lineNumber, line));
    }
    parsedLine = new UnknownLine(line);
    return parsedLine;
  }
}
