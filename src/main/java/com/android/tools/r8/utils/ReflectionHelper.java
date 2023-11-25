// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ReflectionHelper {

  @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
  public static <T> T performReflection(Object object, ReflectiveOperation<?> operation)
      throws Exception {
    return (T) operation.compute(object);
  }

  public static ReflectiveOperationSequenceBuilder builder() {
    return new ReflectiveOperationSequenceBuilder();
  }

  public enum DeclaredType {
    FIELD,
    METHOD
  }

  public abstract static class ReflectiveOperation<Member> {

    final Class<?> classForDeclaration;
    final String declaredMember;
    final Consumer<Member> modifier;
    final ReflectiveOperation<?> nextOperation;

    private ReflectiveOperation(
        Class<?> classForDeclaration,
        String declaredMember,
        ReflectiveOperation<?> nextOperation,
        Consumer<Member> modifier) {
      this.classForDeclaration = classForDeclaration;
      this.declaredMember = declaredMember;
      this.nextOperation = nextOperation;
      this.modifier = modifier;
    }

    public abstract Object compute(Object object) throws Exception;
  }

  public static class ReflectiveMethodOperation extends ReflectiveOperation<Method> {

    private ReflectiveMethodOperation(
        Class<?> classForDeclaration,
        String declaredMember,
        ReflectiveOperation<?> nextOperation,
        Consumer<Method> modifier) {
      super(classForDeclaration, declaredMember, nextOperation, modifier);
    }

    @Override
    public Object compute(Object object) throws Exception {
      Class<?> clazz = classForDeclaration == null ? object.getClass() : classForDeclaration;
      Method declaredMethod = clazz.getDeclaredMethod(declaredMember);
      modifier.accept(declaredMethod);
      // The reflection helper do not support arguments at this point.
      Object returnValue = declaredMethod.invoke(object);
      return nextOperation != null ? nextOperation.compute(returnValue) : returnValue;
    }
  }

  public static class ReflectiveFieldOperation extends ReflectiveOperation<Field> {

    private ReflectiveFieldOperation(
        Class<?> classForDeclaration,
        String declaredMember,
        ReflectiveOperation<?> nextOperation,
        Consumer<Field> modifier) {
      super(classForDeclaration, declaredMember, nextOperation, modifier);
    }

    @Override
    public Object compute(Object object) throws Exception {
      Class<?> clazz = classForDeclaration == null ? object.getClass() : classForDeclaration;
      Field declaredField = clazz.getDeclaredField(declaredMember);
      modifier.accept(declaredField);
      Object fieldValue = declaredField.get(object);
      return nextOperation != null ? nextOperation.compute(fieldValue) : fieldValue;
    }
  }

  public static class ReflectiveOperationSequenceBuilder {

    List<ReflectiveOperationBuilder> reflectiveOperationBuilderList = new ArrayList<>();

    public ReflectiveOperationBuilder readMethod(String declaredMember) {
      return add(declaredMember, DeclaredType.METHOD);
    }

    public ReflectiveOperationBuilder readField(String declaredMember) {
      return add(declaredMember, DeclaredType.FIELD);
    }

    private ReflectiveOperationBuilder add(String declaredMember, DeclaredType declaredType) {
      ReflectiveOperationBuilder reflectiveOperationBuilder =
          new ReflectiveOperationBuilder(declaredMember, declaredType, this);
      reflectiveOperationBuilderList.add(reflectiveOperationBuilder);
      return reflectiveOperationBuilder;
    }

    public ReflectiveOperation<?> build() {
      assert !reflectiveOperationBuilderList.isEmpty();
      ReflectiveOperation<?> lastOperation = null;
      for (int i = reflectiveOperationBuilderList.size() - 1; i >= 0; i--) {
        lastOperation = reflectiveOperationBuilderList.get(i).build(lastOperation);
      }
      return lastOperation;
    }
  }

  public static class ReflectiveOperationBuilder {

    private final String declaredMember;
    private final DeclaredType declaredType;
    private boolean setAccessible = false;
    private final ReflectiveOperationSequenceBuilder sequenceBuilder;

    private ReflectiveOperationBuilder(
        String declaredMember,
        DeclaredType declaredType,
        ReflectiveOperationSequenceBuilder sequenceBuilder) {
      this.declaredMember = declaredMember;
      this.declaredType = declaredType;
      this.sequenceBuilder = sequenceBuilder;
    }

    public ReflectiveOperationBuilder setSetAccessible(boolean setAccessible) {
      this.setAccessible = setAccessible;
      return this;
    }

    public ReflectiveOperationSequenceBuilder done() {
      return sequenceBuilder;
    }

    private ReflectiveOperation<?> build(ReflectiveOperation<?> nextOperation) {
      if (declaredType == DeclaredType.FIELD) {
        return new ReflectiveFieldOperation(
            null,
            declaredMember,
            nextOperation,
            field -> {
              if (setAccessible) {
                field.setAccessible(true);
              }
            });
      } else {
        assert declaredType == DeclaredType.METHOD;
        return new ReflectiveMethodOperation(
            null,
            declaredMember,
            nextOperation,
            method -> {
              if (setAccessible) {
                method.setAccessible(true);
              }
            });
      }
    }
  }
}
