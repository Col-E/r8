// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Range;
import com.android.tools.r8.naming.MemberNaming.Signature;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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

  private boolean skipLine() throws IOException {
    lineNo++;
    lineOffset = 0;
    line = reader.readLine();
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
    if (next() != c) {
      throw new ParseException("Expected '" + c + "'");
    }
    return c;
  }

  void parse(ProguardMap.Builder mapBuilder) throws IOException {
    // Read the first line.
    line = reader.readLine();
    parseClassMappings(mapBuilder);
  }

  // Parsing of entries

  private void parseClassMappings(ProguardMap.Builder mapBuilder) throws IOException {
    while (hasLine()) {
      String before = parseType(false);
      skipWhitespace();
      // Workaround for proguard map files that contain entries for package-info.java files.
      if (!acceptArrow()) {
        // If this was a package-info line, we parsed the "package" string.
        if (!before.endsWith("package") || !acceptString("-info")) {
          throw new ParseException("Expected arrow after class name " + before);
        }
        skipLine();
        continue;
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
    Range previousTargetRange = null;
    Signature previousSignature = null;
    String previousRenamedName = null;
    List<Consumer<MemberNaming>> deferredChanges = new ArrayList<>(10);
    Consumer<MemberNaming> flushMemberNaming =
        m -> {
          if (m != null) {
            classNamingBuilder.addMemberEntry(m);
          }
        };
    boolean lastRound = false;
    for (; ; ) {
      Signature signature = null;
      Range originalRange = null;
      String renamedName = null;
      Range targetRange = null;

      // In the last round we're only here to flush deferredChanges and activeMemberNaming, so skip
      // parsing.
      if (!lastRound) {
        if (!Character.isWhitespace(peek())) {
          lastRound = true;
          continue;
        }
        skipWhitespace();
        Range maybeRange = maybeParseRange();
        if (maybeRange != null) {
          targetRange = maybeRange;
          expect(':');
        } else {
          targetRange = null;
        }
        signature = parseSignature();
        if (peek() == ':') {
          // This is a mapping or inlining definition
          next();
          originalRange = maybeParseRange();
          if (originalRange == null) {
            throw new ParseException("No number follows the colon after the method signature.");
          }
        }
        skipWhitespace();
        skipArrow();
        skipWhitespace();
        renamedName = parseMethodName();
      }

      // If there are deferred changes and the line we've just read cannot possibly belong to the
      // deferred changes (different target line numbers or renamed name) then we need to flush the
      // deferred changes now.
      // In the last round both targetRange and renamedName will be null so the condition will be
      // true.
      if (!deferredChanges.isEmpty()
          && (!Objects.equals(previousTargetRange, targetRange)
              || !Objects.equals(previousRenamedName, renamedName))) {
        // Flush activeMemberNaming if it's for a different member.
        if (activeMemberNaming != null
            && (!activeMemberNaming.getOriginalSignature().equals(previousSignature)
                || !activeMemberNaming.getRenamedName().equals(previousRenamedName))) {
          classNamingBuilder.addMemberEntry(activeMemberNaming);
          activeMemberNaming = null;
        }
        if (activeMemberNaming == null) {
          activeMemberNaming = new MemberNaming(previousSignature, previousRenamedName, false);
        }
        final MemberNaming finalActiveMemberNaming = activeMemberNaming;
        deferredChanges.forEach(ch -> ch.accept(finalActiveMemberNaming));
        deferredChanges.clear();
      }

      if (lastRound) {
        flushMemberNaming.accept(activeMemberNaming);
        assert deferredChanges.isEmpty();
        break;
      }

      // Interpret what we've just parsed.
      if (targetRange == null) {
        if (originalRange != null) {
          throw new ParseException("No mapping for original range " + originalRange + ".");
        }
        // Here we have a line like 'a() -> b' or a field like 'a -> b'
        flushMemberNaming.accept(activeMemberNaming);
        activeMemberNaming = new MemberNaming(signature, renamedName, true);
      } else {

        // Note that at this point originalRange may be null which either means, it's the same as
        // the targetRange (identity mapping) or that it's unknown (source line number information
        // was not available).

        assert signature instanceof MethodSignature;

        // Defer this change until we parse a line that has a different target range / renamed name.
        final Range finalTargetRange = targetRange;
        final MethodSignature finalSignature = (MethodSignature) signature;
        if (deferredChanges.isEmpty()) {
          // If this is the first deferred change with this target range / renamed name, then it's
          // either an actual original <-> target range mapping or the innermost callee of an
          // inlined stack.
          final Range finalOriginalRange =
              originalRange == null ? MemberNaming.UNSPECIFIED_RANGE : originalRange;
          deferredChanges.add(
              m -> m.addMappedRange(finalTargetRange, finalSignature, finalOriginalRange));
        } else {
          // This is not the first deferred change with this target range / renamed name, it must be
          // a caller. Add the original range as a single number.
          final int finalOriginalRangeTo =
              originalRange == null ? MemberNaming.UNSPECIFIED_LINE_NUMBER : originalRange.to;
          deferredChanges.add(
              m -> m.addCaller(finalTargetRange, finalSignature, finalOriginalRangeTo));
        }
      }

      previousRenamedName = renamedName;
      previousTargetRange = targetRange;
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
    if (!Character.isJavaIdentifierStart(peek())) {
      throw new ParseException("Identifier expected");
    }
    next();
    while (Character.isJavaIdentifierPart(peek())) {
      next();
    }
    if (isInit) {
      expect('>');
    }
    if (Character.isJavaIdentifierPart(peek())) {
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

  private Range maybeParseRange() {
    if (!Character.isDigit(peek())) {
      return null;
    }
    int from = parseNumber();
    if (peek() != ':') {
      return new Range(from, from);
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
    private final String msg;

    ParseException(String msg) {
      lineNo = ProguardMapReader.this.lineNo;
      lineOffset = ProguardMapReader.this.lineOffset;
      this.msg = msg;
    }

    @Override
    public String toString() {
      return "Parse error [" + lineNo + ":" + lineOffset + "] " + msg;
    }
  }
}
