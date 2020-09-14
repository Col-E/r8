// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DebugBytecodeWriter;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.ir.code.Position;
import java.util.Comparator;
import java.util.Objects;

public abstract class DexDebugEvent extends DexItem implements Comparable<DexDebugEvent> {

  // Compare ID(s) for virtual debug events.
  private static final int DBG_SET_INLINE_FRAME_COMPARE_ID = Constants.DBG_LAST_SPECIAL + 1;

  public static final DexDebugEvent[] EMPTY_ARRAY = {};

  public void collectIndexedItems(IndexedItemCollection collection, GraphLens graphLens) {
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

  abstract int internalCompareTo(DexDebugEvent other);

  @Override
  public final int compareTo(DexDebugEvent other) {
    if (this == other) {
      return 0;
    }
    int diff = Integer.compare(getCompareToId(), other.getCompareToId());
    return diff != 0 ? diff : internalCompareTo(other);
  }

  public abstract void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping);

  public abstract void accept(DexDebugEventVisitor visitor);

  public boolean isSetInlineFrame() {
    return false;
  }

  public SetInlineFrame asSetInlineFrame() {
    return null;
  }

  public static class AdvancePC extends DexDebugEvent {

    public final int delta;

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
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
    int internalCompareTo(DexDebugEvent other) {
      return Integer.compare(delta, ((AdvancePC) other).delta);
    }
  }

  public static class SetPrologueEnd extends DexDebugEvent {

    SetPrologueEnd() {
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
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
    int internalCompareTo(DexDebugEvent other) {
      assert other instanceof SetPrologueEnd;
      return 0;
    }
  }


  public static class SetEpilogueBegin extends DexDebugEvent {

    SetEpilogueBegin() {
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
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
    int internalCompareTo(DexDebugEvent other) {
      assert other instanceof SetEpilogueBegin;
      return 0;
    }
  }

  public static class AdvanceLine extends DexDebugEvent {

    final int delta;

    AdvanceLine(int delta) {
      this.delta = delta;
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
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
    int internalCompareTo(DexDebugEvent other) {
      return Integer.compare(delta, ((AdvanceLine) other).delta);
    }
  }

  static public class StartLocal extends DexDebugEvent {

    final int registerNum;
    final DexString name;
    final DexType type;
    final DexString signature;

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
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
      writer.putByte(signature == null
          ? Constants.DBG_START_LOCAL
          : Constants.DBG_START_LOCAL_EXTENDED);
      writer.putUleb128(registerNum);
      writer.putString(name);
      writer.putType(type);
      if (signature != null) {
        writer.putString(signature);
      }
    }

    @Override
    public void collectIndexedItems(IndexedItemCollection collection, GraphLens graphLens) {
      if (name != null) {
        name.collectIndexedItems(collection);
      }
      if (type != null) {
        DexType rewritten = graphLens.lookupType(type);
        rewritten.collectIndexedItems(collection);
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
    int internalCompareTo(DexDebugEvent other) {
      return Comparator.comparingInt((StartLocal e) -> e.registerNum)
          .thenComparing(e -> e.name, DexString::slowCompareTo)
          .thenComparing(e -> e.type, DexType::slowCompareTo)
          .thenComparing(e -> e.signature, Comparator.nullsFirst(DexString::slowCompareTo))
          .compare(this, (StartLocal) other);
    }
  }

  public static class EndLocal extends DexDebugEvent {

    final int registerNum;

    EndLocal(int registerNum) {
      this.registerNum = registerNum;
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
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
    int internalCompareTo(DexDebugEvent other) {
      return Integer.compare(registerNum, ((EndLocal) other).registerNum);
    }
  }

  public static class RestartLocal extends DexDebugEvent {

    final int registerNum;

    RestartLocal(int registerNum) {
      this.registerNum = registerNum;
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
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
    int internalCompareTo(DexDebugEvent other) {
      return Integer.compare(registerNum, ((RestartLocal) other).registerNum);
    }
  }

  public static class SetFile extends DexDebugEvent {

    DexString fileName;

    SetFile(DexString fileName) {
      this.fileName = fileName;
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
      writer.putByte(Constants.DBG_SET_FILE);
      writer.putString(fileName);
    }

    @Override
    public void collectIndexedItems(IndexedItemCollection collection, GraphLens graphLens) {
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
    int internalCompareTo(DexDebugEvent other) {
      return fileName.slowCompareTo(((SetFile) other).fileName);
    }
  }

  public static class SetInlineFrame extends DexDebugEvent {

    final DexMethod callee;
    final Position caller;

    SetInlineFrame(DexMethod callee, Position caller) {
      assert callee != null;
      this.callee = callee;
      this.caller = caller;
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
      // CallerPosition will not be written.
    }

    @Override
    public void accept(DexDebugEventVisitor visitor) {
      visitor.visit(this);
    }

    @Override
    public String toString() {
      return String.format("SET_INLINE_FRAME %s %s", callee, caller);
    }

    @Override
    public int hashCode() {
      return 31 * callee.hashCode() + Objects.hashCode(caller);
    }

    @Override
    int getCompareToId() {
      return DBG_SET_INLINE_FRAME_COMPARE_ID;
    }

    @Override
    int internalCompareTo(DexDebugEvent other) {
      return Comparator.comparing((SetInlineFrame e) -> e.callee, DexMethod::slowCompareTo)
          .thenComparing(e -> e.caller, Comparator.nullsFirst(Position::compareTo))
          .compare(this, (SetInlineFrame) other);
    }

    @Override
    public boolean isSetInlineFrame() {
      return true;
    }

    @Override
    public SetInlineFrame asSetInlineFrame() {
      return this;
    }

    public boolean hasOuterPosition(DexMethod method) {
      return (caller == null && callee == method)
          || (caller != null && caller.getOutermostCaller().method == method);
    }
  }

  public static class Default extends DexDebugEvent {

    final int value;

    Default(int value) {
      assert (value >= Constants.DBG_FIRST_SPECIAL) && (value <= Constants.DBG_LAST_SPECIAL);
      this.value = value;
    }

    @Override
    public void writeOn(DebugBytecodeWriter writer, ObjectToOffsetMapping mapping) {
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
    int internalCompareTo(DexDebugEvent other) {
      return Integer.compare(value, ((Default) other).value);
    }
  }
}
