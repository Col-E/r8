// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;

public class SyntheticMarker {

  private static final String SYNTHETIC_MARKER_ATTRIBUTE_TYPE_NAME = "R8SynthesizedClass";

  public static Attribute getMarkerAttributePrototype() {
    return MarkerAttribute.PROTOTYPE;
  }

  public static void writeMarkerAttribute(ClassWriter writer, SyntheticKind kind) {
    writer.visitAttribute(new MarkerAttribute(kind));
  }

  public static SyntheticMarker readMarkerAttribute(Attribute attribute) {
    if (attribute instanceof MarkerAttribute) {
      MarkerAttribute marker = (MarkerAttribute) attribute;
      return new SyntheticMarker(marker.kind, null);
    }
    return null;
  }

  private static class MarkerAttribute extends Attribute {

    private static final MarkerAttribute PROTOTYPE = new MarkerAttribute(null);

    private SyntheticKind kind;

    public MarkerAttribute(SyntheticKind kind) {
      super(SYNTHETIC_MARKER_ATTRIBUTE_TYPE_NAME);
      this.kind = kind;
    }

    @Override
    protected Attribute read(
        ClassReader classReader,
        int offset,
        int length,
        char[] charBuffer,
        int codeAttributeOffset,
        Label[] labels) {
      short id = classReader.readShort(offset);
      assert id >= 0;
      SyntheticKind kind = SyntheticKind.fromId(id);
      return new MarkerAttribute(kind);
    }

    @Override
    protected ByteVector write(
        ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
      ByteVector byteVector = new ByteVector();
      assert 0 <= kind.id && kind.id <= Short.MAX_VALUE;
      byteVector.putShort(kind.id);
      return byteVector;
    }
  }

  public static void addMarkerToClass(
      DexProgramClass clazz, SyntheticKind kind, InternalOptions options) {
    // TODO(b/158159959): Consider moving this to the dex writer similar to the CF case.
    assert !options.isGeneratingClassFiles();
    clazz.setAnnotations(
        clazz
            .annotations()
            .getWithAddedOrReplaced(
                DexAnnotation.createAnnotationSynthesizedClass(kind, options.itemFactory)));
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
            clazz.annotations(), appView.dexItemFactory());
    return marker;
  }

  private static SyntheticMarker internalStripMarkerFromClass(
      DexProgramClass clazz, AppView<?> appView) {
    ClassAccessFlags flags = clazz.accessFlags;
    if (clazz.superType != appView.dexItemFactory().objectType) {
      return NO_MARKER;
    }
    if (!flags.isSynthetic() || flags.isAbstract() || flags.isEnum()) {
      return NO_MARKER;
    }
    SyntheticKind kind =
        DexAnnotation.getSynthesizedClassAnnotationInfo(
            clazz.annotations(), appView.dexItemFactory());
    if (kind == null) {
      return NO_MARKER;
    }
    assert clazz.annotations().size() == 1;
    if (kind.isSingleSyntheticMethod) {
      if (!clazz.interfaces.isEmpty()) {
        return NO_MARKER;
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (!SyntheticMethodBuilder.isValidSyntheticMethod(method)) {
          return NO_MARKER;
        }
      }
    }
    clazz.setAnnotations(DexAnnotationSet.empty());
    DexType context = getSyntheticContextType(clazz.type, kind, appView.dexItemFactory());
    return new SyntheticMarker(
        kind, SynthesizingContext.fromSyntheticInputClass(clazz, context, appView));
  }

  private static DexType getSyntheticContextType(
      DexType type, SyntheticKind kind, DexItemFactory factory) {
    String prefix = SyntheticNaming.getPrefixForExternalSyntheticType(kind, type);
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
    return kind != null && kind.isSingleSyntheticMethod;
  }

  public boolean isSyntheticClass() {
    return kind != null && !kind.isSingleSyntheticMethod;
  }

  public SyntheticKind getKind() {
    return kind;
  }

  public SynthesizingContext getContext() {
    return context;
  }
}
