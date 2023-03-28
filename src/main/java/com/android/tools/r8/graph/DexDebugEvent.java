// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DebugBytecodeWriter;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Objects;

public abstract class DexDebugEvent extends DexItem implements StructuralItem<DexDebugEvent> {

  // Compare ID(s) for virtual debug events.
  private static final int DBG_SET_POSITION_FRAME_COMPARE_ID = Constants.DBG_LAST_SPECIAL + 1;

  public static final DexDebugEvent[] EMPTY_ARRAY = {};

  public void collectIndexedItems(AppView<?> appView, IndexedItemCollection collection) {
    // Empty by default.
  }

  @Override
  public void collectMixedSectionItems(MixedSectionCollection collection) {
    // Empty by default.
  }

  // Make sure all concrete subclasses implements toString, hashCode, and equals.
  @Override
  abstract public String toString();

  @Override
  abstract public int hashCode();

  @Override
  public final boolean equals(Object other) {
    return other instanceof DexDebugEvent && compareTo((DexDebugEvent) other) == 0;
  }

  abstract int getCompareToId();

  abstract int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor);

  abstract void internalAcceptHashing(HashingVisitor visitor);

  @Override
  public DexDebugEvent self() {
    return this;
  }

  @Override
  public StructuralMapping<DexDebugEvent> getStructuralMapping() {
    throw new Unreachable();
  }

  @Override
  public final int acceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
    int diff = visitor.visitInt(getCompareToId(), other.getCompareToId());
    return diff != 0 ? diff : internalAcceptCompareTo(other, visitor);
  }

  @Override
  public final void acceptHashing(HashingVisitor visitor) {
    visitor.visitInt(getCompareToId());
    internalAcceptHashing(visitor);
  }

  public final void writeOn(
      DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
    assert isWritableEvent();
    internalWriteOn(writer, mapping, graphLens);
  }

  boolean isWritableEvent() {
    return false;
  }

  void internalWriteOn(
      DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
    throw new Unreachable();
  }

  public abstract void accept(DexDebugEventVisitor visitor);

  public boolean isDefaultEvent() {
    return false;
  }

  public boolean isPositionFrame() {
    return false;
  }

  public boolean isAdvanceLine() {
    return false;
  }

  public SetPositionFrame asSetPositionFrame() {
    return null;
  }

  public Default asDefaultEvent() {
    return null;
  }

  public AdvanceLine asAdvanceLine() {
    return null;
  }

  public static class AdvancePC extends DexDebugEvent {

    public final int delta;

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(Constants.DBG_ADVANCE_PC);
      writer.putUleb128(delta);
    }

    public AdvancePC(int delta) {
      this.delta = delta;
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      assert delta >= 0;
      visitor.visit(this);
    }


    @Override
    public String toString() {
      return "ADVANCE_PC " + delta;
    }

    @Override
    public int hashCode() {
      return Constants.DBG_ADVANCE_PC
          + delta * 7;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_ADVANCE_PC;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return visitor.visitInt(delta, ((AdvancePC) other).delta);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(delta);
    }
  }

  public static class SetPrologueEnd extends DexDebugEvent {

    SetPrologueEnd() {
    }

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(Constants.DBG_SET_PROLOGUE_END);
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return "SET_PROLOGUE_END";
    }


    @Override
    public int hashCode() {
      return Constants.DBG_SET_PROLOGUE_END;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_SET_PROLOGUE_END;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      assert other instanceof SetPrologueEnd;
      return 0;
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      // Nothing to hash as the ID has already been hashed.
    }
  }


  public static class SetEpilogueBegin extends DexDebugEvent {

    SetEpilogueBegin() {
    }

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(Constants.DBG_SET_EPILOGUE_BEGIN);
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return "SET_EPILOGUE_BEGIN";
    }

    @Override
    public int hashCode() {
      return Constants.DBG_SET_EPILOGUE_BEGIN;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_SET_EPILOGUE_BEGIN;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      assert other instanceof SetEpilogueBegin;
      return 0;
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      // Nothing to hash as the ID has already been hashed.
    }
  }

  public static class AdvanceLine extends DexDebugEvent {

    final int delta;

    AdvanceLine(int delta) {
      this.delta = delta;
    }

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public boolean isAdvanceLine() {
      return true;
    }

    @Override
    public AdvanceLine asAdvanceLine() {
      return this;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(Constants.DBG_ADVANCE_LINE);
      writer.putSleb128(delta);
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return "ADVANCE_LINE " + delta;
    }

    @Override
    public int hashCode() {
      return Constants.DBG_ADVANCE_LINE
          + delta * 7;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_ADVANCE_LINE;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return visitor.visitInt(delta, ((AdvanceLine) other).delta);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(delta);
    }
  }

  static public class StartLocal extends DexDebugEvent {

    final int registerNum;
    final DexString name;
    final DexType type;
    final DexString signature;

    private static void spec(StructuralSpecification<StartLocal, ?> spec) {
      spec.withInt(e -> e.registerNum)
          .withItem(e -> e.name)
          .withItem(e -> e.type)
          .withNullableItem(e -> e.signature);
    }

    public StartLocal(
        int registerNum,
        DexString name,
        DexType type,
        DexString signature) {
      this.registerNum = registerNum;
      this.name = name;
      this.type = type;
      this.signature = signature;
    }

    public StartLocal(int registerNum, DebugLocalInfo local) {
      this(registerNum, local.name, local.type, local.signature);
    }

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(signature == null
          ? Constants.DBG_START_LOCAL
          : Constants.DBG_START_LOCAL_EXTENDED);
      writer.putUleb128(registerNum);
      writer.putString(name);
      writer.putType(graphLens.lookupType(type));
      if (signature != null) {
        writer.putString(signature);
      }
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection collection) {
      if (name != null) {
        name.collectIndexedItems(collection);
      }
      if (type != null) {
        DexType rewritten = appView.graphLens().lookupType(type);
        rewritten.collectIndexedItems(appView, collection);
      }
      if (signature != null) {
        signature.collectIndexedItems(collection);
      }
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return "START_LOCAL " + registerNum;
    }

    @Override
    public int hashCode() {
      return Constants.DBG_START_LOCAL
          + registerNum * 7
          + Objects.hashCode(name) * 13
          + Objects.hashCode(type) * 17
          + Objects.hashCode(signature) * 19;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_START_LOCAL;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return visitor.visit(this, (StartLocal) other, StartLocal::spec);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visit(this, StartLocal::spec);
    }
  }

  public static class EndLocal extends DexDebugEvent {

    final int registerNum;

    EndLocal(int registerNum) {
      this.registerNum = registerNum;
    }

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(Constants.DBG_END_LOCAL);
      writer.putUleb128(registerNum);
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return "END_LOCAL " + registerNum;
    }

    @Override
    public int hashCode() {
      return Constants.DBG_END_LOCAL
          + registerNum * 7;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_END_LOCAL;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return visitor.visitInt(registerNum, ((EndLocal) other).registerNum);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(registerNum);
    }
  }

  public static class RestartLocal extends DexDebugEvent {

    final int registerNum;

    RestartLocal(int registerNum) {
      this.registerNum = registerNum;
    }

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(Constants.DBG_RESTART_LOCAL);
      writer.putUleb128(registerNum);
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return "RESTART_LOCAL " + registerNum;
    }

    @Override
    public int hashCode() {
      return Constants.DBG_RESTART_LOCAL
          + registerNum * 7;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_RESTART_LOCAL;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return visitor.visitInt(registerNum, ((RestartLocal) other).registerNum);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(registerNum);
    }
  }

  /**
   * Unused/unsupported set-file event.
   *
   * <p>The set-file event is unused by all DEX VMs and incorrect on some older VMs. It is
   * represented in the type of events for completeness, but should never be emitted as part of
   * writing DEX code.
   */
  public static class SetFile extends DexDebugEvent {

    DexString fileName;

    SetFile(DexString fileName) {
      this.fileName = fileName;
    }

    @Override
    boolean isWritableEvent() {
      // Even though this is a DEX specified event it is unsupported and should never be written.
      return false;
    }

    @Override
    public void collectIndexedItems(AppView<?> appView, IndexedItemCollection collection) {
      fileName.collectIndexedItems(collection);
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return "SET_FILE " + fileName.toString();
    }

    @Override
    public int hashCode() {
      return Constants.DBG_SET_FILE
          + fileName.hashCode() * 7;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_SET_FILE;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return fileName.acceptCompareTo(((SetFile) other).fileName, visitor);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      fileName.acceptHashing(visitor);
    }
  }

  public static class SetPositionFrame extends DexDebugEvent {

    private final Position position;

    private static void specify(StructuralSpecification<SetPositionFrame, ?> spec) {
      spec.withNullableItem(e -> e.position);
    }

    SetPositionFrame(Position position) {
      this.position = position;
    }

    public Position getPosition() {
      return position;
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return String.format("SET_POSITION_FRAME %s", position);
    }

    @Override
    public int hashCode() {
      return 31 * Objects.hashCode(position);
    }

    @Override
    int getCompareToId() {
      return DBG_SET_POSITION_FRAME_COMPARE_ID;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return visitor.visit(this, (SetPositionFrame) other, SetPositionFrame::specify);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visit(this, SetPositionFrame::specify);
    }

    @Override
    public boolean isPositionFrame() {
      return true;
    }

    @Override
    public SetPositionFrame asSetPositionFrame() {
      return this;
    }
  }

  public static class Default extends DexDebugEvent {

    final int value;

    Default(int value) {
      assert (value >= Constants.DBG_FIRST_SPECIAL) && (value <= Constants.DBG_LAST_SPECIAL);
      this.value = value;
    }

    // Use DexDebugEventBuilder.addDefaultEventWithAdvancePcIfNecessary instead.
    static int computeSpecialOpcode(int lineDelta, int pcDelta) {
      return Constants.DBG_FIRST_SPECIAL
          + (lineDelta - Constants.DBG_LINE_BASE)
          + Constants.DBG_LINE_RANGE * pcDelta;
    }

    // Use DexDebugEventBuilder.addDefaultEventWithAdvancePcIfNecessary instead.
    public static Default create(int lineDelta, int pcDelta) {
      return new Default(computeSpecialOpcode(lineDelta, pcDelta));
    }

    @Override
    public boolean isDefaultEvent() {
      return true;
    }

    @Override
    public Default asDefaultEvent() {
      return this;
    }

    @Override
    boolean isWritableEvent() {
      return true;
    }

    @Override
    public void internalWriteOn(
        DebugBytecodeWriter writer, ObjectToOffsetMapping mapping, GraphLens graphLens) {
      writer.putByte(value);
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    public int getPCDelta() {
      int adjustedOpcode = value - Constants.DBG_FIRST_SPECIAL;
      return adjustedOpcode / Constants.DBG_LINE_RANGE;
    }

    public int getLineDelta() {
      int adjustedOpcode = value - Constants.DBG_FIRST_SPECIAL;
      return Constants.DBG_LINE_BASE + (adjustedOpcode % Constants.DBG_LINE_RANGE);
    }

    @Override
    public String toString() {
      return String.format("DEFAULT %d (dpc %d, dline %d)", value, getPCDelta(), getLineDelta());
    }

    @Override
    public int hashCode() {
      return Constants.DBG_FIRST_SPECIAL
          + value * 7;
    }

    @Override
    int getCompareToId() {
      return Constants.DBG_FIRST_SPECIAL;
    }

    @Override
    int internalAcceptCompareTo(DexDebugEvent other, CompareToVisitor visitor) {
      return visitor.visitInt(value, ((Default) other).value);
    }

    @Override
    void internalAcceptHashing(HashingVisitor visitor) {
      visitor.visitInt(value);
    }
  }
}
