// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.ThrowsCondition;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BiMapContainer;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SegmentTree;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ComposingBuilder {

  /**
   * To ensure we can do alpha renaming of classes and members without polluting the existing
   * mappping, we use a committed map that we update for each class name mapping. That allows us to
   * rename to existing renamed names as long as these are also renamed later in the map.
   */
  private final Map<String, ComposingClassBuilder> committed = new HashMap<>();

  private Map<String, ComposingClassBuilder> current = new HashMap<>();

  private MapVersionMappingInformation currentMapVersion = null;

  private final ComposingSharedData sharedData = new ComposingSharedData();

  public void compose(ClassNameMapper classNameMapper) throws MappingComposeException {
    MapVersionMappingInformation thisMapVersion = classNameMapper.getFirstMapVersionInformation();
    if (thisMapVersion != null) {
      if (currentMapVersion == null
          || currentMapVersion.getMapVersion().isLessThan(thisMapVersion.getMapVersion())) {
        currentMapVersion = thisMapVersion;
      }
    }
    sharedData.patchupMappingInformation(classNameMapper);
    for (ClassNamingForNameMapper classMapping : classNameMapper.getClassNameMappings().values()) {
      compose(classMapping);
    }
    commit();
  }

  private void compose(ClassNamingForNameMapper classMapping) throws MappingComposeException {
    String originalName = classMapping.originalName;
    ComposingClassBuilder composingClassBuilder = committed.get(originalName);
    String renamedName = classMapping.renamedName;
    if (composingClassBuilder == null) {
      composingClassBuilder = new ComposingClassBuilder(originalName, renamedName, sharedData);
    } else {
      composingClassBuilder.setRenamedName(renamedName);
      committed.remove(originalName);
    }
    ComposingClassBuilder duplicateMapping = current.put(renamedName, composingClassBuilder);
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
    composingClassBuilder.compose(classMapping);
  }

  private void commit() throws MappingComposeException {
    for (Entry<String, ComposingClassBuilder> newEntry : current.entrySet()) {
      String renamedName = newEntry.getKey();
      ComposingClassBuilder classBuilder = newEntry.getValue();
      ComposingClassBuilder duplicateMapping = committed.put(renamedName, classBuilder);
      if (duplicateMapping != null) {
        throw new MappingComposeException(
            "Duplicate class mapping. Both '"
                + duplicateMapping.getOriginalName()
                + "' and '"
                + classBuilder.getOriginalName()
                + "' maps to '"
                + renamedName
                + "'.");
      }
    }
    current = new HashMap<>();
  }

  @Override
  public String toString() {
    List<ComposingClassBuilder> classBuilders = new ArrayList<>(committed.values());
    classBuilders.sort(Comparator.comparing(ComposingClassBuilder::getOriginalName));
    StringBuilder sb = new StringBuilder();
    // TODO(b/241763080): Keep preamble of mapping files"
    if (currentMapVersion != null) {
      sb.append("# ").append(currentMapVersion.serialize()).append("\n");
    }
    ChainableStringConsumer wrap = ChainableStringConsumer.wrap(sb::append);
    for (ComposingClassBuilder classBuilder : classBuilders) {
      classBuilder.write(wrap);
    }
    return sb.toString();
  }

  public static class ComposingSharedData {

    /**
     * RewriteFrameInformation contains condition clauses that are bound to the residual program. As
     * a result of that, we have to patch up the conditions when we compose new class mappings.
     */
    private final List<RewriteFrameMappingInformation> mappingInformationToPatchUp =
        new ArrayList<>();

    private void patchupMappingInformation(ClassNameMapper classNameMapper) {
      BiMapContainer<String, String> obfuscatedToOriginalMapping =
          classNameMapper.getObfuscatedToOriginalMapping();
      for (RewriteFrameMappingInformation rewriteMappingInfo : mappingInformationToPatchUp) {
        rewriteMappingInfo
            .getConditions()
            .forEach(
                rewriteCondition -> {
                  ThrowsCondition throwsCondition = rewriteCondition.asThrowsCondition();
                  if (throwsCondition != null) {
                    String originalName = throwsCondition.getClassReference().getTypeName();
                    String obfuscatedName = obfuscatedToOriginalMapping.inverse.get(originalName);
                    if (obfuscatedName != null) {
                      throwsCondition.setClassReferenceInternal(
                          Reference.classFromTypeName(obfuscatedName));
                    }
                  }
                });
      }
    }
  }

  public static class ComposingClassBuilder {

    private static final String INDENTATION = "    ";
    private static final int NO_RANGE_FROM = -1;

    private final String originalName;
    private String renamedName;
    private final Map<String, List<MemberNaming>> fieldMembers = new HashMap<>();
    // Ideally we would have liked to use the signature as a key since this uniquely specifies a
    // method. However, because a mapping file has no right hand side, we therefore assume that a
    // starting position uniquely identifies a method. If no position is given there can be only
    // one method since any shrinker should put in line numbers for overloads.
    private final Map<String, SegmentTree<List<MappedRange>>> methodMembers = new HashMap<>();
    private List<MappingInformation> additionalMappingInfo = null;
    private final ComposingSharedData sharedData;

    private ComposingClassBuilder(
        String originalName, String renamedName, ComposingSharedData sharedData) {
      this.originalName = originalName;
      this.renamedName = renamedName;
      this.sharedData = sharedData;
    }

    public void setRenamedName(String renamedName) {
      this.renamedName = renamedName;
    }

    public String getOriginalName() {
      return originalName;
    }

    public String getRenamedName() {
      return renamedName;
    }

    public void compose(ClassNamingForNameMapper mapper) throws MappingComposeException {
      List<MappingInformation> newMappingInfo = mapper.getAdditionalMappingInfo();
      if (newMappingInfo != null && !newMappingInfo.isEmpty()) {
        if (additionalMappingInfo == null) {
          additionalMappingInfo = new ArrayList<>();
        }
        additionalMappingInfo.addAll(newMappingInfo);
      }
      composeFieldNamings(mapper);
      composeMethodNamings(mapper);
    }

    private void composeFieldNamings(ClassNamingForNameMapper mapper)
        throws MappingComposeException {
      mapper.forAllFieldNaming(
          fieldNaming -> {
            List<MemberNaming> memberNamings = fieldMembers.get(fieldNaming.getOriginalName());
            if (memberNamings == null) {
              fieldMembers
                  .computeIfAbsent(fieldNaming.getRenamedName(), ignoreArgument(ArrayList::new))
                  .add(fieldNaming);
              return;
            }
            // There is no right-hand side of field mappings thus if we have seen an existing
            // mapping we cannot compose the type. For fields we check that the original type is
            // the same or we throw an error since we cannot guarantee a proper composition.
            for (int i = 0; i < memberNamings.size(); i++) {
              MemberNaming memberNaming = memberNamings.get(i);
              assert memberNaming.getRenamedName().equals(fieldNaming.getOriginalName());
              if (memberNaming.renamedSignature.equals(fieldNaming.getOriginalSignature())) {
                memberNamings.set(
                    i,
                    new MemberNaming(
                        memberNaming.getOriginalSignature(), fieldNaming.getRenamedName()));
                return;
              }
            }
            throw new MappingComposeException(
                "Unable to compose field naming '"
                    + fieldNaming
                    + "' since the original type has changed.");
          });
    }

    private void composeMethodNamings(ClassNamingForNameMapper mapper)
        throws MappingComposeException {
      for (Entry<String, MappedRangesOfName> entry : mapper.mappedRangesByRenamedName.entrySet()) {
        MappedRangesOfName value = entry.getValue();
        List<MappedRange> mappedRanges = value.getMappedRanges();
        MappedRangeResult mappedRangeResult;
        int index = 0;
        while ((mappedRangeResult = getMappedRangesForMethod(mappedRanges, index)) != null) {
          index = mappedRangeResult.endIndex;
          MappedRange newMappedRange = mappedRangeResult.lastRange;
          String originalName = newMappedRange.signature.getName();
          SegmentTree<List<MappedRange>> listSegmentTree = methodMembers.get(originalName);
          List<MappedRange> existingMappedRanges = null;
          if (listSegmentTree != null) {
            Entry<Integer, List<MappedRange>> existingEntry =
                listSegmentTree.findEntry(mappedRangeResult.startOriginalPosition);
            // We assume that all new minified ranges for a method are rewritten in the new map
            // such that no previous existing positions exists.
            if (existingEntry != null) {
              listSegmentTree.removeSegment(existingEntry.getKey());
              existingMappedRanges = existingEntry.getValue();
            }
          }
          Range minifiedRange = mappedRangeResult.lastRange.minifiedRange;
          int endMinifiedPosition = minifiedRange == null ? NO_RANGE_FROM : minifiedRange.to;
          methodMembers
              .computeIfAbsent(newMappedRange.renamedName, ignored -> new SegmentTree<>(false))
              .add(
                  mappedRangeResult.startMinifiedPosition,
                  endMinifiedPosition,
                  composeMappedRangesForMethod(existingMappedRanges, mappedRangeResult.allRanges));
        }
      }
    }

    /***
     * Iterates over mapped ranges in order, starting from index, and adds to an internal result as
     * long as the current mapped range is the same method and return a mapped range result
     * containing all ranges for a method along with some additional information.
     */
    private MappedRangeResult getMappedRangesForMethod(List<MappedRange> mappedRanges, int index) {
      if (index >= mappedRanges.size()) {
        return null;
      }
      List<MappedRange> seenMappedRanges = new ArrayList<>();
      MappedRange lastSeen = mappedRanges.get(index);
      seenMappedRanges.add(lastSeen);
      if (lastSeen.minifiedRange == null) {
        return new MappedRangeResult(
            NO_RANGE_FROM, NO_RANGE_FROM, index + 1, lastSeen, seenMappedRanges);
      }
      int startMinifiedPosition = lastSeen.minifiedRange.from;
      int startOriginalPosition =
          lastSeen.originalRange == null ? NO_RANGE_FROM : lastSeen.originalRange.from;
      for (int i = index + 1; i < mappedRanges.size(); i++) {
        MappedRange thisMappedRange = mappedRanges.get(i);
        // We assume that if we see a mapping where the minified range is 0 then we will not find
        // another mapping where it is not null.
        if (thisMappedRange.minifiedRange == null) {
          assert !lastSeen.signature.equals(thisMappedRange.signature);
          break;
        }
        // Otherwise break if we see a signature that is not equal to the current one and it
        // has another minified range meaning it is not an inlinee.
        if (!thisMappedRange.signature.equals(lastSeen.signature)
            && !thisMappedRange.minifiedRange.equals(lastSeen.minifiedRange)
            && !isInlineMappedRange(mappedRanges, i)) {
          break;
        }
        for (MappingInformation mappingInformation : thisMappedRange.getAdditionalMappingInfo()) {
          if (mappingInformation.isRewriteFrameMappingInformation()) {
            sharedData.mappingInformationToPatchUp.add(
                mappingInformation.asRewriteFrameMappingInformation());
          }
        }
        seenMappedRanges.add(thisMappedRange);
        lastSeen = thisMappedRange;
      }
      return new MappedRangeResult(
          startMinifiedPosition,
          startOriginalPosition,
          index + seenMappedRanges.size(),
          lastSeen,
          seenMappedRanges);
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
      Int2ReferenceMap<List<MappedRange>> mappedRangesForPosition =
          getExistingMapping(existingRanges);
      List<MappedRange> newComposedRanges = new ArrayList<>();
      for (int i = 0; i < newRanges.size(); i++) {
        if (isInlineMappedRange(newRanges, i)) {
          throw new MappingComposeException(
              "Unexpected inline frame '" + existingRanges.get(i) + "' in composing map.");
        }
        MappedRange newRange = newRanges.get(i);
        if (newRange.originalRange == null) {
          List<MappedRange> existingMappedRanges = mappedRangesForPosition.get(NO_RANGE_FROM);
          assert existingMappedRanges.size() <= 1;
          if (existingMappedRanges.isEmpty()) {
            newComposedRanges.add(newRange);
          } else {
            MappedRange existingRange = existingMappedRanges.get(0);
            newComposedRanges.add(
                new MappedRange(
                    newRange.minifiedRange,
                    existingRange.signature,
                    existingRange.originalRange,
                    newRange.renamedName));
          }
        } else {
          // First check if the original range matches the existing minified range.
          List<MappedRange> existingMappedRanges =
              mappedRangesForPosition.get(newRange.originalRange.from);
          if (existingMappedRanges == null) {
            throw new MappingComposeException(
                "Unexpected missing original position for '" + newRange + "'.");
          }
          if (ListUtils.last(existingMappedRanges).minifiedRange.equals(newRange.originalRange)) {
            computeComposedMappedRange(
                newComposedRanges,
                newRange,
                existingMappedRanges,
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
                  mappedRangesForPosition.get(newRange.getOriginalLineNumber(position));
              if (existingMappedRangesForPosition == null) {
                throw new MappingComposeException(
                    "Unexpected missing original position for '" + newRange + "'.");
              }
              if (!ListUtils.last(existingMappedRanges)
                  .minifiedRange
                  .equals(ListUtils.last(existingMappedRangesForPosition).minifiedRange)) {
                computeComposedMappedRange(
                    newComposedRanges,
                    newRange,
                    existingMappedRanges,
                    lastStartingMinifiedFrom,
                    position - 1);
                lastStartingMinifiedFrom = position;
                existingMappedRanges = existingMappedRangesForPosition;
              }
            }
            computeComposedMappedRange(
                newComposedRanges,
                newRange,
                existingMappedRanges,
                lastStartingMinifiedFrom,
                newRange.minifiedRange.to);
          }
        }
      }
      return newComposedRanges;
    }

    /***
     * Builds a position to mapped ranges for mappings for looking up all mapped ranges for a given
     * position.
     */
    private Int2ReferenceMap<List<MappedRange>> getExistingMapping(
        List<MappedRange> existingRanges) {
      Int2ReferenceMap<List<MappedRange>> mappedRangesForPosition =
          new Int2ReferenceOpenHashMap<>();
      List<MappedRange> currentRangesForPosition = new ArrayList<>();
      for (int i = 0; i < existingRanges.size(); i++) {
        MappedRange mappedRange = existingRanges.get(i);
        currentRangesForPosition.add(mappedRange);
        if (!isInlineMappedRange(existingRanges, i)) {
          if (mappedRange.minifiedRange == null) {
            mappedRangesForPosition.put(NO_RANGE_FROM, currentRangesForPosition);
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
      return mappedRangesForPosition;
    }

    private void computeComposedMappedRange(
        List<MappedRange> newComposedRanges,
        MappedRange newMappedRange,
        List<MappedRange> existingMappedRanges,
        int lastStartingMinifiedFrom,
        int position) {
      Range existingRange = existingMappedRanges.get(0).minifiedRange;
      assert existingMappedRanges.stream().allMatch(x -> x.minifiedRange.equals(existingRange));
      Range newMinifiedRange = new Range(lastStartingMinifiedFrom, position);
      boolean copyOriginalRange = existingRange.equals(newMappedRange.originalRange);
      for (MappedRange existingMappedRange : existingMappedRanges) {
        Range existingOriginalRange = existingMappedRange.originalRange;
        Range newOriginalRange;
        if (copyOriginalRange || existingOriginalRange.span() == 1) {
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
        existingMappedRange
            .getAdditionalMappingInfo()
            .forEach(
                info -> computedRange.addMappingInformation(info, ConsumerUtils.emptyConsumer()));
        newComposedRanges.add(computedRange);
      }
    }

    private boolean isInlineMappedRange(List<MappedRange> mappedRanges, int index) {
      if (index + 1 >= mappedRanges.size()) {
        return false;
      }
      return mappedRanges
          .get(index)
          .minifiedRange
          .equals(mappedRanges.get(index + 1).minifiedRange);
    }

    public void write(ChainableStringConsumer consumer) {
      consumer.accept(originalName).accept(" -> ").accept(renamedName).accept(":\n");
      if (additionalMappingInfo != null) {
        additionalMappingInfo.forEach(
            info -> consumer.accept("# " + info.serialize()).accept("\n"));
      }
      writeFields(consumer);
      writeMethods(consumer);
    }

    private void writeFields(ChainableStringConsumer consumer) {
      ArrayList<MemberNaming> fieldNamings = new ArrayList<>();
      for (List<MemberNaming> namingsForKey : fieldMembers.values()) {
        fieldNamings.addAll(namingsForKey);
      }
      fieldNamings.sort(Comparator.comparing(MemberNaming::getOriginalName));
      fieldNamings.forEach(
          naming -> {
            consumer.accept(INDENTATION).accept(naming.toString()).accept("\n");
          });
    }

    private void writeMethods(ChainableStringConsumer consumer) {
      Map<String, List<MappedRange>> signatureToMappedRanges = new HashMap<>();
      methodMembers
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
  }
}
