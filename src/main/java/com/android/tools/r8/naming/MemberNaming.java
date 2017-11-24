// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptor;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.Signature.SignatureKind;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import org.objectweb.asm.Type;

/**
 * Stores renaming information for a member.
 * <p>
 * This includes the signature, the original name and inlining range information.
 */
public class MemberNaming {

  public static final int UNSPECIFIED_LINE_NUMBER = Integer.MIN_VALUE;
  public static final Range UNSPECIFIED_RANGE =
      new Range(UNSPECIFIED_LINE_NUMBER, UNSPECIFIED_LINE_NUMBER);

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MemberNaming)) {
      return false;
    }

    MemberNaming that = (MemberNaming) o;
    return signature.equals(that.signature)
        && renamedSignature.equals(that.renamedSignature)
        && unconstrainedIdentityMapping == that.unconstrainedIdentityMapping
        && mappedRanges.equals(that.mappedRanges);
  }

  @Override
  public int hashCode() {
    int result = signature.hashCode();
    result = 31 * result + renamedSignature.hashCode();
    result = 31 * result + mappedRanges.hashCode();
    result = 31 * result + (unconstrainedIdentityMapping ? 1 : 0);
    return result;
  }

  /**
   * Original signature of the member
   */
  final Signature signature;
  /**
   * Renamed signature where the name (but not the types) have been renamed.
   */
  final Signature renamedSignature;

  /**
   * List of line number ranges, mapped from original to target line numbers. These mapped ranges
   * are either simple ranges or inlined ranges.
   */
  public final List<MappedRange> mappedRanges = new LinkedList<>();

  /// See the comment for the constructor.
  public final boolean unconstrainedIdentityMapping;

  /**
   * {@code unconstrainedIdentityMapping = true} means that any line number of the method with the
   * name of renamedName is actually the same line number in the method signature. In Proguard-map
   * syntax: "    a() -> b"
   * In other cases use {@code unconstrainedIdentityMapping = false} and specify explicit ranges
   * later with {@link #addMappedRange}.
   */
  MemberNaming(Signature signature, String renamedName, boolean unconstrainedIdentityMapping) {
    this.signature = signature;
    this.renamedSignature = signature.asRenamed(renamedName);
    this.unconstrainedIdentityMapping = unconstrainedIdentityMapping;
  }

  /**
   * {@code originalRange} can be {@link #UNSPECIFIED_RANGE} which indicates either that it's the
   * same as the {@code targetRange} or that it's not known.
   */
  public void addMappedRange(Range targetRange, MethodSignature signature, Range originalRange) {
    mappedRanges.add(new MappedRange(targetRange, originalRange, signature));
  }

  /**
   * Specify inlining callers for the {@code targetRange} added previously with
   * {@link #addMappedRange}
   * {@code originalLineInCaller} can be {@link #UNSPECIFIED_LINE_NUMBER} which indicates it's
   * either not known or the same as {@code targetRange.to}.
   * Make the {@link #addCaller} calls in the same order as if moving outwards on the stack frame.
   */
  public void addCaller(Range targetRange, MethodSignature signature, int originalLineInCaller) {
    assert !mappedRanges.isEmpty();
    // Find the corresponding mappedRange. Usually we're adding callers to the last mappedRange,
    // start with that one.
    ListIterator<MappedRange> iterator = mappedRanges.listIterator(mappedRanges.size());
    do {
      MappedRange item = iterator.previous();
      if (item.targetRange.equals(targetRange)) {
        item.addCaller(originalLineInCaller, signature);
        return;
      }
    } while (iterator.hasPrevious());
    assert false;
  }

  /**
   * Return the smallest target line number that can be found in {@link #mappedRanges}. Otherwise
   * return 1.
   * Between ranges with identical from value, favor greater ranges, because in Proguard-maps the
   * full range of a method is usually printed before the inlining details which span less lines.
   */
  public int firstTargetLineNumber() {
    Range bestRange = null;
    for (MappedRange r : mappedRanges) {
      if (bestRange == null
          || (r.targetRange.from < bestRange.from
              || (r.targetRange.from == bestRange.from && r.targetRange.to > bestRange.to))) {
        bestRange = r.targetRange;
      }
    }
    return bestRange == null ? 1 : bestRange.from;
  }

  public Signature getOriginalSignature() {
    return signature;
  }

  public String getOriginalName() {
    return signature.name;
  }

  public Signature getRenamedSignature() {
    return renamedSignature;
  }

  public String getRenamedName() {
    return renamedSignature.name;
  }

  public boolean isMethodNaming() {
    return signature.kind() == SignatureKind.METHOD;
  }

  protected void write(Writer writer, boolean indent) throws IOException {
    if (unconstrainedIdentityMapping) {
      if (indent) {
        writer.append("    ");
      }
      signature.write(writer);
      writer.append(" -> ");
      writer.append(renamedSignature.name);
      writer.append("\n");
    }
    for (MappedRange mappedRange : mappedRanges) {
      mappedRange.write(writer, indent);
    }
  }

  @Override
  public String toString() {
    try {
      StringWriter writer = new StringWriter();
      write(writer, false);
      return writer.toString();
    } catch (IOException e) {
      return e.toString();
    }
  }

  public abstract static class Signature {

    public final String name;

    protected Signature(String name) {
      this.name = name;
    }

    abstract Signature asRenamed(String renamedName);

    abstract public SignatureKind kind();

    @Override
    abstract public boolean equals(Object o);

    @Override
    abstract public int hashCode();

    abstract void write(Writer builder) throws IOException;

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

    enum SignatureKind {
      METHOD,
      FIELD
    }
  }

  public static class FieldSignature extends Signature {

    public final String type;

    public FieldSignature(String name, String type) {
      super(name);
      this.type = type;
    }

    public static FieldSignature fromDexField(DexField field) {
      return new FieldSignature(field.name.toSourceString(),
          field.type.toSourceString());
    }

    DexField toDexField(DexItemFactory factory, DexType clazz) {
      return factory.createField(
          clazz,
          factory.createType(javaTypeToDescriptor(type)),
          factory.createString(name));
    }

    @Override
    Signature asRenamed(String renamedName) {
      return new FieldSignature(renamedName, type);
    }

    @Override
    public SignatureKind kind() {
      return SignatureKind.FIELD;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof FieldSignature)) {
        return false;
      }
      FieldSignature that = (FieldSignature) o;
      return name.equals(that.name) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
      return name.hashCode() * 31 + type.hashCode();
    }

    @Override
    public String toString() {
      return type + " " + name;
    }

    @Override
    void write(Writer writer) throws IOException {
      writer.append(type);
      writer.append(' ');
      writer.append(name);
    }
  }

  public static class MethodSignature extends Signature {

    public final String type;
    public final String[] parameters;

    public MethodSignature(String name, String type, String[] parameters) {
      super(name);
      this.type = type;
      this.parameters = parameters;
    }

    public MethodSignature(String name, String type, Collection<String> parameters) {
      super(name);
      this.type = type;
      this.parameters = parameters.toArray(new String[parameters.size()]);
    }

    public static MethodSignature fromDexMethod(DexMethod method) {
      String[] paramNames = new String[method.getArity()];
      DexType[] values = method.proto.parameters.values;
      for (int i = 0; i < values.length; i++) {
        paramNames[i] = values[i].toSourceString();
      }
      return new MethodSignature(method.name.toSourceString(),
          method.proto.returnType.toSourceString(), paramNames);
    }

    public static MethodSignature fromSignature(String name, String signature) {
      Type[] parameterDescriptors = Type.getArgumentTypes(signature);
      Type returnDescriptor = Type.getReturnType(signature);
      String[] parameterTypes = new String[parameterDescriptors.length];
      for (int i = 0; i < parameterDescriptors.length; i++) {
        parameterTypes[i] =
            DescriptorUtils.descriptorToJavaType(parameterDescriptors[i].getDescriptor());
      }
      return new MethodSignature(
          name,
          DescriptorUtils.descriptorToJavaType(returnDescriptor.getDescriptor()),
          parameterTypes);
    }

    DexMethod toDexMethod(DexItemFactory factory, DexType clazz) {
      DexType[] paramTypes = new DexType[parameters.length];
      for (int i = 0; i < parameters.length; i++) {
        paramTypes[i] = factory.createType(javaTypeToDescriptor(parameters[i]));
      }
      DexType returnType = factory.createType(javaTypeToDescriptor(type));
      return factory.createMethod(
          clazz,
          factory.createProto(returnType, paramTypes),
          factory.createString(name));
    }

    public static MethodSignature initializer(String[] parameters) {
      return new MethodSignature(Constants.INSTANCE_INITIALIZER_NAME, "void", parameters);
    }

    @Override
    Signature asRenamed(String renamedName) {
      return new MethodSignature(renamedName, type, parameters);
    }

    @Override
    public SignatureKind kind() {
      return SignatureKind.METHOD;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof MethodSignature)) {
        return false;
      }

      MethodSignature that = (MethodSignature) o;
      return type.equals(that.type)
          && name.equals(that.name)
          && Arrays.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
      return (type.hashCode() * 17
          + name.hashCode()) * 31
          + Arrays.hashCode(parameters);
    }

    @Override
    void write(Writer writer) throws IOException {
      writer.append(type)
          .append(' ')
          .append(name)
          .append('(');
      for (int i = 0; i < parameters.length; i++) {
        writer.append(parameters[i]);
        if (i < parameters.length - 1) {
          writer.append(',');
        }
      }
      writer.append(')');
    }
  }

  /**
   * MappedRange describes an (original line numbers, signature) <-> (target line numbers) mapping
   * with an optional list of inlining callers. If there are no inlining callers the signature is
   * expected to be identical to the containing MemberNaming's signature (not enforced).
   */
  private class MappedRange {
    /**
     * Caller is an inlining caller with a single original line number.
     */
    private class Caller {
      private final int originalLine; // Can be UNSPECIFIED_LINE_NUMBER.
      private final MethodSignature signature;

      private Caller(int originalLine, MethodSignature signature) {
        this.originalLine = originalLine;
        this.signature = signature;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        }
        if (!(o instanceof Caller)) {
          return false;
        }

        Caller that = (Caller) o;

        return originalLine == that.originalLine && signature.equals(that.signature);
      }

      @Override
      public int hashCode() {
        int result = originalLine;
        result = 31 * result + signature.hashCode();
        return result;
      }
    }

    private final Range targetRange;
    private final Range originalRange;
    private final MethodSignature signature;

    /**
     * Optional list of callers. If they are present, the last caller's signature is expected to be
     * identical to the containing MemberNaming's signature (not enforced).
     */
    private List<Caller> callers = null;

    /**
     * {@code originalRange} can be {@link #UNSPECIFIED_RANGE}, which indicates that will be
     * replaced by {@code targetRange}, indicating an identity mapping.
     */
    private MappedRange(Range targetRange, Range originalRange, MethodSignature signature) {
      assert targetRange != null && originalRange != null;
      this.targetRange = targetRange;
      this.originalRange = originalRange;
      this.signature = signature;
    }

    private void addCaller(int originalLine, MethodSignature signature) {
      if (callers == null) {
        callers = new ArrayList<>();
      }
      callers.add(new Caller(originalLine, signature));
    }

    private void write(Writer writer, boolean indent) throws IOException {
      if (indent) {
        writer.append("    ");
      }
      writer.append(targetRange.toString());
      writer.append(":");
      signature.write(writer);
      if (!originalRange.equals(targetRange) && originalRange != UNSPECIFIED_RANGE) {
        writer.append(':')
            .append(originalRange.toString());
      }
      writer.append(" -> ")
          .append(renamedSignature.name);
      writer.append("\n");
      if (callers != null) {
        for (Caller caller : callers) {
          if (indent) {
            writer.append("    ");
          }
          writer.append(targetRange.toString());
          writer.append(":");
          caller.signature.write(writer);
          if (caller.originalLine != UNSPECIFIED_LINE_NUMBER) {
            writer.append(':').append(String.valueOf(caller.originalLine));
          }
          writer.append(" -> ").append(renamedSignature.name);
          writer.append("\n");
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof MappedRange)) {
        return false;
      }

      MappedRange that = (MappedRange) o;

      return targetRange.equals(that.targetRange)
          && originalRange.equals(that.originalRange)
          && signature.equals(that.signature)
          && Objects.equals(callers, that.callers);
    }

    @Override
    public int hashCode() {
      int result = targetRange.hashCode();
      result = 31 * result + originalRange.hashCode();
      result = 31 * result + signature.hashCode();
      result = 31 * result + Objects.hashCode(callers);
      return result;
    }
  }

  /**
   * Represents a linenumber range.
   */
  public static class Range {

    public final int from;
    public final int to;

    Range(int from, int to) {
      this.from = from;
      this.to = to;
    }

    public boolean contains(int value) {
      return from <= value && value <= to;
    }

    @Override
    public String toString() {
      return from + ":" + to;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Range)) {
        return false;
      }

      Range range = (Range) o;
      return from == range.from && to == range.to;
    }

    @Override
    public int hashCode() {
      int result = from;
      result = 31 * result + to;
      return result;
    }

  }
}
