// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import static com.android.tools.r8.dex.Constants.U4BIT_MAX;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;

/** Format45cc for instructions of size 4, with 5 registers and 2 constant pool index. */
public abstract class DexFormat45cc extends DexBase4Format {

  public final byte A;
  public final byte C;
  public final byte D;
  public final byte E;
  public final byte F;
  public final byte G;
  public DexMethod BBBB;
  public DexProto HHHH;

  private static void specify(StructuralSpecification<DexFormat45cc, ?> spec) {
    spec.withInt(i -> i.A)
        .withInt(i -> i.C)
        .withInt(i -> i.D)
        .withInt(i -> i.E)
        .withInt(i -> i.F)
        .withInt(i -> i.G)
        .withItem(i -> i.BBBB)
        .withItem(i -> i.HHHH);
  }

  DexFormat45cc(int high, BytecodeStream stream, DexMethod[] methodMap, DexProto[] protoMap) {
    super(stream);
    G = (byte) (high & 0xf);
    A = (byte) ((high >> 4) & 0xf);
    BBBB = methodMap[read16BitValue(stream)];
    int next = read8BitValue(stream);
    E = (byte) (next & 0xf);
    F = (byte) ((next >> 4) & 0xf);
    next = read8BitValue(stream);
    C = (byte) (next & 0xf);
    D = (byte) ((next >> 4) & 0xf);
    HHHH = protoMap[read16BitValue(stream)];
  }

  // A | G | op | [meth]@BBBB | F | E | D | C | [proto]@HHHH
  protected DexFormat45cc(int A, DexMethod BBBB, DexProto HHHH, int C, int D, int E, int F, int G) {
    assert 0 <= A && A <= U4BIT_MAX;
    assert 0 <= C && C <= U4BIT_MAX;
    assert 0 <= D && D <= U4BIT_MAX;
    assert 0 <= E && E <= U4BIT_MAX;
    assert 0 <= F && F <= U4BIT_MAX;
    assert 0 <= G && G <= U4BIT_MAX;
    this.A = (byte) A;
    this.BBBB = BBBB;
    this.HHHH = HHHH;
    this.C = (byte) C;
    this.D = (byte) D;
    this.E = (byte) E;
    this.F = (byte) F;
    this.G = (byte) G;
  }

  @Override
  public final int hashCode() {
    return ((HHHH.hashCode() << 28)
            | (BBBB.hashCode() << 24)
            | (A << 20)
            | (C << 16)
            | (D << 12)
            | (E << 8)
            | (F << 4)
            | G)
        ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat45cc) other, DexFormat45cc::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat45cc::specify);
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    MethodLookupResult lookup =
        appView
            .graphLens()
            .lookupMethod(getMethod(), context.getReference(), InvokeType.POLYMORPHIC);
    assert lookup.getType() == InvokeType.POLYMORPHIC;
    lookup.getReference().collectIndexedItems(appView, indexedItems);

    DexProto rewrittenProto = rewriter.rewriteProto(getProto());
    rewrittenProto.collectIndexedItems(appView, indexedItems);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    // The method is one of java.lang.MethodHandle.invoke/invokeExact.
    // Only the method signature (getProto()) is to be type rewritten.
    assert rewriter.dexItemFactory().polymorphicMethods.isPolymorphicInvoke(getMethod());
    assert getMethod()
        == graphLens
            .lookupMethod(getMethod(), context.getReference(), InvokeType.POLYMORPHIC)
            .getReference();
    DexProto rewrittenProto = rewriter.rewriteProto(getProto());
    writeFirst(A, G, dest);
    write16BitReference(getMethod(), dest, mapping);
    write16BitValue(combineBytes(makeByte(F, E), makeByte(D, C)), dest);
    write16BitReference(rewrittenProto, dest, mapping);
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterArguments(builder, ", ");
    builder.append(", ");
    // TODO(sgjesse): Add support for smali name mapping.
    builder.append(BBBB.toSmaliString());
    builder.append(", ");
    builder.append(HHHH.toSmaliString());
    return formatSmaliString(builder.toString());
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterArguments(builder, " ");
    builder.append(" ");
    builder.append(retracer.toDescriptor(BBBB));
    builder.append(", ");
    builder.append(retracer.toDescriptor(HHHH));
    return formatString(builder.toString());
  }

  private void appendRegisterArguments(StringBuilder builder, String separator) {
    builder.append("{ ");
    int[] values = new int[] {C, D, E, F, G};
    for (int i = 0; i < A; i++) {
      if (i != 0) {
        builder.append(separator);
      }
      builder.append("v").append(values[i]);
    }
    builder.append(" }");
  }

  @Override
  public DexMethod getMethod() {
    return BBBB;
  }

  @Override
  public DexProto getProto() {
    return HHHH;
  }
}
