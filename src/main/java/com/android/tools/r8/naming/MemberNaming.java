// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.JAVA_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.DescriptorUtils.javaTypeToDescriptor;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.Signature.SignatureKind;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.objectweb.asm.Type;

/**
 * Stores renaming information for a member.
 *
 * <p>This includes the signature and the original name.
 */
public class MemberNaming implements MappingWithResidualInfo {

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
        && renamedName.equals(that.renamedName)
        && Objects.equals(residualSignature, that.residualSignature);
  }

  @Override
  public int hashCode() {
    int result = signature.hashCode();
    result = 31 * result + renamedName.hashCode();
    result = 31 * result + Objects.hashCode(residualSignature);
    return result;
  }

  /** Original signature of the member. */
  private final Signature signature;
  /** Residual signature where types and names could be changed. */
  private Signature residualSignature = null;
  /**
   * Renamed name in the mapping file. This value will always be present even if the residual
   * signature is not.
   */
  private final String renamedName;
  /** Position of the member in the file. */
  private final Position position;

  public MemberNaming(Signature signature, String renamedName) {
    this(signature, renamedName, Position.UNKNOWN);
  }

  public MemberNaming(Signature signature, Signature residualSignature) {
    this(signature, residualSignature.getName(), Position.UNKNOWN);
    this.residualSignature = residualSignature;
  }

  public MemberNaming(Signature signature, String renamedName, Position position) {
    this.signature = signature;
    this.renamedName = renamedName;
    this.position = position;
  }

  @Override
  public Signature getOriginalSignature() {
    return signature;
  }

  public String getOriginalName() {
    return signature.name;
  }

  @Override
  public boolean hasResidualSignature() {
    return residualSignature != null;
  }

  @Override
  public String getRenamedName() {
    return renamedName;
  }

  public boolean isMethodNaming() {
    return signature.kind() == SignatureKind.METHOD;
  }

  public boolean isFieldNaming() {
    return signature.kind() == SignatureKind.FIELD;
  }

  public Position getPosition() {
    return position;
  }

  @Override
  public String toString() {
    return signature.toString() + " -> " + renamedName;
  }

  @Override
  public Signature getResidualSignatureInternal() {
    return residualSignature;
  }

  @Override
  public void setResidualSignatureInternal(Signature signature) {
    this.residualSignature = signature;
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

    public abstract Signature computeResidualSignature(
        String renamedName, Function<String, String> typeNameMapper);

    public boolean isQualified() {
      return name.indexOf(JAVA_PACKAGE_SEPARATOR) != -1;
    }

    public String toUnqualifiedName() {
      assert isQualified();
      return name.substring(name.lastIndexOf(JAVA_PACKAGE_SEPARATOR) + 1);
    }

    public String toHolderFromQualified() {
      assert isQualified();
      return name.substring(0, name.lastIndexOf(JAVA_PACKAGE_SEPARATOR));
    }

    public boolean isMethodSignature() {
      return false;
    }

    public boolean isFieldSignature() {
      return false;
    }

    public MethodSignature asMethodSignature() {
      return null;
    }

    public FieldSignature asFieldSignature() {
      return null;
    }

    @Override
    public String toString() {
      try {
        StringWriter writer = new StringWriter();
        write(writer);
        return writer.toString();
      } catch (IOException e) {
        // StringWriter is not throwing IOException
        throw new Unreachable(e);
      }
    }

    public String getName() {
      return name;
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
      return fromDexField(field, false);
    }

    public static FieldSignature fromDexField(DexField field, boolean withQualifiedName) {
      return new FieldSignature(
          withQualifiedName ? field.qualifiedName() : field.name.toSourceString(),
          field.type.toSourceString());
    }

    public static FieldSignature fromFieldReference(FieldReference fieldReference) {
      return new FieldSignature(
          fieldReference.getFieldName(), fieldReference.getFieldType().getTypeName());
    }

    public DexField toDexField(DexItemFactory factory, DexType clazz) {
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
    public FieldSignature computeResidualSignature(
        String renamedName, Function<String, String> typeNameMapper) {
      return new FieldSignature(renamedName, DescriptorUtils.mapTypeName(type, typeNameMapper));
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

    @Override
    public boolean isFieldSignature() {
      return true;
    }

    @Override
    public FieldSignature asFieldSignature() {
      return this;
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
      this.parameters = parameters.toArray(StringUtils.EMPTY_ARRAY);
    }

    public static MethodSignature fromDexMethod(DexMethod method) {
      return fromDexMethod(method, false);
    }

    public static MethodSignature fromDexMethod(DexMethod method, boolean withQualifiedName) {
      String[] paramNames = new String[method.getArity()];
      DexType[] values = method.proto.parameters.values;
      for (int i = 0; i < values.length; i++) {
        paramNames[i] = values[i].toSourceString();
      }
      return new MethodSignature(
          withQualifiedName ? method.qualifiedName() : method.name.toSourceString(),
          method.proto.returnType.toSourceString(),
          paramNames);
    }

    public static MethodSignature fromSignature(String name, String signature) {
      Type returnDescriptor = Type.getReturnType(signature);
      return new MethodSignature(
          name,
          DescriptorUtils.descriptorToJavaType(returnDescriptor.getDescriptor()),
          ArrayUtils.mapToStringArray(
              Type.getArgumentTypes(signature),
              param -> DescriptorUtils.descriptorToJavaType(param.getDescriptor())));
    }

    public static MethodSignature fromMethodReference(MethodReference reference) {
      TypeReference returnType = reference.getReturnType();
      return new MethodSignature(
          reference.getMethodName(),
          returnType == null ? "void" : returnType.getTypeName(),
          CollectionUtils.mapToStringArray(reference.getFormalTypes(), TypeReference::getTypeName));
    }

    public MethodSignature toUnqualified() {
      assert isQualified();
      return new MethodSignature(toUnqualifiedName(), type, parameters);
    }

    public DexMethod toDexMethod(DexItemFactory factory, DexType clazz) {
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
    MethodSignature asRenamed(String renamedName) {
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
    public String toString() {
        return type + ' ' + name + '(' + String.join(",", parameters) + ')';
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

    public String toDescriptor() {
      StringBuilder sb = new StringBuilder();
      sb.append('(');
      for (String parameterType : parameters) {
        sb.append(javaTypeToDescriptor(parameterType));
      }
      sb.append(')');
      sb.append(javaTypeToDescriptor(type));
      return sb.toString();
    }

    @Override
    public MethodSignature computeResidualSignature(
        String renamedName, Function<String, String> typeNameMapper) {
      return new MethodSignature(
          renamedName,
          DescriptorUtils.mapTypeName(type, typeNameMapper),
          ArrayUtils.mapToStringArray(
              parameters,
              parameterTypeName -> DescriptorUtils.mapTypeName(parameterTypeName, typeNameMapper)));
    }

    @Override
    public boolean isMethodSignature() {
      return true;
    }

    @Override
    public MethodSignature asMethodSignature() {
      return this;
    }
  }
}
