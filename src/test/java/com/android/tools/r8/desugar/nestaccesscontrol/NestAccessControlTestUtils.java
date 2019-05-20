// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.StringContains.containsString;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class NestAccessControlTestUtils {

  public static final Path JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_JAR_DIR).resolve("nestHostExample" + JAR_EXTENSION);
  public static final Path CLASSES_PATH =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_BUILD_DIR).resolve("nestHostExample/");
  public static final String PACKAGE_NAME = "nestHostExample.";

  public static final List<String> CLASS_NAMES =
      ImmutableList.of(
          "BasicNestHostWithInnerClassFields",
          "BasicNestHostWithInnerClassFields$BasicNestedClass",
          "BasicNestHostWithInnerClassMethods",
          "BasicNestHostWithInnerClassMethods$BasicNestedClass",
          "BasicNestHostWithInnerClassConstructors",
          "BasicNestHostWithInnerClassConstructors$BasicNestedClass",
          "BasicNestHostWithAnonymousInnerClass",
          "BasicNestHostWithAnonymousInnerClass$1",
          "BasicNestHostWithAnonymousInnerClass$InterfaceForAnonymousClass",
          "BasicNestHostClassMerging",
          "BasicNestHostClassMerging$MiddleInner",
          "BasicNestHostClassMerging$MiddleOuter",
          "BasicNestHostClassMerging$InnerMost",
          "BasicNestHostTreePruning",
          "BasicNestHostTreePruning$Pruned",
          "BasicNestHostTreePruning$NotPruned",
          "NestHostExample",
          "NestHostExample$NestMemberInner",
          "NestHostExample$NestMemberInner$NestMemberInnerInner",
          "NestHostExample$StaticNestMemberInner",
          "NestHostExample$StaticNestMemberInner$StaticNestMemberInnerInner",
          "NestHostExample$StaticNestInterfaceInner");
  public static final int NUMBER_OF_TEST_CLASSES = CLASS_NAMES.size();

  // The following map use ids, i.e., strings which represents respectively
  // a nest with only field, method, constructor, anonymous class and
  // all at once nest based private accesses.
  public static final ImmutableList<String> NEST_IDS =
      ImmutableList.of("fields", "methods", "constructors", "anonymous", "all");
  public static final ImmutableMap<String, String> MAIN_CLASSES =
      ImmutableMap.<String, String>builder()
          .put("fields", "BasicNestHostWithInnerClassFields")
          .put("methods", "BasicNestHostWithInnerClassMethods")
          .put("constructors", "BasicNestHostWithInnerClassConstructors")
          .put("anonymous", "BasicNestHostWithAnonymousInnerClass")
          .put("all", "NestHostExample")
          .put("merge", "BasicNestHostClassMerging")
          .put("prune", "BasicNestHostTreePruning")
          .build();
  public static final String ALL_RESULT_LINE =
      String.join(
          ", ",
          new String[] {
            "field",
            "staticField",
            "staticField",
            "hostMethod",
            "staticHostMethod",
            "staticHostMethod",
            "nest1SField",
            "staticNest1SField",
            "staticNest1SField",
            "nest1SMethod",
            "staticNest1SMethod",
            "staticNest1SMethod",
            "nest2SField",
            "staticNest2SField",
            "staticNest2SField",
            "nest2SMethod",
            "staticNest2SMethod",
            "staticNest2SMethod",
            "nest1Field",
            "nest1Method",
            "nest2Field",
            "nest2Method"
          });
  public static final ImmutableMap<String, String> EXPECTED_RESULTS =
      ImmutableMap.<String, String>builder()
          .put(
              "fields",
              StringUtils.lines(
                  "RWnestFieldRWRWnestFieldRWRWnestFieldnoBridge", "RWfieldRWRWfieldRWRWnestField"))
          .put(
              "methods",
              StringUtils.lines(
                  "nestMethodstaticNestMethodstaticNestMethodnoBridge",
                  "hostMethodstaticHostMethodstaticNestMethod"))
          .put(
              "constructors",
              StringUtils.lines("field", "nest1SField", "1", "innerFieldUnusedConstructor"))
          .put(
              "anonymous",
              StringUtils.lines(
                  "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethod"))
          .put(
              "all",
              StringUtils.lines(
                  ALL_RESULT_LINE,
                  ALL_RESULT_LINE,
                  ALL_RESULT_LINE,
                  ALL_RESULT_LINE,
                  "staticInterfaceMethodstaticStaticInterfaceMethod",
                  "staticInterfaceMethodstaticStaticInterfaceMethod",
                  "staticInterfaceMethodstaticStaticInterfaceMethod",
                  "staticInterfaceMethodstaticStaticInterfaceMethod"))
          .build();

  public static String getMainClass(String id) {
    return PACKAGE_NAME + MAIN_CLASSES.get(id);
  }

  public static String getExpectedResult(String id) {
    return EXPECTED_RESULTS.get(id);
  }

  public static List<Path> classesOfNest(String nestID) {
    return classesMatching(MAIN_CLASSES.get(nestID));
  }

  public static List<Path> classesMatching(String matcher) {
    return CLASS_NAMES.stream()
        .filter(name -> containsString(matcher).matches(name))
        .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
        .collect(toList());
  }
}
