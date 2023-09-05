// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class FieldResolutionResult
    extends MemberResolutionResult<DexEncodedField, DexField> {

  public static FailedFieldResolutionResult failure() {
    return FailedFieldResolutionResult.INSTANCE;
  }

  public static UnknownFieldResolutionResult unknown() {
    return UnknownFieldResolutionResult.INSTANCE;
  }

  @Override
  public boolean isFieldResolutionResult() {
    return true;
  }

  @Override
  public FieldResolutionResult asFieldResolutionResult() {
    return this;
  }

  public DexEncodedField getResolvedField() {
    return null;
  }

  public DexField getResolvedFieldReference() {
    return null;
  }

  public DexClass getResolvedHolder() {
    return null;
  }

  @Override
  public DexClassAndField getResolutionPair() {
    return null;
  }

  public ProgramField getSingleProgramField() {
    return null;
  }

  public ProgramField getProgramField() {
    return null;
  }

  public boolean isSingleFieldResolutionResult() {
    return false;
  }

  public boolean isSingleProgramFieldResolutionResult() {
    return false;
  }

  public boolean hasSuccessfulResolutionResult() {
    return false;
  }

  public SingleFieldResolutionResult<?> asSingleFieldResolutionResult() {
    return null;
  }

  public SingleProgramFieldResolutionResult asSingleProgramFieldResolutionResult() {
    return null;
  }

  public SingleClasspathFieldResolutionResult asSingleClasspathFieldResolutionResult() {
    return null;
  }

  @Override
  public boolean isSuccessfulMemberResolutionResult() {
    return false;
  }

  @Override
  public SingleFieldResolutionResult<?> asSuccessfulMemberResolutionResult() {
    return null;
  }

  public boolean isPossiblyFailedOrUnknownResolution() {
    return false;
  }

  public boolean hasProgramOrClasspathResult() {
    return false;
  }

  public boolean hasProgramResult() {
    return false;
  }

  public boolean hasClasspathResult() {
    return false;
  }

  public boolean isMultiFieldResolutionResult() {
    return false;
  }

  public final void forEachFieldResolutionResult(Consumer<FieldResolutionResult> resultConsumer) {
    visitFieldResolutionResults(resultConsumer, resultConsumer, resultConsumer);
  }

  public final void forEachSuccessfulFieldResolutionResult(
      Consumer<SingleFieldResolutionResult<?>> resultConsumer) {
    visitFieldResolutionResults(resultConsumer, failedResult -> {});
  }

  public final void visitFieldResolutionResults(
      Consumer<SingleFieldResolutionResult<?>> singleResultConsumer,
      Consumer<FailedOrUnknownFieldResolutionResult> failedResolutionConsumer) {
    visitFieldResolutionResults(
        singleResultConsumer, singleResultConsumer, failedResolutionConsumer);
  }

  public abstract void visitFieldResolutionResults(
      Consumer<? super SingleFieldResolutionResult<?>> programOrClasspathConsumer,
      Consumer<? super SingleLibraryFieldResolutionResult> libraryResultConsumer,
      Consumer<? super FailedOrUnknownFieldResolutionResult> failedResolutionConsumer);

  public DexClass getInitialResolutionHolder() {
    return null;
  }

  public static SingleFieldResolutionResult<?> createSingleFieldResolutionResult(
      DexClass initialResolutionHolder, DexClass holder, DexEncodedField definition) {
    if (holder.isLibraryClass()) {
      return new SingleLibraryFieldResolutionResult(
          initialResolutionHolder, holder.asLibraryClass(), definition);
    } else if (holder.isClasspathClass()) {
      return new SingleClasspathFieldResolutionResult(
          initialResolutionHolder, holder.asClasspathClass(), definition);
    } else {
      assert holder.isProgramClass();
      return new SingleProgramFieldResolutionResult(
          initialResolutionHolder, holder.asProgramClass(), definition);
    }
  }

  public abstract static class SingleFieldResolutionResult<T extends DexClass>
      extends FieldResolutionResult
      implements SuccessfulMemberResolutionResult<DexEncodedField, DexField> {

    private final DexClass initialResolutionHolder;
    private final T resolvedHolder;
    private final DexEncodedField resolvedField;

    @SuppressWarnings("ReferenceEquality")
    SingleFieldResolutionResult(
        DexClass initialResolutionHolder, T resolvedHolder, DexEncodedField resolvedField) {
      assert resolvedHolder.type == resolvedField.getHolderType();
      this.initialResolutionHolder = initialResolutionHolder;
      this.resolvedHolder = resolvedHolder;
      this.resolvedField = resolvedField;
    }

    @Override
    public DexClass getInitialResolutionHolder() {
      return initialResolutionHolder;
    }

    @Override
    public T getResolvedHolder() {
      return resolvedHolder;
    }

    @Override
    public DexEncodedField getResolvedField() {
      return resolvedField;
    }

    @Override
    public DexField getResolvedFieldReference() {
      return resolvedField.getReference();
    }

    @Override
    public DexEncodedField getResolvedMember() {
      return resolvedField;
    }

    @Override
    public DexClassAndField getResolutionPair() {
      return DexClassAndField.create(resolvedHolder, resolvedField);
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return AccessControl.isMemberAccessible(this, context, appView, appInfo);
    }

    @Override
    public boolean isSingleFieldResolutionResult() {
      return true;
    }

    @Override
    public SingleFieldResolutionResult<T> asSingleFieldResolutionResult() {
      return this;
    }

    @Override
    public boolean isSuccessfulMemberResolutionResult() {
      return true;
    }

    @Override
    public SingleFieldResolutionResult<T> asSuccessfulMemberResolutionResult() {
      return this;
    }

    @Override
    public boolean hasSuccessfulResolutionResult() {
      return true;
    }
  }

  public static class SingleProgramFieldResolutionResult
      extends SingleFieldResolutionResult<DexProgramClass> {

    SingleProgramFieldResolutionResult(
        DexClass initialResolutionHolder,
        DexProgramClass resolvedHolder,
        DexEncodedField resolvedField) {
      super(initialResolutionHolder, resolvedHolder, resolvedField);
    }

    @Override
    public ProgramField getProgramField() {
      return getSingleProgramField();
    }

    @Override
    public ProgramField getSingleProgramField() {
      return new ProgramField(getResolvedHolder(), getResolvedField());
    }

    @Override
    public boolean isSingleProgramFieldResolutionResult() {
      return true;
    }

    @Override
    public SingleProgramFieldResolutionResult asSingleProgramFieldResolutionResult() {
      return this;
    }

    @Override
    public boolean hasProgramOrClasspathResult() {
      return true;
    }

    @Override
    public boolean hasProgramResult() {
      return true;
    }

    @Override
    public void visitFieldResolutionResults(
        Consumer<? super SingleFieldResolutionResult<?>> programOrClasspathConsumer,
        Consumer<? super SingleLibraryFieldResolutionResult> libraryResultConsumer,
        Consumer<? super FailedOrUnknownFieldResolutionResult> failedResolutionConsumer) {
      programOrClasspathConsumer.accept(this);
    }
  }

  public static class SingleClasspathFieldResolutionResult
      extends SingleFieldResolutionResult<DexClasspathClass> {

    SingleClasspathFieldResolutionResult(
        DexClass initialResolutionHolder,
        DexClasspathClass resolvedHolder,
        DexEncodedField resolvedField) {
      super(initialResolutionHolder, resolvedHolder, resolvedField);
    }

    @Override
    public SingleClasspathFieldResolutionResult asSingleClasspathFieldResolutionResult() {
      return this;
    }

    @Override
    public boolean hasProgramOrClasspathResult() {
      return true;
    }

    @Override
    public boolean hasClasspathResult() {
      return true;
    }

    @Override
    public void visitFieldResolutionResults(
        Consumer<? super SingleFieldResolutionResult<?>> programOrClasspathConsumer,
        Consumer<? super SingleLibraryFieldResolutionResult> libraryResultConsumer,
        Consumer<? super FailedOrUnknownFieldResolutionResult> failedResolutionConsumer) {
      programOrClasspathConsumer.accept(this);
    }
  }

  public static class SingleLibraryFieldResolutionResult
      extends SingleFieldResolutionResult<DexLibraryClass> {

    SingleLibraryFieldResolutionResult(
        DexClass initialResolutionHolder,
        DexLibraryClass resolvedHolder,
        DexEncodedField resolvedField) {
      super(initialResolutionHolder, resolvedHolder, resolvedField);
    }

    @Override
    public void visitFieldResolutionResults(
        Consumer<? super SingleFieldResolutionResult<?>> programOrClasspathConsumer,
        Consumer<? super SingleLibraryFieldResolutionResult> libraryResultConsumer,
        Consumer<? super FailedOrUnknownFieldResolutionResult> failedResolutionConsumer) {
      libraryResultConsumer.accept(this);
    }
  }

  public abstract static class MultipleFieldResolutionResult<
          C extends DexClass & ProgramOrClasspathClass, T extends SingleFieldResolutionResult<C>>
      extends FieldResolutionResult {

    protected final T programOrClasspathResult;
    protected final List<SingleLibraryFieldResolutionResult> libraryResolutionResults;
    protected final List<FailedOrUnknownFieldResolutionResult> failedOrUnknownResolutionResults;

    public MultipleFieldResolutionResult(
        T programOrClasspathResult,
        List<SingleLibraryFieldResolutionResult> libraryResolutionResults,
        List<FailedOrUnknownFieldResolutionResult> failedOrUnknownResolutionResults) {
      assert programOrClasspathResult == null
          || !programOrClasspathResult.getResolvedHolder().isLibraryClass();
      assert failedOrUnknownResolutionResults.stream()
          .allMatch(FieldResolutionResult::isPossiblyFailedOrUnknownResolution);
      assert BooleanUtils.intValue(programOrClasspathResult != null)
                  + libraryResolutionResults.size()
                  + failedOrUnknownResolutionResults.size()
              > 1
          : "Should have been a single or failed result";
      this.programOrClasspathResult = programOrClasspathResult;
      this.libraryResolutionResults = libraryResolutionResults;
      this.failedOrUnknownResolutionResults = failedOrUnknownResolutionResults;
    }

    @Override
    public boolean isMultiFieldResolutionResult() {
      return true;
    }

    @Override
    public DexClass getInitialResolutionHolder() {
      throw new Unimplemented("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public boolean hasProgramOrClasspathResult() {
      return programOrClasspathResult != null;
    }

    @Override
    public boolean hasProgramResult() {
      return programOrClasspathResult != null && programOrClasspathResult.hasProgramResult();
    }

    @Override
    public boolean hasClasspathResult() {
      return programOrClasspathResult != null && programOrClasspathResult.hasClasspathResult();
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      throw new Unimplemented("Should not be called on MultipleFieldResolutionResult");
    }

    @Override
    public boolean isPossiblyFailedOrUnknownResolution() {
      return !failedOrUnknownResolutionResults.isEmpty();
    }

    @Override
    public boolean isSuccessfulMemberResolutionResult() {
      return failedOrUnknownResolutionResults.isEmpty();
    }

    @Override
    public void visitFieldResolutionResults(
        Consumer<? super SingleFieldResolutionResult<?>> programOrClasspathConsumer,
        Consumer<? super SingleLibraryFieldResolutionResult> libraryResultConsumer,
        Consumer<? super FailedOrUnknownFieldResolutionResult> failedResolutionConsumer) {
      if (programOrClasspathResult != null) {
        programOrClasspathConsumer.accept(programOrClasspathResult);
      }
      libraryResolutionResults.forEach(libraryResultConsumer);
      failedOrUnknownResolutionResults.forEach(failedResolutionConsumer);
    }

    @Override
    public boolean hasSuccessfulResolutionResult() {
      return hasProgramOrClasspathResult() || !libraryResolutionResults.isEmpty();
    }
  }

  public static class MultipleProgramWithLibraryFieldResolutionResult
      extends MultipleFieldResolutionResult<DexProgramClass, SingleProgramFieldResolutionResult> {

    public MultipleProgramWithLibraryFieldResolutionResult(
        SingleProgramFieldResolutionResult programOrClasspathResult,
        List<SingleLibraryFieldResolutionResult> libraryResolutionResults,
        List<FailedOrUnknownFieldResolutionResult> failedOrUnknownResolutionResults) {
      super(programOrClasspathResult, libraryResolutionResults, failedOrUnknownResolutionResults);
    }

    @Override
    public ProgramField getProgramField() {
      return programOrClasspathResult == null ? null : programOrClasspathResult.getProgramField();
    }
  }

  public static class MultipleClasspathWithLibraryFieldResolutionResult
      extends MultipleFieldResolutionResult<
          DexClasspathClass, SingleClasspathFieldResolutionResult> {

    public MultipleClasspathWithLibraryFieldResolutionResult(
        SingleClasspathFieldResolutionResult programOrClasspathResult,
        List<SingleLibraryFieldResolutionResult> libraryResolutionResults,
        List<FailedOrUnknownFieldResolutionResult> failedOrUnknownResolutionResults) {
      super(programOrClasspathResult, libraryResolutionResults, failedOrUnknownResolutionResults);
    }
  }

  public static class MultipleLibraryFieldResolutionResult
      extends MultipleFieldResolutionResult<DexProgramClass, SingleProgramFieldResolutionResult> {

    public MultipleLibraryFieldResolutionResult(
        List<SingleLibraryFieldResolutionResult> libraryResolutionResults,
        List<FailedOrUnknownFieldResolutionResult> failedOrUnknownResolutionResults) {
      super(null, libraryResolutionResults, failedOrUnknownResolutionResults);
    }
  }

  public abstract static class FailedOrUnknownFieldResolutionResult extends FieldResolutionResult {

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppView<?> appView, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.FALSE;
    }

    @Override
    public void visitFieldResolutionResults(
        Consumer<? super SingleFieldResolutionResult<?>> programOrClasspathConsumer,
        Consumer<? super SingleLibraryFieldResolutionResult> libraryResultConsumer,
        Consumer<? super FailedOrUnknownFieldResolutionResult> failedResolutionConsumer) {
      failedResolutionConsumer.accept(this);
    }

    @Override
    public boolean isPossiblyFailedOrUnknownResolution() {
      return true;
    }
  }

  public static class FailedFieldResolutionResult extends FailedOrUnknownFieldResolutionResult {

    private static final FailedFieldResolutionResult INSTANCE = new FailedFieldResolutionResult();

    @Override
    public boolean isFailedResolution() {
      return true;
    }
  }

  /**
   * Used in D8 when trying to resolve a field that is not declared on the enclosing class of the
   * current method.
   */
  public static class UnknownFieldResolutionResult extends FailedOrUnknownFieldResolutionResult {

    private static final UnknownFieldResolutionResult INSTANCE = new UnknownFieldResolutionResult();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private FieldResolutionResult currentResult = null;

    private Builder() {}

    public void addResolutionResult(FieldResolutionResult otherResult) {
      assert otherResult != null;
      if (currentResult == null) {
        currentResult = otherResult;
        return;
      }
      Box<SingleFieldResolutionResult<?>> singleResult = new Box<>();
      List<SingleLibraryFieldResolutionResult> libraryResults = new ArrayList<>();
      List<FailedOrUnknownFieldResolutionResult> failedResults = new ArrayList<>();
      currentResult.visitFieldResolutionResults(
          singleResult::set, libraryResults::add, failedResults::add);
      otherResult.visitFieldResolutionResults(
          otherProgramOrClasspathResult -> {
            if (singleResult.isSet()) {
              assert false : "Unexpected multiple results between program and classpath";
              if (singleResult.get().hasProgramResult()) {
                return;
              }
            }
            singleResult.set(otherProgramOrClasspathResult);
          },
          newLibraryResult -> {
            if (!Iterables.any(
                libraryResults,
                existing -> existing.getResolvedHolder() == newLibraryResult.getResolvedHolder())) {
              libraryResults.add(newLibraryResult);
            }
          },
          newFailedResult -> {
            if (!Iterables.any(
                failedResults,
                existing ->
                    existing.isFailedResolution() == newFailedResult.isFailedResolution())) {
              failedResults.add(newFailedResult);
            }
          });
      if (!singleResult.isSet()) {
        if (libraryResults.size() == 1 && failedResults.isEmpty()) {
          currentResult = libraryResults.get(0);
        } else if (libraryResults.isEmpty() && failedResults.size() == 1) {
          currentResult = failedResults.get(0);
        } else {
          currentResult = new MultipleLibraryFieldResolutionResult(libraryResults, failedResults);
        }
      } else if (libraryResults.isEmpty() && failedResults.isEmpty()) {
        currentResult = singleResult.get();
      } else if (singleResult.get().hasProgramResult()) {
        currentResult =
            new MultipleProgramWithLibraryFieldResolutionResult(
                singleResult.get().asSingleProgramFieldResolutionResult(),
                libraryResults,
                failedResults);
      } else {
        SingleClasspathFieldResolutionResult classpathResult =
            singleResult.get().asSingleClasspathFieldResolutionResult();
        assert classpathResult != null;
        currentResult =
            new MultipleClasspathWithLibraryFieldResolutionResult(
                classpathResult, libraryResults, failedResults);
      }
    }

    public FieldResolutionResult buildOrIfEmpty(FieldResolutionResult emptyResult) {
      return currentResult == null ? emptyResult : currentResult;
    }
  }
}
