// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.MemberNaming.Signature.SignatureKind;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stores name information for a class.
 * <p>
 * This includes how the class was renamed and information on the classes members.
 */
public class ClassNamingForNameMapper implements ClassNaming {

  public static class Builder extends ClassNaming.Builder {
    private final String originalName;
    private final String renamedName;
    private final Map<MethodSignature, MemberNaming> methodMembers = new HashMap<>();
    private final Map<FieldSignature, MemberNaming> fieldMembers = new HashMap<>();
    private final Map<String, List<MappedRange>> mappedRangesByName = new HashMap<>();

    private Builder(String renamedName, String originalName) {
      this.originalName = originalName;
      this.renamedName = renamedName;
    }

    @Override
    public ClassNaming.Builder addMemberEntry(MemberNaming entry) {
      if (entry.isMethodNaming()) {
        methodMembers.put((MethodSignature) entry.getRenamedSignature(), entry);
      } else {
        fieldMembers.put((FieldSignature) entry.getRenamedSignature(), entry);
      }
      return this;
    }

    @Override
    public ClassNamingForNameMapper build() {
      Map<String, MappedRangesOfName> map;

      if (mappedRangesByName.isEmpty()) {
        map = Collections.emptyMap();
      } else {
        map = new HashMap<>(mappedRangesByName.size());
        for (Map.Entry<String, List<MappedRange>> entry : mappedRangesByName.entrySet()) {
          map.put(entry.getKey(), new MappedRangesOfName(entry.getValue()));
        }
      }

      return new ClassNamingForNameMapper(
          renamedName, originalName, methodMembers, fieldMembers, map);
    }

    /** The parameters are forwarded to MappedRange constructor, see explanation there. */
    @Override
    public void addMappedRange(
        Range obfuscatedRange,
        MemberNaming.MethodSignature originalSignature,
        Object originalRange,
        String obfuscatedName) {
      mappedRangesByName
          .computeIfAbsent(obfuscatedName, k -> new ArrayList<>())
          .add(new MappedRange(obfuscatedRange, originalSignature, originalRange, obfuscatedName));
    }
  }

  /** List of MappedRanges that belong to the same obfuscated name. */
  public static class MappedRangesOfName {
    private final List<MappedRange> mappedRanges;

    MappedRangesOfName(List<MappedRange> mappedRanges) {
      this.mappedRanges = mappedRanges;
    }

    /**
     * Return the first MappedRange that contains {@code line}. Return general MappedRange ("a() ->
     * b") if no concrete mapping found.
     */
    public MappedRange firstRangeForLine(int line) {
      MappedRange bestRange = null;
      for (MappedRange range : mappedRanges) {
        if (range.obfuscatedRange == null) {
          if (bestRange == null) {
            // This is an "a() -> b" mapping (no concrete line numbers), remember this if there'll
            // be no better one.
            bestRange = range;
          }
        } else if (range.obfuscatedRange.contains(line)) {
          // Concrete obfuscated range found ("x:y:a()[:u[:v]] -> b")
          return range;
        }
      }
      return bestRange;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      MappedRangesOfName that = (MappedRangesOfName) o;

      return mappedRanges.equals(that.mappedRanges);
    }

    @Override
    public int hashCode() {
      return mappedRanges.hashCode();
    }
  }

  static Builder builder(String renamedName, String originalName) {
    return new Builder(renamedName, originalName);
  }

  public final String originalName;
  private final String renamedName;

  /**
   * Mapping from the renamed signature to the naming information for a member.
   * <p>
   * A renamed signature is a signature where the member's name has been obfuscated but not the type
   * information.
   **/
  private final ImmutableMap<MethodSignature, MemberNaming> methodMembers;
  private final ImmutableMap<FieldSignature, MemberNaming> fieldMembers;

  /** Map of obfuscated name -> MappedRangesOfName */
  public final Map<String, MappedRangesOfName> mappedRangesByName;

  private ClassNamingForNameMapper(
      String renamedName,
      String originalName,
      Map<MethodSignature, MemberNaming> methodMembers,
      Map<FieldSignature, MemberNaming> fieldMembers,
      Map<String, MappedRangesOfName> mappedRangesByName) {
    this.renamedName = renamedName;
    this.originalName = originalName;
    this.methodMembers = ImmutableMap.copyOf(methodMembers);
    this.fieldMembers = ImmutableMap.copyOf(fieldMembers);
    this.mappedRangesByName = mappedRangesByName;
  }

  @Override
  public MemberNaming lookup(Signature renamedSignature) {
    if (renamedSignature.kind() == SignatureKind.METHOD) {
      assert renamedSignature instanceof MethodSignature;
      return methodMembers.get(renamedSignature);
    } else {
      assert renamedSignature.kind() == SignatureKind.FIELD;
      assert renamedSignature instanceof FieldSignature;
      return fieldMembers.get(renamedSignature);
    }
  }

  @Override
  public MemberNaming lookupByOriginalSignature(Signature original) {
    if (original.kind() == SignatureKind.METHOD) {
      for (MemberNaming memberNaming: methodMembers.values()) {
        if (memberNaming.signature.equals(original)) {
          return memberNaming;
        }
      }
      return null;
    } else {
      assert original.kind() == SignatureKind.FIELD;
      for (MemberNaming memberNaming : fieldMembers.values()) {
        if (memberNaming.signature.equals(original)) {
          return memberNaming;
        }
      }
      return null;
    }
  }

  public List<MemberNaming> lookupByOriginalName(String originalName) {
    List<MemberNaming> result = new ArrayList<>();
    for (MemberNaming naming : methodMembers.values()) {
      if (naming.signature.name.equals(originalName)) {
        result.add(naming);
      }
    }
    for (MemberNaming naming : fieldMembers.values()) {
      if (naming.signature.name.equals(originalName)) {
        result.add(naming);
      }
    }
    return result;
  }

  @Override
  public <T extends Throwable> void forAllMemberNaming(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    forAllFieldNaming(consumer);
    forAllMethodNaming(consumer);
  }

  @Override
  public <T extends Throwable> void forAllFieldNaming(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    for (MemberNaming naming : fieldMembers.values()) {
      consumer.accept(naming);
    }
  }

  @Override
  public <T extends Throwable> void forAllMethodNaming(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    for (MemberNaming naming : methodMembers.values()) {
      consumer.accept(naming);
    }
  }

  void write(Writer writer) throws IOException {
    writer.append(originalName);
    writer.append(" -> ");
    writer.append(renamedName);
    writer.append(":\n");

    // First print non-method MemberNamings.
    forAllMemberNaming(
        m -> {
          if (!m.isMethodNaming()) {
            writer.append("    ").append(m.toString()).append('\n');
          }
        });

    // Sort MappedRanges by sequence number to restore construction order (original Proguard-map
    // input)
    List<MappedRange> rangeList = new ArrayList<>();
    for (MappedRangesOfName ranges : mappedRangesByName.values()) {
      rangeList.addAll(ranges.mappedRanges);
    }
    rangeList.sort(
        (lhs, rhs) -> {
          return lhs.sequenceNumber - rhs.sequenceNumber;
        });
    for (MappedRange range : rangeList) {
      writer.append("    ").append(range.toString()).append('\n');
    }
  }

  @Override
  public String toString() {
    try {
      StringWriter writer = new StringWriter();
      write(writer);
      return writer.toString();
    } catch (IOException e) {
      return e.toString();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClassNamingForNameMapper)) {
      return false;
    }

    ClassNamingForNameMapper that = (ClassNamingForNameMapper) o;

    return originalName.equals(that.originalName)
        && renamedName.equals(that.renamedName)
        && methodMembers.equals(that.methodMembers)
        && fieldMembers.equals(that.fieldMembers)
        && mappedRangesByName.equals(that.mappedRangesByName);
  }

  @Override
  public int hashCode() {
    int result = originalName.hashCode();
    result = 31 * result + renamedName.hashCode();
    result = 31 * result + methodMembers.hashCode();
    result = 31 * result + fieldMembers.hashCode();
    result = 31 * result + mappedRangesByName.hashCode();
    return result;
  }

  /**
   * MappedRange describes an (original line numbers, signature) <-> (obfuscated line numbers)
   * mapping. It can describe 3 different things:
   *
   * <p>1. The method is renamed. The original source lines are preserved. The corresponding
   * Proguard-map syntax is "a(...) -> b"
   *
   * <p>2. The source lines of a method in the original range are renumbered to the obfuscated
   * range. In this case the {@link MappedRange#originalRange} is either a {@code Range} or null,
   * indicating that the original range is unknown or is the same as the {@link
   * MappedRange#obfuscatedRange}. The corresponding Proguard-map syntax is "x:y:a(...) -> b" or
   * "x:y:a(...):u:v -> b"
   *
   * <p>3. The source line of a method is the inlining caller of the previous {@code MappedRange}.
   * In this case the {@link MappedRange@originalRange} is either an {@code int} or null, indicating
   * that the original source line is unknown, or may be identical to a line of the obfuscated
   * range. The corresponding Proguard-map syntax is "x:y:a(...) -> b" or "x:y:a(...):u -> b"
   */
  public static class MappedRange {

    private static int nextSequenceNumber = 0;

    private synchronized int getNextSequenceNumber() {
      return nextSequenceNumber++;
    }

    public final Range obfuscatedRange; // Can be null, if so then originalRange must also be null.
    public final MethodSignature signature;
    public final Object originalRange; // null, Integer or Range.
    public final String obfuscatedName;

    /**
     * The sole purpose of {@link #sequenceNumber} is to preserve the order of members read from a
     * Proguard-map.
     */
    private final int sequenceNumber = getNextSequenceNumber();

    private MappedRange(
        Range obfuscatedRange,
        MethodSignature signature,
        Object originalRange,
        String obfuscatedName) {

      assert obfuscatedRange != null || originalRange == null;
      assert originalRange == null
          || originalRange instanceof Integer
          || originalRange instanceof Range;

      this.obfuscatedRange = obfuscatedRange;
      this.signature = signature;
      this.originalRange = originalRange;
      this.obfuscatedName = obfuscatedName;
    }

    public int originalLineFromObfuscated(int obfuscatedLineNumber) {
      if (obfuscatedRange == null) {
        // General mapping without concrete line numbers: "a() -> b"
        return obfuscatedLineNumber;
      }
      assert obfuscatedRange.contains(obfuscatedLineNumber);
      if (originalRange == null) {
        // Concrete identity mapping: "x:y:a() -> b"
        return obfuscatedLineNumber;
      } else if (originalRange instanceof Integer) {
        // Inlinee: "x:y:a():u -> b"
        return (int) originalRange;
      } else {
        // "x:y:a():u:v -> b"
        assert originalRange instanceof Range;
        return ((Range) originalRange).from + obfuscatedLineNumber - obfuscatedRange.from;
      }
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      if (obfuscatedRange != null) {
        builder.append(obfuscatedRange).append(':');
      }
      builder.append(signature);
      if (originalRange != null) {
        builder.append(":").append(originalRange);
      }
      builder.append(" -> ").append(obfuscatedName);
      return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
      // sequenceNumber is intentionally omitted from equality test since it doesn't contribute to
      // identity.
      if (this == o) {
        return true;
      }
      if (!(o instanceof MappedRange)) {
        return false;
      }

      MappedRange that = (MappedRange) o;

      return Objects.equals(obfuscatedRange, that.obfuscatedRange)
          && Objects.equals(originalRange, that.originalRange)
          && signature.equals(that.signature)
          && obfuscatedName.equals(that.obfuscatedName);
    }

    @Override
    public int hashCode() {
      // sequenceNumber is intentionally omitted from hashCode since it's not used in equality test.
      int result = Objects.hashCode(obfuscatedRange);
      result = 31 * result + Objects.hashCode(originalRange);
      result = 31 * result + signature.hashCode();
      result = 31 * result + obfuscatedName.hashCode();
      return result;
    }
  }
}

