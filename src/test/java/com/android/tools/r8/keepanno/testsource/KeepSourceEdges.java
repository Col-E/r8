// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.testsource;

import com.android.tools.r8.keepanno.ast.KeepClassItemPattern;
import com.android.tools.r8.keepanno.ast.KeepClassItemReference;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepConsequences.Builder;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepFieldNamePattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMemberItemPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility to get the AST edges for the various annotation test sources.
 *
 * <p>This makes it easier to share the test sources among tests (e.g., processor and asm tests).
 */
public class KeepSourceEdges {

  private static class SourceData {
    final Class<?> clazz;
    final String expected;
    final List<KeepEdge> edges;

    public SourceData(Class<?> clazz, String expected, List<KeepEdge> edges) {
      this.clazz = clazz;
      this.expected = expected;
      this.edges = edges;
    }
  }

  private static final List<SourceData> SOURCES = new ArrayList<>();

  static {
    SOURCES.add(
        new SourceData(
            KeepClassAndDefaultConstructorSource.class,
            getKeepClassAndDefaultConstructorSourceExpected(),
            getKeepClassAndDefaultConstructorSourceEdges()));
    SOURCES.add(
        new SourceData(
            KeepFieldSource.class, getKeepFieldSourceExpected(), getKeepFieldSourceEdges()));
    SOURCES.add(
        new SourceData(
            KeepDependentFieldSource.class,
            getKeepDependentFieldSourceExpected(),
            getKeepDependentFieldSourceEdges()));
  }

  public static List<KeepEdge> getExpectedEdges(Class<?> clazz) {
    for (SourceData source : SOURCES) {
      if (source.clazz == clazz) {
        return source.edges;
      }
    }
    throw new RuntimeException();
  }

  public static String getExpected(Class<?> clazz) {
    for (SourceData source : SOURCES) {
      if (source.clazz == clazz) {
        return source.expected;
      }
    }
    throw new RuntimeException();
  }

  public static String getKeepClassAndDefaultConstructorSourceExpected() {
    return StringUtils.lines("A is alive!");
  }

  public static List<KeepEdge> getKeepClassAndDefaultConstructorSourceEdges() {
    Class<?> clazz = KeepClassAndDefaultConstructorSource.A.class;
    return Collections.singletonList(
        mkEdge(mkConsequences(mkTarget(mkClass(clazz)), mkTarget(mkMethod(clazz, "<init>")))));
  }

  public static String getKeepFieldSourceExpected() {
    return StringUtils.lines("The values match!");
  }

  public static List<KeepEdge> getKeepFieldSourceEdges() {
    return Collections.singletonList(
        mkEdge(mkConsequences(mkTarget(mkField(KeepFieldSource.A.class, "f")))));
  }

  public static String getKeepDependentFieldSourceExpected() {
    return getKeepFieldSourceExpected();
  }

  public static List<KeepEdge> getKeepDependentFieldSourceEdges() {
    return Collections.singletonList(
        mkDepEdge(
            mkPreconditions(mkCondition(mkMethod(KeepDependentFieldSource.class, "main"))),
            mkConsequences(mkTarget(mkField(KeepDependentFieldSource.A.class, "f")))));
  }

  // Ast helpers.

  static KeepClassItemPattern mkClass(Class<?> clazz) {
    KeepQualifiedClassNamePattern name = KeepQualifiedClassNamePattern.exact(clazz.getTypeName());
    return KeepClassItemPattern.builder().setClassNamePattern(name).build();
  }

  static KeepMemberItemPattern mkMethod(Class<?> clazz, String methodName) {
    KeepQualifiedClassNamePattern name = KeepQualifiedClassNamePattern.exact(clazz.getTypeName());
    KeepClassItemReference classReference =
        KeepClassItemPattern.builder().setClassNamePattern(name).build().toClassItemReference();
    KeepMethodPattern methodPattern =
        KeepMethodPattern.builder().setNamePattern(KeepMethodNamePattern.exact(methodName)).build();
    KeepMemberItemPattern methodItem =
        KeepMemberItemPattern.builder()
            .setClassReference(classReference)
            .setMemberPattern(methodPattern)
            .build();
    return methodItem;
  }

  static KeepItemPattern mkField(Class<?> clazz, String fieldName) {
    KeepQualifiedClassNamePattern name = KeepQualifiedClassNamePattern.exact(clazz.getTypeName());
    KeepClassItemReference classReference =
        KeepClassItemPattern.builder().setClassNamePattern(name).build().toClassItemReference();
    KeepFieldPattern fieldPattern =
        KeepFieldPattern.builder().setNamePattern(KeepFieldNamePattern.exact(fieldName)).build();
    KeepMemberItemPattern fieldItem =
        KeepMemberItemPattern.builder()
            .setClassReference(classReference)
            .setMemberPattern(fieldPattern)
            .build();
    return fieldItem;
  }

  static KeepTarget mkTarget(KeepItemPattern item) {
    return KeepTarget.builder().setItemPattern(item).build();
  }

  static KeepCondition mkCondition(KeepItemPattern item) {
    return KeepCondition.builder().setItemPattern(item).build();
  }

  static KeepConsequences mkConsequences(KeepTarget... targets) {
    Builder builder = KeepConsequences.builder();
    Arrays.asList(targets).forEach(builder::addTarget);
    return builder.build();
  }

  static KeepPreconditions mkPreconditions(KeepCondition... conditions) {
    KeepPreconditions.Builder builder = KeepPreconditions.builder();
    Arrays.asList(conditions).forEach(builder::addCondition);
    return builder.build();
  }

  static KeepEdge mkEdge(KeepConsequences consequences) {
    return KeepEdge.builder().setConsequences(consequences).build();
  }

  static KeepEdge mkDepEdge(KeepPreconditions preconditions, KeepConsequences consequences) {
    return KeepEdge.builder().setPreconditions(preconditions).setConsequences(consequences).build();
  }
}
