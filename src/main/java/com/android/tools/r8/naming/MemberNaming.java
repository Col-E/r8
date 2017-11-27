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
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Type;

/**
 * Stores renaming information for a member.
 * <p>
 * This includes the signature, the original name and inlining range information.
 */
public class MemberNaming {

  private static int nextSequenceNumber = 0;

  private synchronized int getNextSequenceNumber() {
    return nextSequenceNumber++;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof MemberNaming)) {
      return false;
    }

    MemberNaming that = (MemberNaming) o;
    // sequenceNumber is intentionally omitted from equality test.
    return signature.equals(that.signature)
        && renamedSignature.equals(that.renamedSignature)
        && unconstrainedIdentityMapping == that.unconstrainedIdentityMapping
        && mappedRanges.equals(that.mappedRanges);
  }

  @Override
  public int hashCode() {
    // sequenceNumber is intentionally omitted from hashCode since it's not used in equality test.
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
   * List of obfuscated line number ranges, mapped to original line numbers. These mapped ranges
   * correspond to the "a:b:(signature)[:x[:y]] -> n" lines in the Proguard-map.
   */
  public final List<MappedRange> mappedRanges = new LinkedList<>();

  /// See the comment for the constructor.
  public final boolean unconstrainedIdentityMapping;

  /**
   * The sole purpose of {@link #sequenceNumber} is to preserve the order of members read from a
   * Proguard-map.
   */
  public final int sequenceNumber = getNextSequenceNumber();

  /**
   * {@code unconstrainedIdentityMapping = true} means that any obfuscated line number is the same
   * as the original. In Proguard-map syntax: " a() -> b" In other cases use
   * {@code unconstrainedIdentityMapping = false} and specify explicit ranges later with
   * {@link #addMappedRange}.
   */
  MemberNaming(Signature signature, String renamedName, boolean unconstrainedIdentityMapping) {
    this.signature = signature;
    this.renamedSignature = signature.asRenamed(renamedName);
    this.unconstrainedIdentityMapping = unconstrainedIdentityMapping;
  }

  /**
   * @param originalRange can be {@code null} which means unknown original range or same as the
   *     obfuscated range, @{code Integer} which indicates the caller of the preceding line, or
   *     {@link Range}.
   */
  public void addMappedRange(
      Range obfuscatedRange, MethodSignature signature, Object originalRange) {
    assert originalRange == null
        || originalRange instanceof Integer
        || originalRange instanceof Range;
    mappedRanges.add(new MappedRange(obfuscatedRange, originalRange, signature));
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
   * MappedRange describes an (original line numbers, signature) <-> (obfuscated line numbers)
   * mapping. It can describe two different things:
   *
   * <p>1. The source lines of a method in the original range are renumbered to the obfuscated
   * range. In this case the {@link MappedRange#originalRange} is either a {@code Range} or null,
   * indicating that the original range is unknown or is the same as the {@link
   * MappedRange#obfuscatedRange}
   *
   * <p>2. The source line of a method is the inlining caller of the previous {@code MappedRange}.
   * In this case the {@link MappedRange@originalRange} is either an {@code int} or null, indicating
   * that the original source line is unknown, or may be identical to a line of the obfuscated
   * range.
   */
  private class MappedRange {

    private final Range obfuscatedRange;
    private final Object originalRange; // null, Integer or Range
    private final MethodSignature signature;

    private MappedRange(Range obfuscatedRange, Object originalRange, MethodSignature signature) {
      assert obfuscatedRange != null;
      assert originalRange == null
          || originalRange instanceof Integer
          || originalRange instanceof Range;
      this.obfuscatedRange = obfuscatedRange;
      this.originalRange = originalRange;
      this.signature = signature;
    }

    private void write(Writer writer, boolean indent) throws IOException {
      if (indent) {
        writer.append("    ");
      }
      writer.append(obfuscatedRange.toString());
      writer.append(":");
      signature.write(writer);
      if (originalRange != null) {
        writer.append(':').append(originalRange.toString());
      }
      writer.append(" -> ").append(renamedSignature.name);
      writer.append("\n");
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

      return obfuscatedRange.equals(that.obfuscatedRange)
          && Objects.equals(originalRange, that.originalRange)
          && signature.equals(that.signature);
    }

    @Override
    public int hashCode() {
      int result = obfuscatedRange.hashCode();
      result = 31 * result + Objects.hashCode(originalRange);
      result = 31 * result + signature.hashCode();
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
