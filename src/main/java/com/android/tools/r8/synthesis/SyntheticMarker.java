// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.SynthesizedAnnotationClassInfo;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.charset.StandardCharsets;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

public class SyntheticMarker {

  private static final String SYNTHETIC_MARKER_ATTRIBUTE_TYPE_NAME =
      "com.android.tools.r8.SynthesizedClassV2";

  public static Attribute getMarkerAttributePrototype(SyntheticNaming syntheticNaming) {
    return new MarkerAttribute(null, null, syntheticNaming);
  }

  public static void writeMarkerAttribute(
      ClassWriter writer, SyntheticKind kind, SyntheticItems syntheticItems) {
    SyntheticNaming naming = syntheticItems.getNaming();
    writer.visitAttribute(new MarkerAttribute(kind, naming.getVersionHash(), naming));
  }

  public static SyntheticMarker readMarkerAttribute(Attribute attribute) {
    if (attribute instanceof MarkerAttribute) {
      MarkerAttribute marker = (MarkerAttribute) attribute;
      if (marker.versionHash.equals(marker.syntheticNaming.getVersionHash())) {
        return new SyntheticMarker(marker.kind, null);
      }
    }
    return null;
  }

  /**
   * CF attribute for marking synthetic classes.
   *
   * <p>The attribute name is defined by {@code SYNTHETIC_MARKER_ATTRIBUTE_TYPE_NAME}. The format of
   * the attribute payload is
   *
   * <pre>
   *   u2 syntheticKindId
   *   u2 versionHashLength
   *   u1[versionHashLength] versionHashBytes
   * </pre>
   */
  private static class MarkerAttribute extends Attribute {

    private final SyntheticKind kind;
    private final String versionHash;
    private final SyntheticNaming syntheticNaming;

    public MarkerAttribute(
        SyntheticKind kind, String versionHash, SyntheticNaming syntheticNaming) {
      super(SYNTHETIC_MARKER_ATTRIBUTE_TYPE_NAME);
      this.kind = kind;
      this.versionHash = versionHash;
      this.syntheticNaming = syntheticNaming;
    }

    @Override
    protected Attribute read(
        ClassReader classReader,
        int offset,
        int length,
        char[] charBuffer,
        int codeAttributeOffset,
        Label[] labels) {
      short syntheticKindId = classReader.readShort(offset);
      offset += 2;
      short versionHashLength = classReader.readShort(offset);
      offset += 2;
      byte[] versionHashBytes = new byte[versionHashLength];
      for (int i = 0; i < versionHashLength; i++) {
        versionHashBytes[i] = (byte) classReader.readByte(offset++);
      }
      assert syntheticKindId >= 0;
      SyntheticKind kind = syntheticNaming.fromId(syntheticKindId);
      String versionHash = new String(versionHashBytes, StandardCharsets.UTF_8);
      return new MarkerAttribute(kind, versionHash, syntheticNaming);
    }

    @Override
    protected ByteVector write(
        ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
      assert 0 <= kind.getId() && kind.getId() <= Short.MAX_VALUE;
      ByteVector byteVector = new ByteVector();
      byteVector.putShort(kind.getId());
      byte[] versionHashBytes = versionHash.getBytes(StandardCharsets.UTF_8);
      byteVector.putShort(versionHashBytes.length);
      byteVector.putByteArray(versionHashBytes, 0, versionHashBytes.length);
      return byteVector;
    }
  }

  public static void addMarkerToClass(
      DexProgramClass clazz, SyntheticKind kind, AppView<?> appView) {
    // TODO(b/158159959): Consider moving this to the dex writer similar to the CF case.
    assert !appView.options().isGeneratingClassFiles();
    assert !isDefinitelyNotSyntheticProgramClass(clazz);
    clazz.setAnnotations(
        clazz
            .annotations()
            .getWithAddedOrReplaced(
                DexAnnotation.createAnnotationSynthesizedClass(
                    kind,
                    appView.options().itemFactory,
                    AndroidApiLevelUtils.getApiReferenceLevelForMerging(
                        appView.apiLevelCompute(), clazz))));
  }

  public static SyntheticMarker stripMarkerFromClass(DexProgramClass clazz, AppView<?> appView) {
    if (clazz.originatesFromClassResource()) {
      SyntheticMarker marker = clazz.stripSyntheticInputMarker();
      if (marker == null) {
        return NO_MARKER;
      }
      assert marker.getContext() == null;
      DexType contextType =
          getSyntheticContextType(clazz.type, marker.kind, appView.dexItemFactory());
      SynthesizingContext context =
          SynthesizingContext.fromSyntheticInputClass(clazz, contextType, appView);
      return new SyntheticMarker(marker.kind, context);
    }
    SyntheticMarker marker = internalStripMarkerFromClass(clazz, appView);
    assert marker != NO_MARKER
        || !DexAnnotation.hasSynthesizedClassAnnotation(
            clazz.annotations(),
            appView.dexItemFactory(),
            appView.getSyntheticItems(),
            appView.apiLevelCompute());
    return marker;
  }

  private static SyntheticMarker internalStripMarkerFromClass(
      DexProgramClass clazz, AppView<?> appView) {
    if (isDefinitelyNotSyntheticProgramClass(clazz)) {
      return NO_MARKER;
    }
    SynthesizedAnnotationClassInfo synthesizedInfo =
        DexAnnotation.getSynthesizedClassAnnotationInfo(
            clazz.annotations(),
            appView.dexItemFactory(),
            appView.getSyntheticItems(),
            appView.apiLevelCompute());
    if (synthesizedInfo == null) {
      return NO_MARKER;
    }
    assert clazz.annotations().size() == 1;
    SyntheticKind kind = synthesizedInfo.getSyntheticKind();
    if (kind.isSingleSyntheticMethod()) {
      if (!clazz.interfaces.isEmpty()) {
        return NO_MARKER;
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (!SyntheticMethodBuilder.isValidSingleSyntheticMethod(method)) {
          return NO_MARKER;
        }
      }
    }
    clazz.setAnnotations(DexAnnotationSet.empty());
    clazz.forEachMethod(method -> method.setApiLevelForCode(synthesizedInfo.getComputedApiLevel()));
    DexType context = getSyntheticContextType(clazz.type, kind, appView.dexItemFactory());
    return new SyntheticMarker(
        kind, SynthesizingContext.fromSyntheticInputClass(clazz, context, appView));
  }

  // Filters out definitely not synthetic classes to avoid expensive computations on all classes.
  public static boolean isDefinitelyNotSyntheticProgramClass(DexProgramClass clazz) {
    ClassAccessFlags flags = clazz.accessFlags;
    return !flags.isSynthetic() || flags.isEnum();
  }

  private static DexType getSyntheticContextType(
      DexType type, SyntheticKind kind, DexItemFactory factory) {
    if (kind.isGlobal()) {
      return type;
    }
    String prefix = SyntheticNaming.getOuterContextFromExternalSyntheticType(kind, type);
    return factory.createType(DescriptorUtils.getDescriptorFromClassBinaryName(prefix));
  }

  private static final SyntheticMarker NO_MARKER = new SyntheticMarker(null, null);

  private final SyntheticKind kind;
  private final SynthesizingContext context;

  public SyntheticMarker(SyntheticKind kind, SynthesizingContext context) {
    this.kind = kind;
    this.context = context;
  }

  public boolean isSyntheticMethods() {
    return kind != null && kind.isSingleSyntheticMethod();
  }

  public boolean isSyntheticClass() {
    return kind != null && !kind.isSingleSyntheticMethod();
  }

  public SyntheticKind getKind() {
    return kind;
  }

  public SynthesizingContext getContext() {
    return context;
  }
}
