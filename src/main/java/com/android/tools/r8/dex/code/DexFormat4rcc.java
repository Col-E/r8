// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.IndexedDexItem;
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
import java.util.function.BiPredicate;

/** Format4rcc for instructions of size 4, with a range of registers and 2 constant pool index. */
public abstract class DexFormat4rcc extends DexBase4Format {

  public final short AA;
  public final char CCCC;
  public DexMethod BBBB;
  public DexProto HHHH;

  private static void specify(StructuralSpecification<DexFormat4rcc, ?> spec) {
    spec.withInt(i -> i.AA).withInt(i -> i.CCCC).withItem(i -> i.BBBB).withItem(i -> i.HHHH);
  }

  // AA | op | [meth]@BBBB | CCCC | [proto]@HHHH
  DexFormat4rcc(int high, BytecodeStream stream, DexMethod[] methodMap, DexProto[] protoMap) {
    super(stream);
    this.AA = (short) high;
    this.BBBB = methodMap[read16BitValue(stream)];
    this.CCCC = read16BitValue(stream);
    this.HHHH = protoMap[read16BitValue(stream)];
  }

  DexFormat4rcc(int firstArgumentRegister, int argumentCount, DexMethod method, DexProto proto) {
    assert 0 <= firstArgumentRegister && firstArgumentRegister <= Constants.U16BIT_MAX;
    assert 0 <= argumentCount && argumentCount <= Constants.U8BIT_MAX;
    this.CCCC = (char) firstArgumentRegister;
    this.AA = (short) argumentCount;
    BBBB = method;
    HHHH = proto;
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
    writeFirst(AA, dest);
    write16BitReference(getMethod(), dest, mapping);
    write16BitValue(CCCC, dest);
    write16BitReference(rewrittenProto, dest, mapping);
  }

  @Override
  public final int hashCode() {
    return ((CCCC << 24) | (HHHH.hashCode() << 12) | (BBBB.hashCode() << 4) | AA)
        ^ getClass().hashCode();
  }

  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(this, (DexFormat4rcc) other, DexFormat4rcc::specify);
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visit(this, DexFormat4rcc::specify);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterRange(builder);
    builder.append(" ");
    builder.append(retracer.toDescriptor(BBBB));
    builder.append(retracer.toDescriptor(HHHH));
    return formatString(builder.toString());
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    StringBuilder builder = new StringBuilder();
    appendRegisterRange(builder);
    builder.append(", ");
    // TODO(sgjesse): Add support for smali name mapping.
    builder.append(BBBB.toSmaliString());
    builder.append(", ");
    builder.append(HHHH.toSmaliString());
    return formatSmaliString(builder.toString());
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
  public boolean equals(
      DexInstruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (other == null || (this.getClass() != other.getClass())) {
      return false;
    }
    DexFormat4rcc o = (DexFormat4rcc) other;
    return o.AA == AA
        && o.CCCC == CCCC
        && equality.test(BBBB, o.BBBB)
        && equality.test(HHHH, o.HHHH);
  }

  private void appendRegisterRange(StringBuilder builder) {
    int firstRegister = CCCC;
    builder.append("{ ");
    builder.append("v").append(firstRegister);
    if (AA != 1) {
      builder.append(" .. v").append(firstRegister + AA - 1);
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
