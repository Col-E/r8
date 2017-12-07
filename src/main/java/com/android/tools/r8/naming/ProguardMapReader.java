// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.utils.IdentifierUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Parses a Proguard mapping file and produces mappings from obfuscated class names to the original
 * name and from obfuscated member signatures to the original members the obfuscated member
 * was formed of.
 * <p>
 * The expected format is as follows
 * <p>
 * original-type-name ARROW obfuscated-type-name COLON starts a class mapping
 * description and maps original to obfuscated.
 * <p>
 * followed by one or more of
 * <p>
 * signature ARROW name
 * <p>
 * which maps the member with the given signature to the new name. This mapping is not
 * bidirectional as member names are overloaded by signature. To make it bidirectional, we extend
 * the name with the signature of the original member.
 * <p>
 * Due to inlining, we might have the above prefixed with a range (two numbers separated by :).
 * <p>
 * range COLON signature ARROW name
 * <p>
 * This has the same meaning as the above but also encodes the line number range of the member. This
 * may be followed by multiple inline mappings of the form
 * <p>
 * range COLON signature COLON range ARROW name
 * <p>
 * to identify that signature was inlined from the second range to the new line numbers in the first
 * range. This is then followed by information on the call trace to where the member was inlined.
 * These entries have the form
 * <p>
 * range COLON signature COLON number ARROW name
 * <p>
 * and are currently only stored to be able to reproduce them later.
 */
public class ProguardMapReader implements AutoCloseable {

  private final BufferedReader reader;

  @Override
  public void close() throws IOException {
    if (reader != null) {
      reader.close();
    }
  }

  ProguardMapReader(BufferedReader reader) {
    this.reader = reader;
  }

  // Internal parser state
  private int lineNo = 0;
  private int lineOffset = 0;
  private String line;

  private char peek() {
    return peek(0);
  }

  private char peek(int distance) {
    return lineOffset + distance < line.length()
        ? line.charAt(lineOffset + distance)
        : '\n';
  }

  private boolean hasNext() {
    return lineOffset < line.length();
  }

  private char next() {
    try {
      return line.charAt(lineOffset++);
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new ParseException("Unexpected end of line");
    }
  }

  private boolean nextLine() throws IOException {
    if (line.length() != lineOffset) {
      throw new ParseException("Expected end of line");
    }
    return skipLine();
  }

  private static boolean isEmptyOrCommentLine(String line) {
    if (line == null) {
      return true;
    }
    for (int i = 0; i < line.length(); ++i) {
      char c = line.charAt(i);
      if (c == '#') {
        return true;
      } else if (!Character.isWhitespace(c)) {
        return false;
      }
    }
    return true;
  }

  private boolean skipLine() throws IOException {
    lineOffset = 0;
    do {
      lineNo++;
      line = reader.readLine();
    } while (hasLine() && isEmptyOrCommentLine(line));
    return hasLine();
  }

  private boolean hasLine() {
    return line != null;
  }

  // Helpers for common pattern
  private void skipWhitespace() {
    while (Character.isWhitespace(peek())) {
      next();
    }
  }

  private char expect(char c) {
    if (!hasNext()) {
      throw new ParseException("Expected '" + c + "'", true);
    }
    if (next() != c) {
      throw new ParseException("Expected '" + c + "'");
    }
    return c;
  }

  void parse(ProguardMap.Builder mapBuilder) throws IOException {
    // Read the first line.
    do {
      lineNo++;
      line = reader.readLine();
    } while (hasLine() && isEmptyOrCommentLine(line));
    parseClassMappings(mapBuilder);
  }

  // Parsing of entries

  private void parseClassMappings(ProguardMap.Builder mapBuilder) throws IOException {
    while (hasLine()) {
      String before = parseType(false);
      skipWhitespace();
      // Workaround for proguard map files that contain entries for package-info.java files.
      assert IdentifierUtils.isDexIdentifierPart('-');
      if (before.endsWith("package-info")) {
        skipLine();
        continue;
      }
      if (before.endsWith("-") && acceptString(">")) {
        // With - as a legal identifier part the grammar is ambiguous, and we treat a->b as a -> b,
        // and not as a- > b (which would be a parse error).
        before = before.substring(0, before.length() - 1);
      } else {
        skipWhitespace();
        acceptArrow();
      }
      skipWhitespace();
      String after = parseType(false);
      expect(':');
      ClassNaming.Builder currentClassBuilder = mapBuilder.classNamingBuilder(after, before);
      if (nextLine()) {
        parseMemberMappings(currentClassBuilder);
      }
    }
  }

  private void parseMemberMappings(ClassNaming.Builder classNamingBuilder) throws IOException {
    MemberNaming activeMemberNaming = null;
    Range previousObfuscatedRange = null;
    Signature previousSignature = null;
    String previousRenamedName = null;
    boolean lastRound = false;
    for (; ; ) {
      Signature signature = null;
      Object originalRange = null;
      String renamedName = null;
      Range obfuscatedRange = null;

      // In the last round we're only here to flush the last line read (which may trigger adding a
      // new MemberNaming) and flush activeMemberNaming, so skip parsing.
      if (!lastRound) {
        if (!Character.isWhitespace(peek())) {
          lastRound = true;
          continue;
        }
        skipWhitespace();
        Object maybeRangeOrInt = maybeParseRangeOrInt();
        if (maybeRangeOrInt != null) {
          if (!(maybeRangeOrInt instanceof Range)) {
            throw new ParseException(
                String.format("Invalid obfuscated line number range (%s).", maybeRangeOrInt));
          }
          obfuscatedRange = (Range) maybeRangeOrInt;
          expect(':');
        }
        signature = parseSignature();
        if (peek() == ':') {
          // This is a mapping or inlining definition
          next();
          originalRange = maybeParseRangeOrInt();
          if (originalRange == null) {
            throw new ParseException("No number follows the colon after the method signature.");
          }
        }
        skipWhitespace();
        skipArrow();
        skipWhitespace();
        renamedName = parseMethodName();
      }

      // If this line refers to a member that should be added to classNamingBuilder (as opposed to
      // an inner inlined callee) and it's different from the activeMemberNaming, then flush (add)
      // the current activeMemberNaming and create a new one.
      // We're also entering this in the last round when there's no current line.
      if (previousRenamedName != null
          && (!Objects.equals(previousObfuscatedRange, obfuscatedRange)
              || !Objects.equals(previousRenamedName, renamedName)
              || (originalRange != null && originalRange instanceof Range))) {
        // Flush activeMemberNaming if it's for a different member.
        if (activeMemberNaming != null) {
          if (!activeMemberNaming.getOriginalSignature().equals(previousSignature)) {
            classNamingBuilder.addMemberEntry(activeMemberNaming);
            activeMemberNaming = null;
          } else {
            assert (activeMemberNaming.getRenamedName().equals(previousRenamedName));
          }
        }
        if (activeMemberNaming == null) {
          activeMemberNaming = new MemberNaming(previousSignature, previousRenamedName);
        }
      }

      if (lastRound) {
        if (activeMemberNaming != null) {
          classNamingBuilder.addMemberEntry(activeMemberNaming);
        }
        break;
      }

      // Interpret what we've just parsed.
      if (obfuscatedRange == null) {
        if (originalRange != null) {
          throw new ParseException("No mapping for original range " + originalRange + ".");
        }
        // Here we have a line like 'a() -> b' or a field like 'a -> b'
        if (activeMemberNaming != null) {
          classNamingBuilder.addMemberEntry(activeMemberNaming);
        }
        activeMemberNaming = new MemberNaming(signature, renamedName);
      } else {

        // Note that at this point originalRange may be null which either means, it's the same as
        // the obfuscatedRange (identity mapping) or that it's unknown (source line number
        // information
        // was not available).

        assert signature instanceof MethodSignature;
      }

      if (signature instanceof MethodSignature) {
        classNamingBuilder.addMappedRange(
            obfuscatedRange, (MethodSignature) signature, originalRange, renamedName);
      }

      previousRenamedName = renamedName;
      previousObfuscatedRange = obfuscatedRange;
      previousSignature = signature;

      if (!nextLine()) {
        lastRound = true;
      }
    }
  }

  // Parsing of components

  private void skipIdentifier(boolean allowInit) {
    boolean isInit = false;
    if (allowInit && peek() == '<') {
      // swallow the leading < character
      next();
      isInit = true;
    }
    if (!IdentifierUtils.isDexIdentifierStart(peek())) {
      throw new ParseException("Identifier expected");
    }
    next();
    while (IdentifierUtils.isDexIdentifierPart(peek())) {
      next();
    }
    if (isInit) {
      expect('>');
    }
    if (IdentifierUtils.isDexIdentifierPart(peek())) {
      throw new ParseException("End of identifier expected");
    }
  }

  // Cache for canonicalizing strings.
  // This saves 10% of heap space for large programs.
  final HashMap<String, String> cache = new HashMap<>();

  private String substring(int start) {
    String result = line.substring(start, lineOffset);
    if (cache.containsKey(result)) {
      return cache.get(result);
    }
    cache.put(result, result);
    return result;
  }

  private String parseMethodName() {
    int startPosition = lineOffset;
    skipIdentifier(true);
    while (peek() == '.') {
      next();
      skipIdentifier(true);
    }
    return substring(startPosition);
  }

  private String parseType(boolean allowArray) {
    int startPosition = lineOffset;
    skipIdentifier(false);
    while (peek() == '.') {
      next();
      skipIdentifier(false);
    }
    if (allowArray) {
      while (peek() == '[') {
        next();
        expect(']');
      }
    }
    return substring(startPosition);
  }

  private Signature parseSignature() {
    String type = parseType(true);
    expect(' ');
    String name = parseMethodName();
    Signature signature;
    if (peek() == '(') {
      next();
      String[] arguments;
      if (peek() == ')') {
        arguments = new String[0];
      } else {
        List<String> items = new LinkedList<>();
        items.add(parseType(true));
        while (peek() != ')') {
          expect(',');
          items.add(parseType(true));
        }
        arguments = items.toArray(new String[items.size()]);
      }
      expect(')');
      signature = new MethodSignature(name, type, arguments);
    } else {
      signature = new FieldSignature(name, type);
    }
    return signature;
  }

  private void skipArrow() {
    expect('-');
    expect('>');
  }

  private boolean acceptArrow() {
    if (peek() == '-' && peek(1) == '>') {
      next();
      next();
      return true;
    }
    return false;
  }

  private boolean acceptString(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (peek(i) != s.charAt(i)) {
        return false;
      }
    }
    for (int i = 0; i < s.length(); i++) {
      next();
    }
    return true;
  }

  private Object maybeParseRangeOrInt() {
    if (!Character.isDigit(peek())) {
      return null;
    }
    int from = parseNumber();
    if (peek() != ':') {
      return from;
    }
    expect(':');
    int to = parseNumber();
    return new Range(from, to);
  }

  private int parseNumber() {
    int result = 0;
    if (!Character.isDigit(peek())) {
      throw new ParseException("Number expected");
    }
    do {
      result *= 10;
      result += Character.getNumericValue(next());
    } while (Character.isDigit(peek()));
    return result;
  }

  private class ParseException extends RuntimeException {

    private final int lineNo;
    private final int lineOffset;
    private final boolean eol;
    private final String msg;

    ParseException(String msg) {
      this(msg, false);
    }

    ParseException(String msg, boolean eol) {
      lineNo = ProguardMapReader.this.lineNo;
      lineOffset = ProguardMapReader.this.lineOffset;
      this.eol = eol;
      this.msg = msg;
    }

    @Override
    public String toString() {
      if (eol) {
        return "Parse error [" + lineNo + ":eol] " + msg;
      } else {
        return "Parse error [" + lineNo + ":" + lineOffset + "] " + msg;
      }
    }
  }
}
