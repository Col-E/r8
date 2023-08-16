// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.MappedRangeUtils.isInlineMappedRangeForComposition;
import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineCallsiteMappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.ThrowsCondition;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SegmentTree;
import com.android.tools.r8.utils.ThrowingBiFunction;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

public class ComposingBuilder {

  private static final Range EMPTY_RANGE = new Range(0, 0);

  private MapVersionMappingInformation currentMapVersion = null;

  /**
   * When composing we store a view of the previously known mappings in committed and retain a
   * current working set. When composing of a new map is finished we commit everything in current
   * into the committed set.
   *
   * <p>The reason for not having just a single set is that we can have a circular mapping as
   * follows:
   *
   * <pre>
   *   a -> b:
   *   ...
   *   b -> a:
   * </pre>
   *
   * After composing our current view of a with the above, we could end up transforming 'a' into 'b'
   * and then later transforming 'b' back into 'a' again. To ensure we do not mess up namings while
   * composing classes and methods we resort to a working set and committed set.
   */
  private final ComposingData committed = new ComposingData();

  private ComposingData current;
  private final InternalOptions options;

  public ComposingBuilder(InternalOptions options) {
    this.options = options;
  }

  public void compose(ClassNameMapper classNameMapper) throws MappingComposeException {
    current = new ComposingData();
    MapVersionMappingInformation newMapVersionInfo =
        classNameMapper.getFirstMapVersionInformation();
    if (newMapVersionInfo == null) {
      throw new MappingComposeException(
          "Composition of mapping files supported from map version 2.2.");
    }
    MapVersion newMapVersion = newMapVersionInfo.getMapVersion();
    if (!ResidualSignatureMappingInformation.isSupported(newMapVersion)
        || newMapVersion.isUnknown()) {
      throw new MappingComposeException(
          "Composition of mapping files supported from map version "
              + ResidualSignatureMappingInformation.SUPPORTED_VERSION.getName()
              + ".");
    }
    if (currentMapVersion == null) {
      currentMapVersion = newMapVersionInfo;
    } else {
      currentMapVersion =
          newMapVersionInfo.compose(currentMapVersion).asMapVersionMappingInformation();
    }
    for (ClassNamingForNameMapper classMapping : classNameMapper.getClassNameMappings().values()) {
      compose(classNameMapper, classMapping);
    }
    committed.commit(current, classNameMapper);
  }

  private void compose(ClassNameMapper classNameMapper, ClassNamingForNameMapper classMapping)
      throws MappingComposeException {
    String originalName = classMapping.originalName;
    String renamedName = classMapping.renamedName;
    ComposingClassBuilder composingClassBuilder =
        new ComposingClassBuilder(originalName, renamedName, committed, current, options);
    ComposingClassBuilder duplicateMapping =
        current.classBuilders.put(renamedName, composingClassBuilder);
    if (duplicateMapping != null) {
      throw new MappingComposeException(
          "Duplicate class mapping. Both '"
              + duplicateMapping.getOriginalName()
              + "' and '"
              + originalName
              + "' maps to '"
              + renamedName
              + "'.");
    }
    composingClassBuilder.compose(classNameMapper, classMapping);
  }

  public String finish() {
    List<ComposingClassBuilder> classBuilders = new ArrayList<>(committed.classBuilders.values());
    classBuilders.sort(Comparator.comparing(ComposingClassBuilder::getOriginalName));
    StringBuilder sb = new StringBuilder();
    committed.preamble.forEach(preambleLine -> sb.append(preambleLine).append("\n"));
    if (currentMapVersion != null) {
      sb.append("# ").append(currentMapVersion.serialize()).append("\n");
    }
    ChainableStringConsumer wrap = ChainableStringConsumer.wrap(sb::append);
    for (ComposingClassBuilder classBuilder : classBuilders) {
      classBuilder.write(wrap);
    }
    return sb.toString();
  }

  public static class ComposingData {

    /**
     * A map of minified names to their class builders. When committing to a new minified name we
     * destructively remove the previous minified mapping and replace it with the up-to-date one.
     */
    private Map<String, ComposingClassBuilder> classBuilders = new HashMap<>();
    /**
     * RewriteFrameInformation contains condition clauses that are bound to the residual program. As
     * a result of that, we have to patch up the conditions when we compose new class mappings.
     */
    private final List<RewriteFrameMappingInformation> rewriteFrameInformation = new ArrayList<>();
    /** Map of newly added outline call site informations which do not require any rewriting. */
    private Map<ClassTypeNameAndMethodName, OutlineCallsiteMappingInformation>
        outlineCallsiteInformation = new HashMap<>();
    /**
     * Map of updated outline definitions which has to be committed. The positions in the caller are
     * fixed at this point since these are local to the method when rewriting.
     */
    private final Map<ClassTypeNameAndMethodName, UpdateOutlineCallsiteInformation>
        outlineSourcePositionsUpdated = new HashMap<>();

    /**
     * Map of signatures that should be removed when finalizing the composed map. The key is the
     * original name of a class.
     */
    private final Map<String, Set<Signature>> signaturesToRemove = new HashMap<>();

    private final List<String> preamble = new ArrayList<>();

    public void commit(ComposingData current, ClassNameMapper classNameMapper)
        throws MappingComposeException {
      preamble.addAll(classNameMapper.getPreamble());
      commitClassBuilders(current, classNameMapper);
      commitRewriteFrameInformation(current, classNameMapper);
      commitOutlineCallsiteInformation(current, classNameMapper);
    }

    private void commitClassBuilders(ComposingData current, ClassNameMapper classNameMapper)
        throws MappingComposeException {
      Set<String> updatedClassBuilders = new HashSet<>();
      Map<String, ComposingClassBuilder> newClassBuilders = new HashMap<>();
      for (Entry<String, ComposingClassBuilder> newEntry : current.classBuilders.entrySet()) {
        String renamedName = newEntry.getKey();
        ComposingClassBuilder classBuilder = newEntry.getValue();
        updatedClassBuilders.add(classBuilder.originalName);
        ComposingClassBuilder existingBuilder = classBuilders.get(classBuilder.originalName);
        if (existingBuilder != null) {
          removeSignaturesFromBuilder(current, existingBuilder);
          classBuilder = existingBuilder.commit(classBuilder);
        }
        newClassBuilders.put(renamedName, classBuilder);
      }
      for (Entry<String, ComposingClassBuilder> existingEntry : classBuilders.entrySet()) {
        if (!updatedClassBuilders.contains(existingEntry.getKey())) {
          ComposingClassBuilder classBuilder = existingEntry.getValue();
          removeSignaturesFromBuilder(current, classBuilder);
          ComposingClassBuilder duplicateMapping =
              newClassBuilders.put(existingEntry.getKey(), classBuilder);
          if (duplicateMapping != null) {
            throw new MappingComposeException(
                "Duplicate class mapping. Both '"
                    + classBuilder.getOriginalName()
                    + "' and '"
                    + classNameMapper.getClassNaming(existingEntry.getKey()).originalName
                    + "' maps to '"
                    + classBuilder.renamedName
                    + "'.");
          }
        }
      }
      classBuilders = newClassBuilders;
    }

    private void removeSignaturesFromBuilder(
        ComposingData current, ComposingClassBuilder classBuilder) {
      Set<Signature> signaturesToRemove =
          current.signaturesToRemove.get(classBuilder.getOriginalName());
      if (signaturesToRemove == null) {
        return;
      }
      signaturesToRemove.forEach(
          signatureToRemove -> {
            if (signatureToRemove.isFieldSignature()) {
              classBuilder.fieldMembers.remove(signatureToRemove.asFieldSignature());
            } else {
              classBuilder.methodsWithoutPosition.remove(signatureToRemove.asMethodSignature());
              classBuilder.methodsWithPosition.remove(signatureToRemove.asMethodSignature());
            }
          });
    }

    public void addSignatureToRemove(
        ComposingClassBuilder composingClassBuilder, Signature signature) {
      signaturesToRemove
          .computeIfAbsent(
              composingClassBuilder.getOriginalName(), ignoreArgument(Sets::newHashSet))
          .add(signature);
    }

    private void commitRewriteFrameInformation(
        ComposingData current, ClassNameMapper classNameMapper) {
      // First update the existing frame information to have new class name mappings.
      Map<String, String> inverse = classNameMapper.getObfuscatedToOriginalMapping().inverse;
      for (RewriteFrameMappingInformation rewriteMappingInfo : rewriteFrameInformation) {
        rewriteMappingInfo
            .getConditions()
            .forEach(
                rewriteCondition -> {
                  ThrowsCondition throwsCondition = rewriteCondition.asThrowsCondition();
                  if (throwsCondition != null) {
                    throwsCondition.setClassReferenceInternal(
                        mapTypeReference(inverse, throwsCondition.getClassReference()).asClass());
                  }
                });
      }
      rewriteFrameInformation.addAll(current.rewriteFrameInformation);
    }

    private void commitOutlineCallsiteInformation(
        ComposingData current, ClassNameMapper classNameMapper) {
      // To commit outline call site information, we take the previously committed and bring forward
      // to a new mapping, and potentially rewrite source positions if available.
      Map<ClassTypeNameAndMethodName, OutlineCallsiteMappingInformation> newOutlineCallsiteInfo =
          new HashMap<>();
      Map<String, String> inverse = classNameMapper.getObfuscatedToOriginalMapping().inverse;
      outlineCallsiteInformation.forEach(
          (holderAndMethodNameOfOutline, outlineInfo) -> {
            UpdateOutlineCallsiteInformation updateOutlineCallsiteInformation =
                current.outlineSourcePositionsUpdated.get(holderAndMethodNameOfOutline);
            String newMethodName = outlineInfo.getOutline().getMethodName();
            if (updateOutlineCallsiteInformation != null) {
              // We have a callsite mapping that we need to update.
              MappedRangeOriginalToMinifiedMap originalToMinifiedMap =
                  MappedRangeOriginalToMinifiedMap.build(
                      updateOutlineCallsiteInformation.newMappedRanges);
              Int2IntSortedMap newPositionMap = new Int2IntLinkedOpenHashMap();
              outlineInfo
                  .getPositions()
                  .forEach(
                      (originalPosition, destination) ->
                          originalToMinifiedMap.visitMinified(
                              originalPosition,
                              newMinified -> newPositionMap.put(newMinified, destination)));
              outlineInfo.setPositionsInternal(newPositionMap);
              newMethodName = updateOutlineCallsiteInformation.newMethodName;
            }
            // Holder, return type or formals could have changed the outline descriptor.
            MethodReference outline = outlineInfo.getOutline();
            ClassReference newHolder =
                mapTypeReference(inverse, outline.getHolderClass()).asClass();
            outlineInfo.setOutlineInternal(
                Reference.method(
                    newHolder,
                    newMethodName,
                    mapTypeReferences(inverse, outline.getFormalTypes()),
                    mapTypeReference(inverse, outline.getReturnType())));
            newOutlineCallsiteInfo.put(
                new ClassTypeNameAndMethodName(
                    newHolder.getTypeName(), holderAndMethodNameOfOutline.getMethodName()),
                outlineInfo);
          });
      newOutlineCallsiteInfo.putAll(current.outlineCallsiteInformation);
      outlineCallsiteInformation = newOutlineCallsiteInfo;
    }

    public void addNewOutlineCallsiteInformation(
        MethodReference outline, OutlineCallsiteMappingInformation outlineCallsiteInfo) {
      outlineCallsiteInformation.put(
          new ClassTypeNameAndMethodName(
              outline.getHolderClass().getTypeName(), outline.getMethodName()),
          outlineCallsiteInfo);
    }

    public UpdateOutlineCallsiteInformation getUpdateOutlineCallsiteInformation(
        String originalHolder, String originalMethodName, String newMethodName) {
      return outlineSourcePositionsUpdated.computeIfAbsent(
          new ClassTypeNameAndMethodName(originalHolder, originalMethodName),
          ignore -> new UpdateOutlineCallsiteInformation(newMethodName));
    }

    private List<TypeReference> mapTypeReferences(
        Map<String, String> typeNameMap, List<TypeReference> typeReferences) {
      return ListUtils.map(typeReferences, typeRef -> mapTypeReference(typeNameMap, typeRef));
    }

    private TypeReference mapTypeReference(
        Map<String, String> typeNameMap, TypeReference typeReference) {
      if (typeReference == null || typeReference.isPrimitive()) {
        return typeReference;
      }
      if (typeReference.isArray()) {
        ArrayReference arrayReference = typeReference.asArray();
        return Reference.array(
            mapTypeReference(typeNameMap, arrayReference.getBaseType()),
            arrayReference.getDimensions());
      } else {
        assert typeReference.isClass();
        String newTypeName = typeNameMap.get(typeReference.getTypeName());
        return newTypeName == null ? typeReference : Reference.classFromTypeName(newTypeName);
      }
    }
  }

  private static class ClassTypeNameAndMethodName {

    private final String holderTypeName;
    private final String methodName;

    public ClassTypeNameAndMethodName(String holderTypeName, String methodName) {
      this.holderTypeName = holderTypeName;
      this.methodName = methodName;
    }

    public String getMethodName() {
      return methodName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ClassTypeNameAndMethodName)) {
        return false;
      }
      ClassTypeNameAndMethodName that = (ClassTypeNameAndMethodName) o;
      return holderTypeName.equals(that.holderTypeName) && methodName.equals(that.methodName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(holderTypeName, methodName);
    }
  }

  private static class UpdateOutlineCallsiteInformation {

    private List<MappedRange> newMappedRanges;
    private final String newMethodName;

    private UpdateOutlineCallsiteInformation(String newMethodName) {
      this.newMethodName = newMethodName;
    }

    private void setNewMappedRanges(List<MappedRange> mappedRanges) {
      newMappedRanges = mappedRanges;
    }
  }

  private static class MappedRangeOriginalToMinifiedMap {

    private final Int2ReferenceMap<List<Integer>> originalToMinified;

    private MappedRangeOriginalToMinifiedMap(Int2ReferenceMap<List<Integer>> originalToMinified) {
      this.originalToMinified = originalToMinified;
    }

    private static MappedRangeOriginalToMinifiedMap build(List<MappedRange> mappedRanges) {
      Int2ReferenceMap<List<Integer>> positionMap = new Int2ReferenceOpenHashMap<>();
      for (MappedRange mappedRange : mappedRanges) {
        Range originalRange = mappedRange.getOriginalRangeOrIdentity();
        for (int position = originalRange.from; position <= originalRange.to; position++) {
          // It is perfectly fine to have multiple minified ranges mapping to the same source, we
          // just need to keep the additional information.
          positionMap
              .computeIfAbsent(position, ignoreArgument(ArrayList::new))
              .add(mappedRange.minifiedRange.from + (position - originalRange.from));
        }
      }
      return new MappedRangeOriginalToMinifiedMap(positionMap);
    }

    public void visitMinified(int originalPosition, Consumer<Integer> consumer) {
      List<Integer> minifiedPositions = originalToMinified.get(originalPosition);
      if (minifiedPositions != null) {
        minifiedPositions.forEach(consumer);
      }
    }
  }

  public static class ComposingClassBuilder {

    private static final String INDENTATION = "    ";
    private static final int NO_RANGE_FROM = -1;

    private final String originalName;
    private final String renamedName;
    private final Map<FieldSignature, MemberNaming> fieldMembers = new HashMap<>();
    private final Map<MethodSignature, SegmentTree<List<MappedRange>>> methodsWithPosition =
        new HashMap<>();
    private final Map<MethodSignature, MappedRange> methodsWithoutPosition = new HashMap<>();
    private final List<MappingInformation> additionalMappingInfo = new ArrayList<>();

    private final ComposingData committed;
    private final ComposingData current;

    private final Map<String, ComposingClassBuilder> committedPreviousClassBuilders;
    private final ComposingClassBuilder committedPreviousClassBuilder;
    private final InternalOptions options;

    private ComposingClassBuilder(
        String originalName,
        String renamedName,
        ComposingData committed,
        ComposingData current,
        InternalOptions options) {
      this.originalName = originalName;
      this.renamedName = renamedName;
      this.current = current;
      this.committed = committed;
      this.options = options;
      committedPreviousClassBuilders = committed.classBuilders;
      committedPreviousClassBuilder = committedPreviousClassBuilders.get(originalName);
    }

    public String getOriginalName() {
      return originalName;
    }

    public String getRenamedName() {
      return renamedName;
    }

    public void compose(ClassNameMapper classNameMapper, ClassNamingForNameMapper mapper)
        throws MappingComposeException {
      List<MappingInformation> newMappingInfo = mapper.getAdditionalMappingInfo();
      if (newMappingInfo != null) {
        additionalMappingInfo.addAll(newMappingInfo);
      }
      composeFieldNamings(mapper, classNameMapper);
      composeMethodNamings(mapper, classNameMapper);
    }

    private void composeFieldNamings(
        ClassNamingForNameMapper mapper, ClassNameMapper classNameMapper) {
      Map<String, String> inverseClassMapping =
          classNameMapper.getObfuscatedToOriginalMapping().inverse;
      mapper.forAllFieldNaming(
          fieldNaming -> {
            MemberNaming fieldNamingToAdd = fieldNaming;
            FieldSignature originalSignature =
                fieldNaming.getOriginalSignature().asFieldSignature();
            FieldSignature residualSignature =
                fieldNaming
                    .computeResidualSignature(type -> inverseClassMapping.getOrDefault(type, type))
                    .asFieldSignature();
            MemberNaming existingMemberNaming = getExistingMemberNaming(originalSignature);
            if (existingMemberNaming != null) {
              Signature existingOriginalSignature = existingMemberNaming.getOriginalSignature();
              if (!existingOriginalSignature.isQualified() && originalSignature.isQualified()) {
                String previousCommittedClassName =
                    getPreviousCommittedClassName(originalSignature.toHolderFromQualified());
                if (previousCommittedClassName != null) {
                  existingOriginalSignature =
                      existingOriginalSignature.toQualifiedSignature(previousCommittedClassName);
                }
              }
              fieldNamingToAdd = new MemberNaming(existingOriginalSignature, residualSignature);
            }
            MemberNaming existing = fieldMembers.put(residualSignature, fieldNamingToAdd);
            assert existing == null;
          });
    }

    private String getPreviousCommittedClassName(String holder) {
      ComposingClassBuilder composingClassBuilder = committedPreviousClassBuilders.get(holder);
      return composingClassBuilder == null ? null : composingClassBuilder.getOriginalName();
    }

    private MemberNaming getExistingMemberNaming(FieldSignature originalSignature) {
      ComposingClassBuilder composingClassBuilder =
          originalSignature.isQualified()
              ? committedPreviousClassBuilders.get(originalSignature.toHolderFromQualified())
              : committedPreviousClassBuilder;
      if (composingClassBuilder == null) {
        return null;
      }
      FieldSignature signature =
          (originalSignature.isQualified()
                  ? originalSignature.toUnqualifiedSignature()
                  : originalSignature)
              .asFieldSignature();
      current.addSignatureToRemove(composingClassBuilder, signature);
      return composingClassBuilder.fieldMembers.get(signature);
    }

    private List<MappedRange> getExistingMappedRanges(MappedRange newMappedRange)
        throws MappingComposeException {
      MethodSignature signature = newMappedRange.getOriginalSignature().toUnqualifiedIfQualified();
      SegmentTree<List<MappedRange>> listSegmentTree = methodsWithPosition.get(signature);
      // If there are no previously mapped positions for the range check methods without position.
      if (listSegmentTree == null) {
        MappedRange existingMappedRange = methodsWithoutPosition.get(signature);
        return existingMappedRange == null ? null : Collections.singletonList(existingMappedRange);
      }
      // Any new transformation can be lossy - for example, D8 only promises to keep line
      // positions if the method is not throwing (and is not debug). Additionally, R8/PG
      // emits `1:1:void foo() -> a` instead of `1:1:void foo():1:1 -> a`, so R8 must
      // capture the preamble position by explicitly inserting 0 as original range.
      int firstPositionOfOriginalRange =
          newMappedRange.getFirstPositionOfOriginalRange(NO_RANGE_FROM);
      Entry<Integer, List<MappedRange>> firstPositionEntries =
          listSegmentTree.findEntry(firstPositionOfOriginalRange);
      if (firstPositionEntries != null) {
        return firstPositionEntries.getValue();
      }
      // If the first position is 0 we can try at find the existing mapped ranges by last
      // position.
      if (firstPositionOfOriginalRange == 0 && !newMappedRange.isOriginalRangePreamble()) {
        Entry<Integer, List<MappedRange>> lastPositionEntries =
            listSegmentTree.findEntry(newMappedRange.getLastPositionOfOriginalRange());
        if (lastPositionEntries != null) {
          return lastPositionEntries.getValue();
        }
      }
      // We assume that all new minified ranges for a method are rewritten in the new map
      // such that no previous existing positions exists.
      // The original can be discarded if it no longer exists or if the method is
      // non-throwing.
      if (!newMappedRange.isOriginalRangePreamble()
          && !options.mappingComposeOptions().allowNonExistingOriginalRanges) {
        throw new MappingComposeException(
            "Could not find original starting position of '"
                + newMappedRange.minifiedRange.from
                + "' which should be "
                + firstPositionOfOriginalRange);
      }
      return null;
    }

    private void composeMethodNamings(
        ClassNamingForNameMapper mapper, ClassNameMapper classNameMapper)
        throws MappingComposeException {
      Map<String, String> inverseClassMapping =
          classNameMapper.getObfuscatedToOriginalMapping().inverse;
      for (Entry<String, MappedRangesOfName> entry : mapper.mappedRangesByRenamedName.entrySet()) {
        List<MappedRangesOfName> mappedRangesOfNames =
            entry.getValue().partitionOnMethodSignature();
        for (MappedRangesOfName rangesOfName : mappedRangesOfNames) {
          List<MappedRange> newRangesToCompose = rangesOfName.getMappedRanges();
          RangeBuilder minified = new RangeBuilder();
          // The new minified ranges may have ranges that range over positions that has additional
          // information, such as inlinees:
          // Existing:
          //  1:2:void caller():14:15 -> x
          //  3:3:void inlinee():42:42 -> x
          //  3:3:void caller():16:16 -> x
          //  4:10:void caller():17:23 -> x
          //  ...
          // New mapping:
          //  1:5:void x():1:5 -> y
          //  6:10:void x():6:6 -> y
          //  ...
          // It is important that we therefore split up the new ranges to map everything back to the
          // existing original mappings:
          // Resulting mapping:
          //  1:2:void caller():14:15 -> y
          //  3:3:void inlinee():42:42 -> y
          //  3:3:void caller():16:16 -> y
          //  4:5:void caller():17:18 -> y
          //  6:10:void caller():19:19 -> y
          //  ...
          List<MappedRange> composedRanges = new ArrayList<>();
          ComputedOutlineInformation computedOutlineInformation = new ComputedOutlineInformation();
          List<MappedRange> composedInlineFrames = new ArrayList<>();
          for (int i = 0; i < newRangesToCompose.size(); i++) {
            MappedRange mappedRange = newRangesToCompose.get(i);
            minified.addRange(mappedRange.minifiedRange);
            // Register mapping information that is dependent on the residual naming to allow
            // updating later on.
            registerMappingInformationFromMappedRanges(mappedRange);

            MethodSignature originalSignature =
                mappedRange.getOriginalSignature().asMethodSignature();

            List<MappedRange> existingMappedRanges = null;
            ComposingClassBuilder existingClassBuilder = getExistingClassBuilder(originalSignature);
            if (existingClassBuilder != null) {
              MethodSignature signature =
                  (originalSignature.isQualified()
                          ? originalSignature.toUnqualifiedSignature()
                          : originalSignature)
                      .asMethodSignature();
              // Remove the existing entry since we are now composing the signature.
              current.addSignatureToRemove(existingClassBuilder, signature);
              existingMappedRanges = existingClassBuilder.getExistingMappedRanges(mappedRange);
              assert existingMappedRanges != null
                  || minified.hasValue()
                  || mappedRange.getOriginalRangeOrIdentity() == null;
            }
            // Now that we have obtained a potential existing mapping, we can compose the frames.
            List<MappedRange> newComposedRange =
                composeMappedRangesForMethod(
                    existingClassBuilder,
                    existingMappedRanges,
                    mappedRange,
                    computedOutlineInformation,
                    !composedInlineFrames.isEmpty());
            composedInlineFrames = composeInlineFrames(newComposedRange, composedInlineFrames);
            if (!isInlineMappedRangeForComposition(newRangesToCompose, i)) {
              composedRanges.addAll(composedInlineFrames);
              composedInlineFrames = Collections.emptyList();
            }
          }
          composedRanges =
              fixupOutlines(computedOutlineInformation, newRangesToCompose, composedRanges);
          MethodSignature residualSignature =
              rangesOfName
                  .getMemberNaming(mapper)
                  .computeResidualSignature(type -> inverseClassMapping.getOrDefault(type, type))
                  .asMethodSignature();
          if (ListUtils.last(composedRanges).minifiedRange != null) {
            SegmentTree<List<MappedRange>> listSegmentTree =
                methodsWithPosition.computeIfAbsent(
                    residualSignature, ignored -> new SegmentTree<>(false));
            listSegmentTree.add(
                minified.getStartOrNoRangeFrom(), minified.getEndOrNoRangeFrom(), composedRanges);
          } else {
            assert composedRanges.size() == 1;
            methodsWithoutPosition.put(residualSignature, ListUtils.last(composedRanges));
          }
        }
      }
    }

    private List<MappedRange> fixupOutlines(
        ComputedOutlineInformation computedOutlineInformation,
        List<MappedRange> newRangesToBeComposed,
        List<MappedRange> composedRanges)
        throws MappingComposeException {
      composedRanges = fixupInlinedOutlines(computedOutlineInformation, composedRanges);
      fixupOutlineInformation(computedOutlineInformation, newRangesToBeComposed, composedRanges);
      fixupOutlineCallsiteInformation(computedOutlineInformation, composedRanges);
      return composedRanges;
    }

    private List<MappedRange> fixupInlinedOutlines(
        ComputedOutlineInformation computedOutlineInformation, List<MappedRange> composedRanges)
        throws MappingComposeException {
      // Check if we could have inlined an outline which is true if we see both an outline and call
      // site to patch up. When an outline has been inlined we have to retrace through the inlined
      // positions, by positions only existing in the previous mapped ranges, to form new ranges.
      if (computedOutlineInformation.seenOutlineMappingInformation.isEmpty()
          || computedOutlineInformation.outlineCallsiteMappingInformationToPatchUp.isEmpty()) {
        return composedRanges;
      }
      Set<OutlineCallsiteMappingInformation> outlineCallSitesToRemove = Sets.newIdentityHashSet();
      Set<OutlineMappingInformation> outlinesToRemove = Sets.newIdentityHashSet();
      // We patch up all ranges from top to bottom with the invariant that all at a given
      // index, all above have been updated correctly. We will do expansion of frames
      // when separating out single minified lines, but we keep the outline information
      // present such that we can fix them when seeing them later.
      int composedRangeIndex = 0;
      while (composedRangeIndex < composedRanges.size() - 1) {
        MappedRange outline = composedRanges.get(composedRangeIndex++);
        MappedRange outlineCallSite = composedRanges.get(composedRangeIndex);
        if (outline.isOutlineFrame()
            && isInlineMappedRangeForComposition(outline, outlineCallSite)) {
          // We should replace the inlined outline frame positions with the synthesized
          // positions from the outline call site.
          if (outlineCallSite.getOutlineCallsiteInformation().size() != 1) {
            // If we have an inlined outline it must be such that the outer frame is an
            // outline callsite.
            throw new MappingComposeException(
                "Expected exactly one outline call site for a mapped range with signature '"
                    + outlineCallSite.getOriginalSignature()
                    + "'.");
          }
          OutlineCallsiteMappingInformation outlineCallSiteInformation =
              outlineCallSite.getOutlineCallsiteInformation().get(0);
          // The original positions in the outline callsite have been composed, so we have to
          // find the existing mapped range and iterate the original positions for that range.
          ComputedMappedRangeForOutline computedInformationForCallSite =
              computedOutlineInformation.getComputedRange(
                  outlineCallSiteInformation, outlineCallSite);
          if (computedInformationForCallSite == null) {
            continue;
          }
          Map<Integer, List<MappedRange>> mappedRangesForOutline =
              new HashMap<>(outlineCallSiteInformation.getPositions().size());
          visitOutlineMappedPositions(
              outlineCallSiteInformation,
              computedInformationForCallSite.current.getOriginalSignature(),
              positionInfo ->
                  mappedRangesForOutline.put(
                      positionInfo.outlinePosition(), positionInfo.mappedRanges()));
          List<MappedRange> newComposedRanges = new ArrayList<>();
          // Copy all previous handled mapped ranges into a new list.
          for (MappedRange previousMappedRanges : composedRanges) {
            if (previousMappedRanges == outline) {
              break;
            }
            newComposedRanges.add(previousMappedRanges);
          }
          // The original positions in the outline have been composed, so we have to find the
          // existing mapped range and iterate the original positions for that range.
          ComputedMappedRangeForOutline computedInformationForOutline =
              computedOutlineInformation.getComputedRange(
                  outline.getOutlineMappingInformation(), outline);
          if (computedInformationForOutline == null) {
            continue;
          }
          // The outline could have additional inlined positions in it, but we should be
          // guaranteed to find call site information on all original line numbers. We
          // therefore iterate one by one and amend the subsequent outer frames as well.
          MappedRange current = computedInformationForOutline.current;
          int minifiedLine = outline.minifiedRange.from;
          for (int originalLine = current.getOriginalRangeOrIdentity().from;
              originalLine <= current.getOriginalRangeOrIdentity().to;
              originalLine++) {
            // If the outline is itself an inline frame it is bound to only have one original
            // position and we can simply insert all inline frames on that position with the
            // existing minified range.
            Range newMinifiedRange =
                outline.originalRange.isCardinal
                    ? outline.minifiedRange
                    : new Range(minifiedLine, minifiedLine);
            List<MappedRange> outlineMappedRanges = mappedRangesForOutline.get(originalLine);
            if (outlineMappedRanges != null) {
              outlineMappedRanges.forEach(
                  range -> {
                    if (range != ListUtils.last(outlineMappedRanges)) {
                      newComposedRanges.add(
                          new MappedRange(
                              newMinifiedRange,
                              range.getOriginalSignature().asMethodSignature(),
                              range.originalRange,
                              outlineCallSite.getRenamedName()));
                    }
                  });
              newComposedRanges.add(
                  new MappedRange(
                      newMinifiedRange,
                      outlineCallSite.getOriginalSignature().asMethodSignature(),
                      ListUtils.last(outlineMappedRanges).originalRange,
                      outlineCallSite.getRenamedName()));
            }
            for (int tailInlineFrameIndex = composedRangeIndex + 1;
                tailInlineFrameIndex < composedRanges.size();
                tailInlineFrameIndex++) {
              MappedRange originalMappedRange = composedRanges.get(tailInlineFrameIndex);
              if (!originalMappedRange.minifiedRange.equals(outlineCallSite.minifiedRange)) {
                break;
              }
              MappedRange newMappedRange = originalMappedRange.withMinifiedRange(newMinifiedRange);
              newMappedRange.setAdditionalMappingInformationInternal(
                  originalMappedRange.getAdditionalMappingInformation());
              newComposedRanges.add(newMappedRange);
            }
            minifiedLine++;
          }
          // We have patched up the the inlined outline and all subsequent inline frames
          // (although some of the subsequent frames above could also be inlined outlines). We
          // therefore need to copy the remaining frames.
          boolean seenMinifiedRange = false;
          for (MappedRange range : composedRanges) {
            if (range.minifiedRange.equals(outline.minifiedRange)) {
              seenMinifiedRange = true;
            } else if (seenMinifiedRange) {
              newComposedRanges.add(range);
            }
          }
          composedRanges = newComposedRanges;
          outlineCallSitesToRemove.add(outlineCallSiteInformation);
          outlinesToRemove.add(outline.getOutlineMappingInformation());
        }
      }
      // If we removed any outlines or call sites, remove the processing of them.
      outlineCallSitesToRemove.forEach(
          computedOutlineInformation.outlineCallsiteMappingInformationToPatchUp::remove);
      outlinesToRemove.forEach(computedOutlineInformation.seenOutlineMappingInformation::remove);
      return composedRanges;
    }

    private void fixupOutlineInformation(
        ComputedOutlineInformation computedOutlineInformation,
        List<MappedRange> newRangesToBeComposed,
        List<MappedRange> composedRanges) {
      if (computedOutlineInformation.seenOutlineMappingInformation.isEmpty()) {
        return;
      }
      MappedRange lastComposedRange = ListUtils.last(composedRanges);
      current
          .getUpdateOutlineCallsiteInformation(
              committedPreviousClassBuilder.getRenamedName(),
              ListUtils.last(newRangesToBeComposed).signature.getName(),
              lastComposedRange.getRenamedName())
          .setNewMappedRanges(newRangesToBeComposed);
    }

    private void fixupOutlineCallsiteInformation(
        ComputedOutlineInformation computedOutlineInformation,
        List<MappedRange> composedRanges)
        throws MappingComposeException {
      Map<OutlineCallsiteMappingInformation, List<ComputedMappedRangeForOutline>>
          outlineCallsitesToPatchUp =
              computedOutlineInformation.outlineCallsiteMappingInformationToPatchUp;
      if (outlineCallsitesToPatchUp.isEmpty()) {
        return;
      }
      MappedRange lastComposedRange = ListUtils.last(composedRanges);
      // Outline positions are synthetic positions, and they have no position in the residual
      // program. We therefore have to find the original positions and copy all inline frames
      // and amend the outermost frame with the residual signature and the next free position.
      List<OutlineCallsiteMappingInformation> outlineCallSites =
          ListUtils.sort(
              outlineCallsitesToPatchUp.keySet(),
              Comparator.comparing(mapping -> mapping.getOutline().toString()));
      IntBox firstAvailableRange = new IntBox(lastComposedRange.minifiedRange.to + 1);
      for (OutlineCallsiteMappingInformation outlineCallSite : outlineCallSites) {
        // It should be sufficient to patch any call-site because they are referencing the same
        // outline call-site information due to using an identity hashmap.
        List<ComputedMappedRangeForOutline> computedMappedRangeForOutlines =
            outlineCallsitesToPatchUp.get(outlineCallSite);
        assert verifyAllOutlineCallSitesAreEqualTo(outlineCallSite, computedMappedRangeForOutlines);
        ComputedMappedRangeForOutline computedMappedRangeForOutline =
            ListUtils.first(computedMappedRangeForOutlines);
        Int2IntSortedMap newPositionMap =
            new Int2IntLinkedOpenHashMap(outlineCallSite.getPositions().size());
        visitOutlineMappedPositions(
            outlineCallSite,
            computedMappedRangeForOutline.current.getOriginalSignature(),
            positionInfo -> {
              int newIndex = firstAvailableRange.getAndIncrement();
              Range newMinifiedRange = new Range(newIndex, newIndex);
              boolean isCaller = false;
              for (MappedRange existingMappedRange : positionInfo.mappedRanges()) {
                int originalPosition =
                    existingMappedRange.getOriginalLineNumber(
                        positionInfo.outlineCallsitePosition());
                Range newOriginalRange =
                    isCaller
                        ? new Range(originalPosition)
                        : new Range(originalPosition, originalPosition);
                MappedRange newMappedRange =
                    new MappedRange(
                        newMinifiedRange,
                        existingMappedRange.getOriginalSignature(),
                        newOriginalRange,
                        lastComposedRange.getRenamedName());
                if (!existingMappedRange.getAdditionalMappingInformation().isEmpty()) {
                  newMappedRange.setAdditionalMappingInformationInternal(
                      existingMappedRange.getAdditionalMappingInformation());
                }
                composedRanges.add(newMappedRange);
                isCaller = true;
              }
              newPositionMap.put(positionInfo.outlinePosition(), newIndex);
            });
        outlineCallSite.setPositionsInternal(newPositionMap);
      }
    }

    private boolean verifyAllOutlineCallSitesAreEqualTo(
        OutlineCallsiteMappingInformation outlineCallSite,
        List<ComputedMappedRangeForOutline> computedMappedRangeForOutlines) {
      for (ComputedMappedRangeForOutline computedMappedRangeForOutline :
          computedMappedRangeForOutlines) {
        for (OutlineCallsiteMappingInformation outlineCallsiteMappingInformation :
            computedMappedRangeForOutline.composed.getOutlineCallsiteInformation()) {
          assert outlineCallSite == outlineCallsiteMappingInformation;
        }
      }
      return true;
    }

    /**
     * The class contains a list of mapped ranges for a single outline position.
     *
     * <p>Outline positions is a key-value map of positions in the outline to positions in the
     * callsite. An instance of OutlineCallSitePositionWithMappedRanges contains the information
     * about a single position.
     */
    public interface OutlineCallSitePositionWithMappedRanges {
      int outlinePosition();

      int outlineCallsitePosition();

      List<MappedRange> mappedRanges();
    }

    private void visitOutlineMappedPositions(
        OutlineCallsiteMappingInformation outlineCallSite,
        MethodSignature originalSignature,
        Consumer<OutlineCallSitePositionWithMappedRanges> outlinePositionConsumer)
        throws MappingComposeException {
      Int2IntSortedMap positionMap = outlineCallSite.getPositions();
      ComposingClassBuilder existingClassBuilder = getExistingClassBuilder(originalSignature);
      if (existingClassBuilder == null) {
        throw new MappingComposeException(
            "Could not find builder with original signature '" + originalSignature + "'.");
      }
      SegmentTree<List<MappedRange>> outlineSegmentTree =
          existingClassBuilder.methodsWithPosition.get(
              originalSignature.toUnqualifiedSignatureIfQualified().asMethodSignature());
      if (outlineSegmentTree == null) {
        throw new MappingComposeException(
            "Could not find method positions for original signature '" + originalSignature + "'.");
      }
      for (Integer keyPosition : positionMap.keySet()) {
        int outlinePosition = keyPosition;
        int callsitePosition = positionMap.get(outlinePosition);
        List<MappedRange> mappedRanges = outlineSegmentTree.find(callsitePosition);
        if (mappedRanges == null) {
          throw new MappingComposeException(
              "Could not find ranges for outline position '"
                  + keyPosition
                  + "' with original signature '"
                  + originalSignature
                  + "'.");
        }
        ExistingMappings existingMappings = ExistingMappings.create(mappedRanges);
        List<MappedRange> mappedRangesForOutlinePosition =
            existingMappings.getPreviousRanges(callsitePosition);
        if (mappedRangesForOutlinePosition == null
            || mappedRangesForOutlinePosition.isEmpty()
            || !mappedRangesForOutlinePosition.get(0).minifiedRange.contains(callsitePosition)) {
          throw new MappingComposeException(
              "Could not find ranges for outline position '"
                  + keyPosition
                  + "' with original signature '"
                  + originalSignature
                  + "'.");
        }
        outlinePositionConsumer.accept(
            new OutlineCallSitePositionWithMappedRanges() {
              @Override
              public int outlinePosition() {
                return outlinePosition;
              }

              @Override
              public int outlineCallsitePosition() {
                return callsitePosition;
              }

              @Override
              public List<MappedRange> mappedRanges() {
                return mappedRangesForOutlinePosition;
              }
            });
      }
    }

    /**
     * Utility function to compose inline frames
     *
     * <p>Say that we have the following input:
     *
     * <pre>
     *   previousInlineFrames:
     *     10:10:void m1():10:10 -> w
     *     10:10:void y():30 -> w
     *     11:11:void m2(int):20:20 -> w
     *     11:11:void y():31 -> w
     *   newComposedRanges:
     *     10:11:void new_synthetic_method():0 -> w
     * </pre>
     *
     * The newComposedRanges are outer frames, but we cannot place them below since their range
     * overlap. We therefore need to place a copy of newComposedRanges into the correct position at
     * the minified range to produce:
     *
     * <pre>
     *   result:
     *     10:10:void m1():10 -> w
     *     10:10:void y():30:30 -> w
     *     10:10:void new_synthetic_method():0 -> w
     *     11:11:void m2(int):20 -> w
     *     11:11:void y():31:31 -> w
     *     11:11:void new_synthetic_method():0 -> w
     * </pre>
     */
    private List<MappedRange> composeInlineFrames(
        List<MappedRange> newComposedRanges, List<MappedRange> previousInlineFrames) {
      if (previousInlineFrames.isEmpty()) {
        return newComposedRanges;
      }
      List<MappedRange> newComposedInlineFrames = new ArrayList<>();
      Range lastMinifiedRange = previousInlineFrames.get(0).minifiedRange;
      for (MappedRange previousFrame : previousInlineFrames) {
        if (!lastMinifiedRange.equals(previousFrame.minifiedRange)) {
          for (MappedRange newComposedRange : newComposedRanges) {
            newComposedInlineFrames.add(
                newComposedRange.partitionOnMinifiedRange(lastMinifiedRange));
          }
          lastMinifiedRange = previousFrame.minifiedRange;
        }
        newComposedInlineFrames.add(previousFrame.partitionOnMinifiedRange(lastMinifiedRange));
      }
      for (MappedRange newComposedRange : newComposedRanges) {
        newComposedInlineFrames.add(newComposedRange.partitionOnMinifiedRange(lastMinifiedRange));
      }
      return newComposedInlineFrames;
    }

    private ComposingClassBuilder getExistingClassBuilder(MethodSignature originalSignature) {
      return originalSignature.isQualified()
          ? committedPreviousClassBuilders.get(originalSignature.toHolderFromQualified())
          : committedPreviousClassBuilder;
    }

    private void registerMappingInformationFromMappedRanges(MappedRange mappedRange)
        throws MappingComposeException {
      for (MappingInformation mappingInformation : mappedRange.getAdditionalMappingInformation()) {
        if (mappingInformation.isRewriteFrameMappingInformation()) {
          RewriteFrameMappingInformation rewriteFrameMappingInformation =
              mappingInformation.asRewriteFrameMappingInformation();
          rewriteFrameMappingInformation
              .getConditions()
              .forEach(
                  condition -> {
                    if (condition.isThrowsCondition()) {
                      current.rewriteFrameInformation.add(rewriteFrameMappingInformation);
                    }
                  });
        } else if (mappingInformation.isOutlineCallsiteInformation()) {
          OutlineCallsiteMappingInformation outlineCallsiteInfo =
              mappingInformation.asOutlineCallsiteInformation();
          MethodReference outline = outlineCallsiteInfo.getOutline();
          if (outline == null) {
            throw new MappingComposeException(
                "Unable to compose outline call site information without outline key: "
                    + outlineCallsiteInfo.serialize());
          }
          current.addNewOutlineCallsiteInformation(outline, outlineCallsiteInfo);
        }
      }
    }

    private List<MappedRange> composeMappedRangesForMethod(
        ComposingClassBuilder existingClassBuilder,
        List<MappedRange> existingRanges,
        MappedRange newRange,
        ComputedOutlineInformation computedOutlineInformation,
        boolean isInlineFrame)
        throws MappingComposeException {
      assert newRange != null;
      // If there are no existing ranges, then just return the new range.
      if (existingRanges == null || existingRanges.isEmpty()) {
        return Collections.singletonList(newRange);
      }
      MappedRange lastExistingRange = ListUtils.last(existingRanges);
      // Check if there are no range information in the original range. If not we do a simple
      // composition of methods.
      if (newRange.getOriginalRangeOrIdentity() == null) {
        return Collections.singletonList(
            composeOnMethodSignature(existingClassBuilder, newRange, lastExistingRange));
      }
      ExistingMappings mappedRangesForPosition = ExistingMappings.create(existingRanges);
      List<MappedRange> newComposedRanges = new ArrayList<>();
      assert newRange.minifiedRange != null;
      int minifiedStart = newRange.minifiedRange.from;
      int minifiedEnd = newRange.minifiedRange.to;
      int originalLineNumberEnd = newRange.getOriginalLineNumber(minifiedEnd);
      while (minifiedStart <= minifiedEnd) {
        int originalLineNumber = newRange.getOriginalLineNumber(minifiedStart);
        // First query if there is an interval ranging over the minifiedStart:
        // [--existing--]
        //        [minifiedStart----minifiedEnd]
        // Note that if existing mapped range has no line information, it will start at position -1.
        List<MappedRange> existingRangesForPosition =
            mappedRangesForPosition.getPreviousRanges(originalLineNumber);
        // If we find no previous ranges or the previous range is outside the minified start, we
        // query for an interval defined later:
        //                      [--existing--]
        // [minifiedStart----minifiedEnd]
        if (isRangeNullOrBefore(existingRangesForPosition, originalLineNumber)) {
          existingRangesForPosition = mappedRangesForPosition.getNextRanges(originalLineNumber);
        }
        // Check if we have the case:
        // [--existing--]                                [--existing--]
        //                [minifiedStart----minifiedEnd]
        // Where the previous existing interval is not the special one defined on -1.
        if (isRangeNullOrAfter(existingRangesForPosition, originalLineNumberEnd)) {
          Range minifiedRange = new Range(minifiedStart, minifiedEnd);
          newComposedRanges.add(newRange.copyWithNewRanges(minifiedRange, minifiedRange));
          minifiedStart = minifiedEnd + 1;
        } else {
          // Check if we have an existing mapped range without line information.
          MappedRange lastMappedRange = ListUtils.last(existingRangesForPosition);
          if (lastMappedRange.minifiedRange == null) {
            return Collections.singletonList(
                composeOnMethodSignature(existingClassBuilder, newRange, lastExistingRange));
          }
          // If we have the case:
          //                    [--existing--]
          // [minifiedStart----minifiedEnd]
          // We have to insert a mapped range from minifiedStart to existingStart.
          if (originalLineNumber < lastMappedRange.minifiedRange.from) {
            int tempMinifiedTo =
                minifiedStart + (lastMappedRange.minifiedRange.from - originalLineNumber - 1);
            Range minifiedRange = new Range(minifiedStart, tempMinifiedTo);
            Range originalRange =
                new Range(originalLineNumber, lastMappedRange.minifiedRange.from - 1);
            newComposedRanges.add(
                newRange.copyWithNewRanges(
                    minifiedRange, originalRange.isPreamble() ? originalRange : minifiedRange));
            minifiedStart = tempMinifiedTo + 1;
          } else {
            // Otherwise we have the case where we can compose directly up until the first end. If
            // the starting point matches directly, we've found the range when looking up
            // previousRanges since interval search is inclusive, the intervals are disjoint and the
            // interval is registered at minifiedStart.
            // [--existing--]
            //    [minifiedStart----minifiedEnd]
            int span = computeSpan(minifiedStart, minifiedEnd, newRange, lastMappedRange);
            composeMappedRange(
                existingClassBuilder,
                newComposedRanges,
                newRange,
                existingRangesForPosition,
                computedOutlineInformation,
                minifiedStart,
                minifiedStart + span,
                isInlineFrame);
            minifiedStart += span + 1;
          }
        }
      }
      return newComposedRanges;
    }

    private boolean isRangeNullOrBefore(
        List<MappedRange> existingRangesForPosition, int originalLineNumber) {
      return existingRangesForPosition == null
          || existingRangesForPosition.isEmpty()
          || (ListUtils.last(existingRangesForPosition).minifiedRange != null
              && ListUtils.last(existingRangesForPosition).minifiedRange.to < originalLineNumber);
    }

    private boolean isRangeNullOrAfter(
        List<MappedRange> existingRangesForPosition, int originalLineNumberEnd) {
      return existingRangesForPosition == null
          || (ListUtils.last(existingRangesForPosition).minifiedRange != null
              && originalLineNumberEnd
                  < ListUtils.last(existingRangesForPosition).minifiedRange.from);
    }

    private int computeSpan(
        int minifiedStart, int minifiedEnd, MappedRange newRange, MappedRange lastMappedRange) {
      int minifiedSpan = minifiedEnd - minifiedStart;
      if (minifiedSpan >= 1 && newRange.getOriginalRangeOrIdentity().isSingleLine()) {
        return minifiedSpan;
      }
      return Math.min(
          minifiedSpan,
          lastMappedRange.minifiedRange.to - newRange.getOriginalLineNumber(minifiedStart));
    }

    private MappedRange composeOnMethodSignature(
        ComposingClassBuilder existingClassBuilder,
        MappedRange newRange,
        MappedRange lastExistingRange)
        throws MappingComposeException {
      Range originalRange;
      if (newRange.getOriginalRangeOrIdentity() == null) {
        originalRange = null;
      } else {
        originalRange =
            lastExistingRange.originalRange != null
                    && lastExistingRange.originalRange.isSingleLine()
                ? lastExistingRange.originalRange
                : EMPTY_RANGE;
      }
      MappedRange newComposedRange =
          new MappedRange(
              newRange.minifiedRange,
              potentiallyQualifySignature(
                  newRange.signature,
                  lastExistingRange.signature,
                  existingClassBuilder.getOriginalName()),
              originalRange,
              newRange.renamedName);
      composeMappingInformation(
          newComposedRange.getAdditionalMappingInformation(),
          lastExistingRange.getAdditionalMappingInformation(),
          info -> newComposedRange.addMappingInformation(info, ConsumerUtils.emptyConsumer()));
      return newComposedRange;
    }

    /***
     * Builds a range to mapped ranges for mappings by building a lookup table sorted on the
     * starting coordinate.
     */
    private static class ExistingMappings {

      private final TreeMap<Integer, List<MappedRange>> mappedRangesForPosition;

      private ExistingMappings(TreeMap<Integer, List<MappedRange>> mappedRangesForPosition) {
        this.mappedRangesForPosition = mappedRangesForPosition;
      }

      private static ExistingMappings create(List<MappedRange> existingRanges) {
        TreeMap<Integer, List<MappedRange>> mappedRangesForPosition = new TreeMap<>();
        if (!existingRanges.isEmpty()) {
          ImmutableList.Builder<MappedRange> builder = ImmutableList.builder();
          Range lastRange = existingRanges.get(0).minifiedRange;
          if (lastRange == null) {
            assert existingRanges.size() == 1;
            mappedRangesForPosition.put(
                NO_RANGE_FROM, Collections.singletonList(existingRanges.get(0)));
          } else {
            for (MappedRange mappedRange : existingRanges) {
              if (!lastRange.equals(mappedRange.minifiedRange)) {
                mappedRangesForPosition.put(lastRange.from, builder.build());
                builder = new ImmutableList.Builder<>();
                lastRange = mappedRange.minifiedRange;
              }
              builder.add(mappedRange);
            }
            mappedRangesForPosition.put(lastRange.from, builder.build());
          }
        }
        return new ExistingMappings(mappedRangesForPosition);
      }

      private List<MappedRange> getNextRanges(int startPosition) {
        Integer ceilingKey = mappedRangesForPosition.ceilingKey(startPosition);
        return ceilingKey == null ? null : mappedRangesForPosition.get(ceilingKey);
      }

      private List<MappedRange> getPreviousRanges(int startPosition) {
        Integer floorKey = mappedRangesForPosition.floorKey(startPosition);
        return floorKey == null ? null : mappedRangesForPosition.get(floorKey);
        }
    }

    private void composeMappedRange(
        ComposingClassBuilder existingClassBuilder,
        List<MappedRange> newComposedRanges,
        MappedRange newMappedRange,
        List<MappedRange> existingMappedRanges,
        ComputedOutlineInformation computedOutlineInformation,
        int lastStartingMinifiedFrom,
        int lastEndingMinifiedTo,
        boolean isInlineFrame)
        throws MappingComposeException {
      Range existingRange = existingMappedRanges.get(0).minifiedRange;
      assert existingMappedRanges.stream().allMatch(x -> x.minifiedRange.equals(existingRange));
      Range newMinifiedRange = new Range(lastStartingMinifiedFrom, lastEndingMinifiedTo);
      for (MappedRange existingMappedRange : existingMappedRanges) {
        Range existingOriginalRange = existingMappedRange.getOriginalRangeOrIdentity();
        Range newOriginalRange;
        if (newMappedRange.minifiedRange.equals(newMinifiedRange)
            && canUseOriginalRange(
                existingOriginalRange, newMappedRange.getOriginalRangeOrIdentity())) {
          newOriginalRange = existingOriginalRange;
        } else {
          // Find the window that the new range points to into the original range.
          int existingMinifiedPos = newMappedRange.getOriginalLineNumber(lastStartingMinifiedFrom);
          int newOriginalStart = existingMappedRange.getOriginalLineNumber(existingMinifiedPos);
          if (isInlineFrame || existingOriginalRange.isCardinal) {
            newOriginalRange = new Range(newOriginalStart);
          } else if (newMappedRange.getOriginalRangeOrIdentity().span() == 1) {
            newOriginalRange = new Range(newOriginalStart, newOriginalStart);
          } else {
            assert newMinifiedRange.span() <= existingOriginalRange.span();
            newOriginalRange =
                new Range(newOriginalStart, newOriginalStart + newMinifiedRange.span() - 1);
          }
        }
        MappedRange computedRange =
            new MappedRange(
                newMinifiedRange,
                potentiallyQualifySignature(
                    newMappedRange.signature,
                    existingMappedRange.signature,
                    existingClassBuilder.getOriginalName()),
                newOriginalRange,
                newMappedRange.renamedName);
        List<MappingInformation> mappingInformationToCompose = new ArrayList<>();
        existingMappedRange
            .getAdditionalMappingInformation()
            .forEach(
                info -> {
                  if (info.isOutlineMappingInformation()) {
                    computedOutlineInformation
                        .seenOutlineMappingInformation
                        .computeIfAbsent(
                            info.asOutlineMappingInformation(), ignoreArgument(ArrayList::new))
                        .add(new ComputedMappedRangeForOutline(newMappedRange, computedRange));
                  } else if (info.isOutlineCallsiteInformation()) {
                    computedOutlineInformation
                        .outlineCallsiteMappingInformationToPatchUp
                        .computeIfAbsent(
                            info.asOutlineCallsiteInformation(), ignoreArgument(ArrayList::new))
                        .add(new ComputedMappedRangeForOutline(newMappedRange, computedRange));
                  }
                  mappingInformationToCompose.add(info);
                });
        composeMappingInformation(
            computedRange.getAdditionalMappingInformation(),
            mappingInformationToCompose,
            info -> computedRange.addMappingInformation(info, ConsumerUtils.emptyConsumer()));
        newComposedRanges.add(computedRange);
      }
    }

    private boolean canUseOriginalRange(Range existingRange, Range newRange) {
      if (newRange.equals(existingRange)) {
        return true;
      }
      if (newRange.isCardinal) {
        return false;
      }
      return existingRange == null || existingRange.span() == 1;
    }

    /***
     * Populates newMappingInformation with existingMappingInformation.
     */
    private void composeMappingInformation(
        List<MappingInformation> newMappingInformation,
        List<MappingInformation> existingMappingInformation,
        Consumer<MappingInformation> consumer)
        throws MappingComposeException {
      Set<MappingInformation> nonCompasableNewInfos = Sets.newIdentityHashSet();
      for (MappingInformation existingInfo : existingMappingInformation) {
        boolean hasBeenComposed = false;
        for (MappingInformation newInfo : newMappingInformation) {
          if (newInfo.shouldCompose(existingInfo)) {
            nonCompasableNewInfos.add(newInfo);
            consumer.accept(newInfo.compose(existingInfo));
            hasBeenComposed = true;
          }
        }
        if (!hasBeenComposed) {
          consumer.accept(existingInfo);
        }
      }
      newMappingInformation.forEach(
          info -> {
            if (!nonCompasableNewInfos.contains(info) && !info.isFileNameInformation()) {
              consumer.accept(info);
            }
          });
    }

    public void write(ChainableStringConsumer consumer) {
      consumer.accept(originalName).accept(" -> ").accept(renamedName).accept(":\n");
      additionalMappingInfo.forEach(info -> consumer.accept("# " + info.serialize()).accept("\n"));
      writeFields(consumer);
      writeMethods(consumer);
    }

    private void writeFields(ChainableStringConsumer consumer) {
      ArrayList<MemberNaming> fieldNamings = new ArrayList<>(fieldMembers.values());
      fieldNamings.sort(Comparator.comparing(MemberNaming::getOriginalName));
      fieldNamings.forEach(
          naming -> consumer.accept(INDENTATION).accept(naming.toString()).accept("\n"));
    }

    private void writeMethods(ChainableStringConsumer consumer) {
      methodsWithoutPosition.forEach(
          (ignored, mapped) -> {
            consumer.accept(INDENTATION).accept(mapped.toString()).accept("\n");
            for (MappingInformation info : mapped.getAdditionalMappingInformation()) {
              consumer.accept(INDENTATION).accept("# ").accept(info.serialize()).accept("\n");
            }
          });
      ArrayList<MethodSignature> sortedSignatures = new ArrayList<>(methodsWithPosition.keySet());
      sortedSignatures.sort(Comparator.comparing(Signature::getName));
      for (MethodSignature key : sortedSignatures) {
        methodsWithPosition
            .get(key)
            .visitSegments(
                mappedRanges ->
                    mappedRanges.forEach(
                        mappedRange -> {
                          consumer.accept(INDENTATION).accept(mappedRange.toString()).accept("\n");
                          for (MappingInformation info :
                              mappedRange.getAdditionalMappingInformation()) {
                            consumer
                                .accept(INDENTATION)
                                .accept("# ")
                                .accept(info.serialize())
                                .accept("\n");
                          }
                        }));
      }
    }

    public ComposingClassBuilder commit(ComposingClassBuilder classBuilder)
        throws MappingComposeException {
      ComposingClassBuilder newClassBuilder =
          new ComposingClassBuilder(
              originalName, classBuilder.renamedName, committed, null, options);
      composeMappingInformation(
          classBuilder.additionalMappingInfo,
          additionalMappingInfo,
          newClassBuilder.additionalMappingInfo::add);
      // Composed field namings and method namings should be freely composable by addition since
      // any renaming/position change should have removed the existing committed mapping.
      putAll(
          newClassBuilder.fieldMembers,
          fieldMembers,
          classBuilder.fieldMembers,
          (committed, add) -> {
            assert committed == null;
            return add;
          });
      putAll(
          newClassBuilder.methodsWithoutPosition,
          methodsWithoutPosition,
          classBuilder.methodsWithoutPosition,
          (committed, add) -> {
            if (committed != null && add != null) {
              throw new MappingComposeException(
                  "Cannot compose duplicate methods without position in class '"
                      + renamedName
                      + "': '"
                      + committed
                      + "' and '"
                      + add);
            }
            return committed != null ? committed : add;
          });
      putAll(
          newClassBuilder.methodsWithPosition,
          methodsWithPosition,
          classBuilder.methodsWithPosition,
          (committed, add) -> add);
      return newClassBuilder;
    }

    private <S extends Signature, V> void putAll(
        Map<S, V> output,
        Map<S, V> committed,
        Map<S, V> toAdd,
        ThrowingBiFunction<V, V, V, MappingComposeException> compose)
        throws MappingComposeException {
      assert output.isEmpty();
      output.putAll(committed);
      for (Entry<S, V> kvEntry : toAdd.entrySet()) {
        output.put(
            kvEntry.getKey(), compose.apply(output.get(kvEntry.getKey()), kvEntry.getValue()));
      }
    }

    private MethodSignature potentiallyQualifySignature(
        MethodSignature newSignature, MethodSignature signature, String originalHolder) {
      return !newSignature.isQualified() || signature.isQualified()
          ? signature
          : new MethodSignature(
              originalHolder + "." + signature.name, signature.type, signature.parameters);
    }

    private static class RangeBuilder {

      private int start = Integer.MAX_VALUE;
      private int end = Integer.MIN_VALUE;

      private void addRange(Range range) {
        if (range != null) {
          start = Math.min(start, range.from);
          end = Math.max(end, range.to);
        }
      }

      private boolean hasValue() {
        return start < Integer.MAX_VALUE;
      }

      private int getStartOrNoRangeFrom() {
        return hasValue() ? start : NO_RANGE_FROM;
      }

      private int getEndOrNoRangeFrom() {
        return hasValue() ? end : NO_RANGE_FROM;
      }

    }

    private static class ComputedOutlineInformation {
      private final Map<OutlineCallsiteMappingInformation, List<ComputedMappedRangeForOutline>>
          outlineCallsiteMappingInformationToPatchUp = new IdentityHashMap<>();
      private final Map<OutlineMappingInformation, List<ComputedMappedRangeForOutline>>
          seenOutlineMappingInformation = new IdentityHashMap<>();

      private ComputedMappedRangeForOutline getComputedRange(
          MappingInformation outline, MappedRange current) {
        List<ComputedMappedRangeForOutline> outlineMappingInformations =
            outline.isOutlineMappingInformation()
                ? seenOutlineMappingInformation.get(outline.asOutlineMappingInformation())
                : outlineCallsiteMappingInformationToPatchUp.get(
                    outline.asOutlineCallsiteInformation());
        if (outlineMappingInformations == null) {
          return null;
        }
        return ListUtils.firstMatching(
            outlineMappingInformations,
            pair -> pair.composed.minifiedRange.contains(current.minifiedRange.from));
      }
    }

    private static class ComputedMappedRangeForOutline {
      private final MappedRange current;
      private final MappedRange composed;

      private ComputedMappedRangeForOutline(MappedRange current, MappedRange composed) {
        this.current = current;
        this.composed = composed;
      }
    }
  }
}
