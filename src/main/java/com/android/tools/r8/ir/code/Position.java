// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.Int2StructuralItemArrayMap;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.HashCodeVisitor;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.function.Predicate;

public abstract class Position implements StructuralItem<Position> {

  // Compare ID(s) for positions.
  private static final int SOURCE_POSITION_COMPARE_ID = 1;
  private static final int SYNTHETIC_POSITION_COMPARE_ID = 2;
  private static final int OUTLINE_POSITION_COMPARE_ID = 3;
  private static final int OUTLINE_CALLER_POSITION_COMPARE_ID = 4;

  protected final int line;
  protected final DexMethod method;

  // If there's no inlining, callerPosition is null.
  //
  // For an inlined instruction its Position contains the inlinee's line and method and
  // callerPosition is the position of the invoke instruction in the caller.
  protected final Position callerPosition;

  private final boolean removeInnerFramesIfThrowingNpe;

  private Position(
      int line, DexMethod method, Position callerPosition, boolean removeInnerFramesIfThrowingNpe) {
    this.line = line;
    this.method = method;
    this.callerPosition = callerPosition;
    this.removeInnerFramesIfThrowingNpe = removeInnerFramesIfThrowingNpe;
  }

  public boolean isSyntheticPosition() {
    return false;
  }

  public boolean isAdditionalMappingInfoPosition() {
    return false;
  }

  public boolean isRemoveInnerFramesIfThrowingNpe() {
    return removeInnerFramesIfThrowingNpe;
  }

  public boolean isOutline() {
    return false;
  }

  public DexMethod getOutlineCallee() {
    return null;
  }

  public Int2StructuralItemArrayMap<Position> getOutlinePositions() {
    return null;
  }

  public boolean hasCallerPosition() {
    return callerPosition != null;
  }

  public Position getCallerPosition() {
    return callerPosition;
  }

  public int getLine() {
    return line;
  }

  public DexMethod getMethod() {
    return method;
  }

  public static Position none() {
    return SourcePosition.NO_POSITION;
  }

  public boolean hasFile() {
    return false;
  }

  public DexString getFile() {
    return null;
  }

  @Override
  public Position self() {
    return this;
  }

  // Unique id to determine the ordering of positions
  public abstract int getCompareToId();

  @Override
  public abstract StructuralMapping<Position> getStructuralMapping();

  private static void specifyBasePosition(StructuralSpecification<Position, ?> spec) {
    spec.withInt(Position::getCompareToId)
        .withInt(Position::getLine)
        .withNullableItem(Position::getMethod)
        .withNullableItem(Position::getCallerPosition)
        .withBool(Position::isRemoveInnerFramesIfThrowingNpe);
  }

  public static Position syntheticNone() {
    return SyntheticPosition.NO_POSITION_SYNTHETIC;
  }

  public static Position getPositionForInlining(
      AppView<?> appView, InvokeMethod invoke, ProgramMethod context) {
    Position position = invoke.getPosition();
    if (position.method == null) {
      assert position.isNone();
      position = SourcePosition.builder().setMethod(context.getReference()).build();
    }
    assert position.getOutermostCaller().method
        == appView.graphLens().getOriginalMethodSignature(context.getReference());
    return position;
  }

  public boolean isNone() {
    return line == -1;
  }

  public boolean isSyntheticNone() {
    return this == syntheticNone();
  }

  public boolean isSome() {
    return !isNone();
  }

  // Follow the linked list of callerPositions and return the last.
  // Return this if no inliner.
  public Position getOutermostCaller() {
    Position lastPosition = this;
    while (lastPosition.callerPosition != null) {
      lastPosition = lastPosition.callerPosition;
    }
    return lastPosition;
  }

  public Position getOutermostCallerMatchingOrElse(
      Predicate<Position> predicate, Position defaultValue) {
    return getOutermostCallerMatchingOrElse(predicate, defaultValue, false);
  }

  private Position getOutermostCallerMatchingOrElse(
      Predicate<Position> predicate, Position defaultValue, boolean isCallerPosition) {
    if (hasCallerPosition()) {
      Position position =
          getCallerPosition().getOutermostCallerMatchingOrElse(predicate, defaultValue, true);
      if (position != null) {
        return position;
      }
    }
    if (isCallerPosition && predicate.test(this)) {
      return this;
    }
    return defaultValue;
  }

  public Position withOutermostCallerPosition(Position newOutermostCallerPosition) {
    return builderWithCopy()
        .setCallerPosition(
            hasCallerPosition()
                ? getCallerPosition().withOutermostCallerPosition(newOutermostCallerPosition)
                : newOutermostCallerPosition)
        .build();
  }

  public Position replaceOutermostCallerPosition(Position newOutermostCallerPosition) {
    if (!hasCallerPosition()) {
      return newOutermostCallerPosition;
    }
    return builderWithCopy()
        .setCallerPosition(
            getCallerPosition().replaceOutermostCallerPosition(newOutermostCallerPosition))
        .build();
  }

  @Override
  public final boolean equals(Object other) {
    return Equatable.equalsImpl(this, other);
  }

  @Override
  public final int hashCode() {
    return HashCodeVisitor.run(this);
  }

  private String toString(boolean forceMethod) {
    if (isNone()) {
      return "--";
    }
    StringBuilder builder = new StringBuilder();
    if (hasFile()) {
      builder.append(getFile()).append(":");
    }
    builder.append("#").append(line);
    if (method != null && (forceMethod || callerPosition != null)) {
      builder.append(":").append(method.name);
    }
    if (callerPosition != null) {
      Position caller = callerPosition;
      while (caller != null) {
        builder.append(";").append(caller.line).append(":").append(caller.method.name);
        caller = caller.callerPosition;
      }
    }
    return builder.toString();
  }

  @Override
  public String toString() {
    return toString(false);
  }

  public abstract PositionBuilder<?, ?> builderWithCopy();

  public abstract static class PositionBuilder<
      P extends Position, B extends PositionBuilder<P, B>> {

    protected int line = -1;
    protected DexMethod method;
    protected Position callerPosition;
    protected boolean removeInnerFramesIfThrowingNpe;

    protected boolean noCheckOfPosition;
    protected boolean noCheckOfMethod;

    abstract B self();

    public B setLine(int line) {
      this.line = line;
      return self();
    }

    public boolean hasLine() {
      return line > -1;
    }

    public B setMethod(DexMethod method) {
      this.method = method;
      return self();
    }

    public B setCallerPosition(Position callerPosition) {
      this.callerPosition = callerPosition;
      return self();
    }

    public B setRemoveInnerFramesIfThrowingNpe(boolean removeInnerFramesIfThrowingNpe) {
      this.removeInnerFramesIfThrowingNpe = removeInnerFramesIfThrowingNpe;
      return self();
    }

    public B disableLineCheck() {
      noCheckOfPosition = true;
      return self();
    }

    public B disableMethodCheck() {
      noCheckOfMethod = true;
      return self();
    }

    public abstract P build();
  }

  public static class SourcePosition extends Position {

    // A no-position marker. Not having a position means the position is implicitly defined by the
    // context, e.g., the marker does not materialize anything concrete.
    private static final SourcePosition NO_POSITION =
        new SourcePosition(-1, null, null, false, null);

    public final DexString file;

    private static void specify(StructuralSpecification<Position, ?> spec) {
      spec.withSpec(Position::specifyBasePosition).withNullableItem(Position::getFile);
    }

    private SourcePosition(
        int line,
        DexMethod method,
        Position callerPosition,
        boolean removeInnerFramesIfThrowingNpe,
        DexString file) {
      super(line, method, callerPosition, removeInnerFramesIfThrowingNpe);
      this.file = file;
      assert callerPosition == null || callerPosition.method != null;
    }

    @Override
    public boolean hasFile() {
      return file != null;
    }

    @Override
    public DexString getFile() {
      return file;
    }

    @Override
    public int getCompareToId() {
      return SOURCE_POSITION_COMPARE_ID;
    }

    @Override
    public PositionBuilder<?, ?> builderWithCopy() {
      return builder()
          .setLine(line)
          .setFile(file)
          .setMethod(method)
          .setCallerPosition(callerPosition);
    }

    @Override
    public StructuralMapping<Position> getStructuralMapping() {
      return SourcePosition::specify;
    }

    public static SourcePositionBuilder builder() {
      return new SourcePositionBuilder();
    }

    public static class SourcePositionBuilder
        extends PositionBuilder<SourcePosition, SourcePositionBuilder> {

      private DexString file;

      @Override
      SourcePositionBuilder self() {
        return this;
      }

      public SourcePositionBuilder setFile(DexString file) {
        this.file = file;
        return this;
      }

      @Override
      public SourcePosition build() {
        assert noCheckOfPosition || line >= 0;
        assert noCheckOfMethod || method != null;
        return new SourcePosition(
            line, method, callerPosition, removeInnerFramesIfThrowingNpe, file);
      }
    }
  }

  public static class SyntheticPosition extends Position {

    // A synthetic marker position that should never materialize.
    // This is used specifically to mark exceptional exit blocks from synchronized methods in
    // release.
    private static final Position NO_POSITION_SYNTHETIC =
        new SyntheticPosition(-1, null, null, false);

    private SyntheticPosition(
        int line,
        DexMethod method,
        Position callerPosition,
        boolean removeInnerFramesIfThrowingNpe) {
      super(line, method, callerPosition, removeInnerFramesIfThrowingNpe);
    }

    @Override
    public boolean isSyntheticPosition() {
      return true;
    }

    @Override
    public int getCompareToId() {
      return SYNTHETIC_POSITION_COMPARE_ID;
    }

    @Override
    public PositionBuilder<?, ?> builderWithCopy() {
      return builder().setLine(line).setMethod(method).setCallerPosition(callerPosition);
    }

    @Override
    public StructuralMapping<Position> getStructuralMapping() {
      return Position::specifyBasePosition;
    }

    public static SyntheticPositionBuilder builder() {
      return new SyntheticPositionBuilder();
    }

    public static class SyntheticPositionBuilder
        extends PositionBuilder<SyntheticPosition, SyntheticPositionBuilder> {

      private SyntheticPositionBuilder() {}

      @Override
      SyntheticPositionBuilder self() {
        return this;
      }

      @Override
      public SyntheticPosition build() {
        assert noCheckOfPosition || line >= 0;
        assert noCheckOfMethod || method != null;
        return new SyntheticPosition(line, method, callerPosition, removeInnerFramesIfThrowingNpe);
      }
    }
  }

  public static class OutlinePosition extends Position {

    private OutlinePosition(
        int line,
        DexMethod method,
        Position callerPosition,
        boolean removeInnerFramesIfThrowingNpe) {
      super(line, method, callerPosition, removeInnerFramesIfThrowingNpe);
    }

    @Override
    public boolean isOutline() {
      return true;
    }

    @Override
    public int getCompareToId() {
      return OUTLINE_POSITION_COMPARE_ID;
    }

    @Override
    public PositionBuilder<?, ?> builderWithCopy() {
      return builder().setLine(line).setMethod(method).setCallerPosition(callerPosition);
    }

    @Override
    public StructuralMapping<Position> getStructuralMapping() {
      return Position::specifyBasePosition;
    }

    public static OutlinePositionBuilder builder() {
      return new OutlinePositionBuilder();
    }

    public static class OutlinePositionBuilder
        extends PositionBuilder<OutlinePosition, OutlinePositionBuilder> {

      private OutlinePositionBuilder() {}

      @Override
      OutlinePositionBuilder self() {
        return this;
      }

      @Override
      public OutlinePosition build() {
        return new OutlinePosition(line, method, callerPosition, removeInnerFramesIfThrowingNpe);
      }
    }
  }

  public static class OutlineCallerPosition extends Position {

    private final Int2StructuralItemArrayMap<Position> outlinePositions;
    private final DexMethod outlineCallee;
    private final boolean isOutline;

    public static void specify(StructuralSpecification<Position, ?> spec) {
      spec.withSpec(Position::specifyBasePosition)
          .withBool(Position::isOutline)
          .withItem(Position::getOutlineCallee)
          .withItem(Position::getOutlinePositions);
    }

    private OutlineCallerPosition(
        int line,
        DexMethod method,
        Position callerPosition,
        boolean removeInnerFramesIfThrowingNpe,
        Int2StructuralItemArrayMap<Position> outlinePositions,
        DexMethod outlineCallee,
        boolean isOutline) {
      super(line, method, callerPosition, removeInnerFramesIfThrowingNpe);
      this.outlinePositions = outlinePositions;
      this.outlineCallee = outlineCallee;
      this.isOutline = isOutline;
    }

    @Override
    public boolean isNone() {
      return false;
    }

    @Override
    public int getCompareToId() {
      return OUTLINE_CALLER_POSITION_COMPARE_ID;
    }

    @Override
    public PositionBuilder<?, ?> builderWithCopy() {
      OutlineCallerPositionBuilder outlineCallerPositionBuilder =
          builder()
              .setLine(line)
              .setMethod(method)
              .setCallerPosition(callerPosition)
              .setOutlineCallee(outlineCallee)
              .setIsOutline(isOutline);
      outlinePositions.forEach(outlineCallerPositionBuilder::addOutlinePosition);
      return outlineCallerPositionBuilder;
    }

    @Override
    public boolean isOutline() {
      return isOutline;
    }

    @Override
    public DexMethod getOutlineCallee() {
      return outlineCallee;
    }

    @Override
    public Int2StructuralItemArrayMap<Position> getOutlinePositions() {
      return outlinePositions;
    }

    @Override
    public StructuralMapping<Position> getStructuralMapping() {
      return OutlineCallerPosition::specify;
    }

    public static OutlineCallerPositionBuilder builder() {
      return new OutlineCallerPositionBuilder();
    }

    public static class OutlineCallerPositionBuilder
        extends PositionBuilder<OutlineCallerPosition, OutlineCallerPositionBuilder> {

      private final Int2StructuralItemArrayMap.Builder<Position> outlinePositionsBuilder =
          Int2StructuralItemArrayMap.builder();
      private DexMethod outlineCallee;
      private boolean isOutline;

      private OutlineCallerPositionBuilder() {}

      @Override
      OutlineCallerPositionBuilder self() {
        return this;
      }

      public OutlineCallerPositionBuilder setOutlineCallee(DexMethod outlineCallee) {
        this.outlineCallee = outlineCallee;
        return this;
      }

      public OutlineCallerPositionBuilder addOutlinePosition(int line, Position callerPosition) {
        outlinePositionsBuilder.put(line, callerPosition);
        return this;
      }

      public OutlineCallerPositionBuilder setIsOutline(boolean isOutline) {
        this.isOutline = isOutline;
        return this;
      }

      public boolean hasOutlinePositions() {
        return !outlinePositionsBuilder.isEmpty();
      }

      @Override
      public OutlineCallerPosition build() {
        assert noCheckOfPosition || line >= 0;
        assert noCheckOfMethod || method != null;
        return new OutlineCallerPosition(
            line,
            method,
            callerPosition,
            removeInnerFramesIfThrowingNpe,
            outlinePositionsBuilder.build(),
            outlineCallee,
            isOutline);
      }
    }
  }
}
