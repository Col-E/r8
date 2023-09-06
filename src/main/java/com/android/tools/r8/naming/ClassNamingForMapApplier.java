// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.MemberNaming.Signature.SignatureKind;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Stores name information for a class.
 * <p>
 * The main differences of this against {@link ClassNamingForNameMapper} are:
 *   1) field and method mappings are maintained and searched separately for faster lookup;
 *   2) similar to the relation between {@link ClassNameMapper} and {@link SeedMapper}, this one
 *   uses original {@link Signature} as a key to look up {@link MemberNaming},
 *   whereas {@link ClassNamingForNameMapper} uses renamed {@link Signature} as a key; and thus
 *   3) logic of {@link #lookup} and {@link #lookupByOriginalSignature} are inverted; and
 *   4) {@link #lookupByOriginalItem}'s are introduced for lightweight lookup.
 */
public class ClassNamingForMapApplier implements ClassNaming {

  public static class Builder extends ClassNaming.Builder {

    private final String originalName;
    private final String renamedName;
    private final Position position;
    private final Reporter reporter;
    private final Map<MethodSignature, List<MemberNaming>> qualifiedMethodMembers = new HashMap<>();
    private final Map<MethodSignature, MemberNaming> methodMembers = new HashMap<>();
    private final Map<FieldSignature, MemberNaming> fieldMembers = new HashMap<>();

    private Builder(String renamedName, String originalName, Position position, Reporter reporter) {
      this.originalName = originalName;
      this.renamedName = renamedName;
      this.position = position;
      this.reporter = reporter;
    }

    @Override
    public ClassNaming.Builder addMemberEntry(MemberNaming entry) {
      // Unlike {@link ClassNamingForNameMapper.Builder#addMemberEntry},
      // the key is original signature.
      if (entry.isMethodNaming()) {
        MethodSignature signature = entry.getOriginalSignature().asMethodSignature();
        if (signature.isQualified()) {
          qualifiedMethodMembers.computeIfAbsent(signature, k -> new ArrayList<>(2)).add(entry);
        } else if (methodMembers.put(signature, entry) != null) {
          // TODO(b/293630963): We are simply not able to handle positions correctly for outlines
          //  at this point. Remove when we do not call GraphLens.getOriginalMethodSignature when
          //  constructing positions.
          if (true) {
            return this;
          }
          reporter.error(
              ProguardMapError.duplicateSourceMember(
                  signature.toString(), this.originalName, entry.getPosition()));
        }
      } else {
        FieldSignature signature = entry.getOriginalSignature().asFieldSignature();
        if (!signature.isQualified() && fieldMembers.put(signature, entry) != null) {
          reporter.error(
              ProguardMapError.duplicateSourceMember(
                  signature.toString(), this.originalName, entry.getPosition()));
        }
      }
      return this;
    }

    @Override
    public MemberNaming lookupMemberEntry(Signature signature) {
      return null;
    }

    @Override
    public ClassNamingForMapApplier build() {
      return new ClassNamingForMapApplier(
          renamedName, originalName, position, qualifiedMethodMembers, methodMembers, fieldMembers);
    }

    @Override
    public MappedRange addMappedRange(
        Range minifiedRange,
        MemberNaming.MethodSignature originalSignature,
        Range originalRange,
        String renamedName) {
      return null;
    }

    @Override
    public void addMappingInformation(
        MappingInformation info, Consumer<MappingInformation> onProhibitedAddition) {
      // Intentionally empty.
    }

    @Override
    public boolean hasNoOverlappingRangesForSignature(MethodSignature residualSignature) {
      return true;
    }
  }

  static Builder builder(
      String renamedName, String originalName, Position position, Reporter reporter) {
    return new Builder(renamedName, originalName, position, reporter);
  }

  private final String originalName;
  final String renamedName;
  final Position position;

  private final ImmutableMap<MethodSignature, List<MemberNaming>> qualifiedMethodMembers;
  private final ImmutableMap<MethodSignature, MemberNaming> methodMembers;
  private final ImmutableMap<FieldSignature, MemberNaming> fieldMembers;

  // Constructor to help chaining {@link ClassNamingForMapApplier} according to class hierarchy.
  ClassNamingForMapApplier(ClassNamingForMapApplier proxy) {
    this(
        proxy.renamedName,
        proxy.originalName,
        proxy.position,
        proxy.qualifiedMethodMembers,
        proxy.methodMembers,
        proxy.fieldMembers);
  }

  private ClassNamingForMapApplier(
      String renamedName,
      String originalName,
      Position position,
      Map<MethodSignature, List<MemberNaming>> qualifiedMethodMembers,
      Map<MethodSignature, MemberNaming> methodMembers,
      Map<FieldSignature, MemberNaming> fieldMembers) {
    this.renamedName = renamedName;
    this.originalName = originalName;
    this.position = position;
    this.qualifiedMethodMembers = ImmutableMap.copyOf(qualifiedMethodMembers);
    this.methodMembers = ImmutableMap.copyOf(methodMembers);
    this.fieldMembers = ImmutableMap.copyOf(fieldMembers);
  }

  public ImmutableMap<MethodSignature, List<MemberNaming>> getQualifiedMethodMembers() {
    return qualifiedMethodMembers;
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
  public <T extends Throwable> void forAllFieldNamingSorted(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    for (MemberNaming naming : CollectionUtils.sort(fieldMembers.values())) {
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

  @Override
  public <T extends Throwable> void forAllMethodNamingSorted(
      ThrowingConsumer<MemberNaming, T> consumer) throws T {
    for (MemberNaming naming : CollectionUtils.sort(methodMembers.values())) {
      consumer.accept(naming);
    }
  }

  @Override
  public MemberNaming lookup(Signature renamedSignature) {
    // As the key is inverted, this looks a lot like
    //   {@link ClassNamingForNameMapper#lookupByOriginalSignature}.
    Collection<MemberNaming> memberNamings =
        renamedSignature.kind() == SignatureKind.METHOD
            ? methodMembers.values()
            : fieldMembers.values();
    for (MemberNaming memberNaming : memberNamings) {
      if (memberNaming.getResidualSignature().equals(renamedSignature)) {
        return memberNaming;
      }
    }
    return null;
  }

  @Override
  public MemberNaming lookupByOriginalSignature(Signature original) {
    // As the key is inverted, this looks a lot like {@link ClassNamingForNameMapper#lookup}.
    if (original.kind() == SignatureKind.METHOD) {
      return methodMembers.get(original.asMethodSignature());
    } else {
      assert original.kind() == SignatureKind.FIELD;
      return fieldMembers.get(original.asFieldSignature());
    }
  }

  MemberNaming lookupByOriginalItem(DexField field) {
    for (Map.Entry<FieldSignature, MemberNaming> entry : fieldMembers.entrySet()) {
      FieldSignature signature = entry.getKey();
      if (signature.name.equals(field.name.toSourceString())
          && signature.type.equals(field.type.toSourceString())) {
        return entry.getValue();
      }
    }
    return null;
  }

  protected MemberNaming lookupByOriginalItem(DexMethod method) {
    for (Map.Entry<MethodSignature, MemberNaming> entry : methodMembers.entrySet()) {
      MethodSignature signature = entry.getKey();
      if (signature.name.equals(method.name.toSourceString())
          && signature.type.equals(method.proto.returnType.toSourceString())
          && Arrays.equals(signature.parameters,
              Arrays.stream(method.proto.parameters.values)
                  .map(DexType::toSourceString).toArray(String[]::new))) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ClassNamingForMapApplier)) {
      return false;
    }

    ClassNamingForMapApplier that = (ClassNamingForMapApplier) o;

    return originalName.equals(that.originalName)
        && renamedName.equals(that.renamedName)
        && qualifiedMethodMembers.equals(that.qualifiedMethodMembers)
        && methodMembers.equals(that.methodMembers)
        && fieldMembers.equals(that.fieldMembers);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(
        originalName, renamedName, qualifiedMethodMembers, methodMembers, fieldMembers);
  }
}
