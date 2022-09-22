// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

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
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.ThrowsCondition;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SegmentTree;
import com.android.tools.r8.utils.ThrowingBiFunction;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ComposingBuilder {

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
    // TODO(b/241763080): Should be stable map version.
    if (newMapVersion.isLessThan(MapVersion.MAP_VERSION_EXPERIMENTAL)
        || newMapVersion.isUnknown()) {
      throw new MappingComposeException(
          "Composition of mapping files supported from map version 2.2.");
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


  @Override
  public String toString() {
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
          classBuilder = existingBuilder.commit(classBuilder);
        }
        newClassBuilders.put(renamedName, classBuilder);
      }
      for (Entry<String, ComposingClassBuilder> existingEntry : classBuilders.entrySet()) {
        if (!updatedClassBuilders.contains(existingEntry.getKey())) {
          ComposingClassBuilder classBuilder = existingEntry.getValue();
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

    public String getHolderTypeName() {
      return holderTypeName;
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
        Range originalRange = mappedRange.originalRange;
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

    public int lookupFirst(int originalPosition) {
      List<Integer> minifiedPositions = originalToMinified.get(originalPosition);
      return minifiedPositions == null ? 0 : minifiedPositions.get(0);
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
      committedPreviousClassBuilder = committed.classBuilders.get(originalName);
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
            if (committedPreviousClassBuilder != null) {
              MemberNaming existingMemberNaming =
                  committedPreviousClassBuilder.fieldMembers.remove(originalSignature);
              if (existingMemberNaming != null) {
                fieldNamingToAdd =
                    new MemberNaming(
                        existingMemberNaming.getOriginalSignature(), residualSignature);
              }
            }
            MemberNaming existing = fieldMembers.put(residualSignature, fieldNamingToAdd);
            assert existing == null;
          });
    }

    private void composeMethodNamings(
        ClassNamingForNameMapper mapper, ClassNameMapper classNameMapper)
        throws MappingComposeException {
      Map<String, String> inverseClassMapping =
          classNameMapper.getObfuscatedToOriginalMapping().inverse;
      for (Entry<String, MappedRangesOfName> entry : mapper.mappedRangesByRenamedName.entrySet()) {
        MappedRangesOfName value = entry.getValue();
        List<MappedRange> mappedRanges = value.getMappedRanges();
        MappedRangeResult mappedRangeResult;
        int index = 0;
        while ((mappedRangeResult = getMappedRangesForMethod(mappedRanges, index)) != null) {
          index = mappedRangeResult.endIndex;
          MappedRange newMappedRange = mappedRangeResult.lastRange;
          MethodSignature originalSignature = newMappedRange.signature;
          List<MappedRange> existingMappedRanges = null;
          // Remove the existing entry if it exists.
          if (committedPreviousClassBuilder != null) {
            SegmentTree<List<MappedRange>> listSegmentTree =
                committedPreviousClassBuilder.methodsWithPosition.get(originalSignature);
            if (listSegmentTree != null) {
              // Any new transformation can be lossy - for example, D8 only promises to keep line
              // positions if the method is not throwing (and is not debug). Additionally, R8/PG
              // emits `1:1:void foo() -> a` instead of `1:1:void foo():1:1 -> a`, so R8 must
              // capture the preamble position by explicitly inserting 0 as original range.
              Entry<Integer, List<MappedRange>> existingEntry =
                  listSegmentTree.findEntry(mappedRangeResult.startOriginalPosition);
              // We assume that all new minified ranges for a method are rewritten in the new map
              // such that no previous existing positions exists.
              if (existingEntry != null) {
                listSegmentTree.removeSegment(existingEntry.getKey());
                existingMappedRanges = existingEntry.getValue();
              } else {
                Range originalRange = newMappedRange.originalRange;
                // The original can be discarded if it no longer exists or if the method is
                // non-throwing.
                if (mappedRangeResult.startOriginalPosition > 0
                    && (originalRange == null || !newMappedRange.originalRange.isPreamble())
                    && !options.mappingComposeOptions().allowNonExistingOriginalRanges) {
                  throw new MappingComposeException(
                      "Could not find original starting position of '"
                          + mappedRangeResult.lastRange
                          + "' which should be "
                          + mappedRangeResult.startOriginalPosition);
                }
              }
              assert newMappedRange.minifiedRange != null;
            } else {
              MappedRange existingMappedRange =
                  committedPreviousClassBuilder.methodsWithoutPosition.get(originalSignature);
              if (existingMappedRange != null) {
                committedPreviousClassBuilder.methodsWithoutPosition.remove(originalSignature);
              }
              existingMappedRanges =
                  existingMappedRange == null
                      ? null
                      : Collections.singletonList(existingMappedRange);
            }
          }
          List<MappedRange> composedRanges =
              composeMappedRangesForMethod(existingMappedRanges, mappedRangeResult.allRanges);
          MappedRange lastComposedRange = ListUtils.last(composedRanges);
          MethodSignature residualSignature =
              newMappedRange
                  .computeResidualSignature(type -> inverseClassMapping.getOrDefault(type, type))
                  .asMethodSignature();
          if (lastComposedRange.minifiedRange != null) {
            methodsWithPosition
                .computeIfAbsent(residualSignature, ignored -> new SegmentTree<>(false))
                .add(
                    mappedRangeResult.startMinifiedPosition,
                    newMappedRange.minifiedRange.to,
                    composedRanges);
          } else {
            assert composedRanges.size() == 1;
            methodsWithoutPosition.put(residualSignature, lastComposedRange);
          }
        }
      }
    }

    /***
     * Iterates over mapped ranges in order, starting from index, and adds to an internal result as
     * long as the current mapped range is the same method and return a mapped range result
     * containing all ranges for a method along with some additional information.
     */
    private MappedRangeResult getMappedRangesForMethod(List<MappedRange> mappedRanges, int index)
        throws MappingComposeException {
      if (index >= mappedRanges.size()) {
        return null;
      }
      int startIndex = index;
      List<MappedRange> seenMappedRanges = new ArrayList<>();
      int startMinifiedPosition = NO_RANGE_FROM;
      int startOriginalPosition = NO_RANGE_FROM;
      MappedRange lastOutermost = null;
      while (index < mappedRanges.size()) {
        List<MappedRange> mappedRangesForThisInterval = new ArrayList<>();
        index =
            addAllInlineFramesUntilOutermostCaller(
                mappedRanges, index, mappedRangesForThisInterval);
        assert mappedRangesForThisInterval.size() > 0;
        MappedRange lastForThisInterval = ListUtils.last(mappedRangesForThisInterval);
        if (lastOutermost == null) {
          startMinifiedPosition =
              lastForThisInterval.minifiedRange != null
                  ? lastForThisInterval.minifiedRange.from
                  : NO_RANGE_FROM;
          startOriginalPosition =
              lastForThisInterval.getFirstPositionOfOriginalRange(NO_RANGE_FROM);
        }
        if (lastOutermost != null
            && !lastForThisInterval.signature.equals(lastOutermost.signature)) {
          break;
        }
        // Register mapping information that is dependent on the residual naming to allow updating
        // later on.
        for (MappedRange mappedRange : mappedRangesForThisInterval) {
          for (MappingInformation mappingInformation : mappedRange.getAdditionalMappingInfo()) {
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
          seenMappedRanges.add(mappedRange);
        }
        lastOutermost = lastForThisInterval;
      }
      return new MappedRangeResult(
          startMinifiedPosition,
          startOriginalPosition,
          startIndex + seenMappedRanges.size(),
          lastOutermost,
          seenMappedRanges);
    }

    private int addAllInlineFramesUntilOutermostCaller(
        List<MappedRange> mappedRanges, int index, List<MappedRange> seenMappedRanges) {
      assert index < mappedRanges.size();
      while (isInlineMappedRange(mappedRanges, index)) {
        seenMappedRanges.add(mappedRanges.get(index++));
      }
      seenMappedRanges.add(mappedRanges.get(index++));
      return index;
    }

    private List<MappedRange> composeMappedRangesForMethod(
        List<MappedRange> existingRanges, List<MappedRange> newRanges)
        throws MappingComposeException {
      assert !newRanges.isEmpty();
      if (existingRanges == null || existingRanges.isEmpty()) {
        return newRanges;
      }
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
      Box<Range> originalRange = new Box<>();
      ExistingMapping mappedRangesForPosition =
          getExistingMapping(
              existingRanges, (start, end) -> originalRange.set(new Range(start, end)));
      List<MappedRange> newComposedRanges = new ArrayList<>();
      ComputedOutlineInformation computedOutlineInformation = new ComputedOutlineInformation();
      for (int i = 0; i < newRanges.size(); i++) {
        if (isInlineMappedRange(newRanges, i)) {
          throw new MappingComposeException(
              "Unexpected inline frame '" + existingRanges.get(i) + "' in composing map.");
        }
        MappedRange newRange = newRanges.get(i);
        MappedRange lastExistingRange = ListUtils.last(existingRanges);
        if (newRange.originalRange == null && newRange.minifiedRange == null) {
          MappedRange newComposedRange =
              new MappedRange(
                  null, lastExistingRange.signature, originalRange.get(), newRange.renamedName);
          composeMappingInformation(
              newComposedRange.getAdditionalMappingInfo(),
              lastExistingRange.getAdditionalMappingInfo(),
              info -> newComposedRange.addMappingInformation(info, ConsumerUtils.emptyConsumer()));
          newComposedRanges.add(newComposedRange);
        } else {
          assert newRange.minifiedRange != null;
          // First check if the original range matches the existing minified range.
          List<MappedRange> existingMappedRanges =
              mappedRangesForPosition.getMappedRangesForPosition(
                  newRange.getFirstPositionOfOriginalRange(NO_RANGE_FROM));
          if (existingMappedRanges == null) {
            // If we cannot lookup the original position because it has been removed we compose with
            // the existing method signature.
            if (newRange.originalRange != null && newRange.originalRange.isPreamble()) {
              newComposedRanges.add(
                  new MappedRange(
                      null,
                      lastExistingRange.signature,
                      originalRange.get(),
                      newRange.renamedName));
              continue;
            } else {
              throw new MappingComposeException(
                  "Unexpected missing original position for '" + newRange + "'.");
            }
          }
          // We have found an existing range for the original position.
          MappedRange lastExistingMappedRange = ListUtils.last(existingMappedRanges);
          // If the existing mapped minified range is equal to the original range of the new range
          // then we have a perfect mapping that we can translate directly.
          if (lastExistingMappedRange.minifiedRange.equals(newRange.originalRange)) {
            computeComposedMappedRange(
                newComposedRanges,
                newRange,
                existingMappedRanges,
                computedOutlineInformation,
                newRange.minifiedRange.from,
                newRange.minifiedRange.to);
          } else {
            // Otherwise, we have a situation where the minified range references over multiple
            // existing ranges. We simply chop op when the range changes on the right hand side. To
            // ensure we do not mess up the spans from the original range, we have to check if the
            // current starting position is inside an original range, and then chop it off.
            // Similarly, when writing the last block, we have to cut it off to match.
            int lastStartingMinifiedFrom = newRange.minifiedRange.from;
            for (int position = newRange.minifiedRange.from;
                position <= newRange.minifiedRange.to;
                position++) {
              List<MappedRange> existingMappedRangesForPosition =
                  mappedRangesForPosition.getMappedRangesForPosition(
                      newRange.getOriginalLineNumber(position));
              MappedRange lastExistingMappedRangeForPosition = null;
              if (existingMappedRangesForPosition != null) {
                lastExistingMappedRangeForPosition =
                    ListUtils.last(existingMappedRangesForPosition);
              }
              if (lastExistingMappedRangeForPosition == null
                  || !lastExistingMappedRange.minifiedRange.equals(
                      lastExistingMappedRangeForPosition.minifiedRange)) {
                // We have seen an existing range we have to compute a splitting for.
                computeComposedMappedRange(
                    newComposedRanges,
                    newRange,
                    existingMappedRanges,
                    computedOutlineInformation,
                    lastStartingMinifiedFrom,
                    position - 1);
                // Advance the last starting position to this point and advance the existing mapped
                // ranges for this position.
                lastStartingMinifiedFrom = position;
                if (existingMappedRangesForPosition == null) {
                  if (!options.mappingComposeOptions().allowNonExistingOriginalRanges) {
                    throw new MappingComposeException(
                        "Unexpected missing original position for '" + newRange + "'.");
                  }
                  // We are at the first position of a hole. If we have existing ranges:
                  // 1:1:void foo():41:41 -> a
                  // 9:9:void foo():49:49 -> a
                  // We may have a new range that is:
                  // 21:29:void foo():1:9
                  // We end up here at position 2 and we have already committed
                  // 21:21:void foo():41:41.
                  // We then introduce a "fake" normal range to simulate the result of retracing one
                  // after the other to end up with:
                  // 21:21:void foo():41:41
                  // 22:28:void foo():2:8
                  // 29:29:void foo():49:49.
                  int startOriginalPosition = newRange.getOriginalLineNumber(position);
                  Integer endOriginalPosition =
                      mappedRangesForPosition.getCeilingForPosition(position);
                  if (endOriginalPosition == null) {
                    endOriginalPosition = newRange.getLastPositionOfOriginalRange() + 1;
                  }
                  Range newOriginalRange =
                      new Range(startOriginalPosition, endOriginalPosition - 1);
                  MappedRange nonExistingMappedRange =
                      new MappedRange(
                          newOriginalRange,
                          lastExistingMappedRange.getOriginalSignature().asMethodSignature(),
                          newOriginalRange,
                          lastExistingMappedRange.renamedName);
                  nonExistingMappedRange.setResidualSignatureInternal(
                      lastExistingRange.getResidualSignatureInternal());
                  lastExistingMappedRange = nonExistingMappedRange;
                  existingMappedRanges = Collections.singletonList(nonExistingMappedRange);
                  position += (endOriginalPosition - startOriginalPosition) - 1;
                } else {
                  lastExistingMappedRange = lastExistingMappedRangeForPosition;
                  existingMappedRanges = existingMappedRangesForPosition;
                }
              }
            }
            computeComposedMappedRange(
                newComposedRanges,
                newRange,
                existingMappedRanges,
                computedOutlineInformation,
                lastStartingMinifiedFrom,
                newRange.minifiedRange.to);
          }
        }
      }
      MappedRange lastComposedRange = ListUtils.last(newComposedRanges);
      if (computedOutlineInformation.seenOutlineMappingInformation != null) {
        current
            .getUpdateOutlineCallsiteInformation(
                committedPreviousClassBuilder.getRenamedName(),
                ListUtils.last(newRanges).signature.getName(),
                lastComposedRange.getRenamedName())
            .setNewMappedRanges(newRanges);
        lastComposedRange.addMappingInformation(
            computedOutlineInformation.seenOutlineMappingInformation,
            ConsumerUtils.emptyConsumer());
      }
      if (!computedOutlineInformation.outlineCallsiteMappingInformationToPatchUp.isEmpty()) {
        MappedRangeOriginalToMinifiedMap originalToMinifiedMap =
            MappedRangeOriginalToMinifiedMap.build(newRanges);
        List<OutlineCallsiteMappingInformation> outlineCallSites =
            new ArrayList<>(computedOutlineInformation.outlineCallsiteMappingInformationToPatchUp);
        outlineCallSites.sort(Comparator.comparing(mapping -> mapping.getOutline().toString()));
        for (OutlineCallsiteMappingInformation outlineCallSite : outlineCallSites) {
          Int2IntSortedMap positionMap = outlineCallSite.getPositions();
          for (Integer keyPosition : positionMap.keySet()) {
            int keyPositionInt = keyPosition;
            int originalDestination = positionMap.get(keyPositionInt);
            int newDestination = originalToMinifiedMap.lookupFirst(originalDestination);
            positionMap.put(keyPositionInt, newDestination);
          }
          lastComposedRange.addMappingInformation(outlineCallSite, ConsumerUtils.emptyConsumer());
        }
      }
      return newComposedRanges;
    }

    public interface ExistingMapping {

      Integer getCeilingForPosition(int i);

      List<MappedRange> getMappedRangesForPosition(int i);
    }

    /***
     * Builds a position to mapped ranges for mappings for looking up all mapped ranges for a given
     * position.
     */
    private ExistingMapping getExistingMapping(
        List<MappedRange> existingRanges, BiConsumer<Integer, Integer> positions) {
      TreeMap<Integer, List<MappedRange>> mappedRangesForPosition = new TreeMap<>();
      List<MappedRange> currentRangesForPosition = new ArrayList<>();
      int startExisting = NO_RANGE_FROM;
      int endExisting = NO_RANGE_FROM;
      boolean isCatchAll = false;
      for (int i = 0; i < existingRanges.size(); i++) {
        MappedRange mappedRange = existingRanges.get(i);
        currentRangesForPosition.add(mappedRange);
        if (!isInlineMappedRange(existingRanges, i)) {
          if (startExisting == NO_RANGE_FROM) {
            startExisting = mappedRange.getFirstPositionOfOriginalRange(NO_RANGE_FROM);
          }
          endExisting = Math.max(mappedRange.getLastPositionOfOriginalRange(), endExisting);
          if (mappedRange.minifiedRange == null) {
            mappedRangesForPosition.put(NO_RANGE_FROM, currentRangesForPosition);
          } else if (mappedRange.minifiedRange.isCatchAll()) {
            isCatchAll = true;
          } else {
            for (int position = mappedRange.minifiedRange.from;
                position <= mappedRange.minifiedRange.to;
                position++) {
              mappedRangesForPosition.put(position, currentRangesForPosition);
            }
          }
          currentRangesForPosition = new ArrayList<>();
        }
      }
      if (startExisting > NO_RANGE_FROM) {
        positions.accept(startExisting, endExisting);
      }
      boolean finalIsCatchAll = isCatchAll;
      List<MappedRange> finalCurrentRangesForPosition = currentRangesForPosition;
      return new ExistingMapping() {
        @Override
        public Integer getCeilingForPosition(int i) {
          return finalIsCatchAll ? i : mappedRangesForPosition.ceilingKey(i);
        }

        @Override
        public List<MappedRange> getMappedRangesForPosition(int i) {
          return finalIsCatchAll ? finalCurrentRangesForPosition : mappedRangesForPosition.get(i);
        }
      };
    }

    private void computeComposedMappedRange(
        List<MappedRange> newComposedRanges,
        MappedRange newMappedRange,
        List<MappedRange> existingMappedRanges,
        ComputedOutlineInformation computedOutlineInformation,
        int lastStartingMinifiedFrom,
        int position)
        throws MappingComposeException {
      Range existingRange = existingMappedRanges.get(0).minifiedRange;
      assert existingMappedRanges.stream().allMatch(x -> x.minifiedRange.equals(existingRange));
      Range newMinifiedRange = new Range(lastStartingMinifiedFrom, position);
      boolean copyOriginalRange = existingRange.equals(newMappedRange.originalRange);
      for (MappedRange existingMappedRange : existingMappedRanges) {
        Range existingOriginalRange = existingMappedRange.originalRange;
        Range newOriginalRange;
        if (copyOriginalRange
            || existingOriginalRange == null
            || existingOriginalRange.span() == 1) {
          newOriginalRange = existingOriginalRange;
        } else {
          // Find the window that the new range points to into the original range.
          int existingMinifiedPos = newMappedRange.getOriginalLineNumber(lastStartingMinifiedFrom);
          int newOriginalStart = existingMappedRange.getOriginalLineNumber(existingMinifiedPos);
          if (newMappedRange.originalRange.span() == 1) {
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
                existingMappedRange.signature,
                newOriginalRange,
                newMappedRange.renamedName);
        List<MappingInformation> mappingInformationToCompose = new ArrayList<>();
        existingMappedRange
            .getAdditionalMappingInfo()
            .forEach(
                info -> {
                  if (info.isOutlineMappingInformation()) {
                    computedOutlineInformation.seenOutlineMappingInformation =
                        info.asOutlineMappingInformation();
                  } else if (info.isOutlineCallsiteInformation()) {
                    computedOutlineInformation.outlineCallsiteMappingInformationToPatchUp.add(
                        info.asOutlineCallsiteInformation());
                  } else {
                    mappingInformationToCompose.add(info);
                  }
                });
        composeMappingInformation(
            computedRange.getAdditionalMappingInfo(),
            mappingInformationToCompose,
            info -> computedRange.addMappingInformation(info, ConsumerUtils.emptyConsumer()));
        newComposedRanges.add(computedRange);
      }
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
            if (!nonCompasableNewInfos.contains(info)) {
              consumer.accept(info);
            }
          });
    }

    private boolean isInlineMappedRange(List<MappedRange> mappedRanges, int index) {
      // We are comparing against the next entry so we need a buffer of one.
      if (index + 1 >= mappedRanges.size()) {
        return false;
      }
      MappedRange mappedRange = mappedRanges.get(index);
      return mappedRange.minifiedRange != null
          && mappedRange.minifiedRange.equals(mappedRanges.get(index + 1).minifiedRange);
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
      Map<String, List<MappedRange>> signatureToMappedRanges = new HashMap<>();
      methodsWithoutPosition.forEach(
          (ignored, mapped) -> {
            consumer.accept(INDENTATION).accept(mapped.toString()).accept("\n");
            for (MappingInformation info : mapped.getAdditionalMappingInfo()) {
              consumer.accept(INDENTATION).accept("# ").accept(info.serialize()).accept("\n");
            }
          });
      methodsWithPosition
          .values()
          .forEach(
              segmentTree -> {
                segmentTree.visitSegments(
                    mappedRanges -> {
                      MethodSignature originalSignature = ListUtils.last(mappedRanges).signature;
                      List<MappedRange> put =
                          signatureToMappedRanges.put(
                              originalSignature.getName() + "_" + originalSignature, mappedRanges);
                      assert put == null;
                    });
              });
      ArrayList<String> strings = new ArrayList<>(signatureToMappedRanges.keySet());
      Collections.sort(strings);
      for (String key : strings) {
        signatureToMappedRanges
            .get(key)
            .forEach(
                mappedRange -> {
                  consumer.accept(INDENTATION).accept(mappedRange.toString()).accept("\n");
                  for (MappingInformation info : mappedRange.getAdditionalMappingInfo()) {
                    consumer.accept(INDENTATION).accept("# ").accept(info.serialize()).accept("\n");
                  }
                });
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

    private static class MappedRangeResult {

      private final int startMinifiedPosition;
      private final int startOriginalPosition;
      private final int endIndex;
      private final MappedRange lastRange;
      private final List<MappedRange> allRanges;

      public MappedRangeResult(
          int startMinifiedPosition,
          int startOriginalPosition,
          int endIndex,
          MappedRange lastRange,
          List<MappedRange> allRanges) {
        this.startMinifiedPosition = startMinifiedPosition;
        this.startOriginalPosition = startOriginalPosition;
        this.endIndex = endIndex;
        this.lastRange = lastRange;
        this.allRanges = allRanges;
      }
    }

    private static class ComputedOutlineInformation {
      private final Set<OutlineCallsiteMappingInformation>
          outlineCallsiteMappingInformationToPatchUp = Sets.newIdentityHashSet();
      private OutlineMappingInformation seenOutlineMappingInformation = null;
    }
  }
}
