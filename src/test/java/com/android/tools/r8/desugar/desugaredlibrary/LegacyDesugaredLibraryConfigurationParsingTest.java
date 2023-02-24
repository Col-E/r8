// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticOrigin;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.RELEASED_1_1_5;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.errors.UnsupportedDesugaredLibraryConfigurationVersionDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.StringUtils.BraceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LegacyDesugaredLibraryConfigurationParsingTest extends DesugaredLibraryTestBase {

  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameterized.Parameters(name = "{0}, spec: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(), ImmutableList.of(RELEASED_1_1_5));
  }

  public LegacyDesugaredLibraryConfigurationParsingTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    parameters.assertNoneRuntime();
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  final AndroidApiLevel minApi = AndroidApiLevel.B;
  final boolean libraryCompilation = true;

  final DexItemFactory factory = new DexItemFactory();
  final Origin origin =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return "Test Origin";
        }
      };

  final Map<String, Object> TEMPLATE =
      ImmutableMap.<String, Object>builder()
          .put(
              "configuration_format_version",
              LegacyDesugaredLibrarySpecificationParser.MAX_SUPPORTED_VERSION)
          .put("group_id", "com.tools.android")
          .put("artifact_id", "desugar_jdk_libs")
          .put(
              "version", LegacyDesugaredLibrarySpecificationParser.MIN_SUPPORTED_VERSION.toString())
          .put("required_compilation_api_level", 1)
          .put("synthesized_library_classes_package_prefix", "j$.")
          .put("common_flags", Collections.emptyList())
          .put("program_flags", Collections.emptyList())
          .put("library_flags", Collections.emptyList())
          .build();

  private LinkedHashMap<String, Object> template() {
    return new LinkedHashMap<>(TEMPLATE);
  }

  private LegacyDesugaredLibrarySpecificationParser parser(DiagnosticsHandler handler) {
    return new LegacyDesugaredLibrarySpecificationParser(
        factory, new Reporter(handler), libraryCompilation, minApi.getLevel());
  }

  private LegacyDesugaredLibrarySpecification runPassing(String resource) {
    return runPassing(StringResource.fromString(resource, origin));
  }

  private LegacyDesugaredLibrarySpecification runPassing(StringResource resource) {
    TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
    LegacyDesugaredLibrarySpecification spec = parser(handler).parse(resource);
    handler.assertNoMessages();
    return spec;
  }

  private void runFailing(String json, Consumer<TestDiagnosticMessages> checker) {
    TestDiagnosticMessagesImpl handler = new TestDiagnosticMessagesImpl();
    try {
      parser(handler).parse(StringResource.fromString(json, origin));
      fail("Expected failure");
    } catch (AbortException e) {
      checker.accept(handler);
    }
  }

  @Test
  public void testReference() {
    // Just test that the reference file parses without issues.
    LegacyDesugaredLibrarySpecification spec =
        runPassing(StringResource.fromFile(libraryDesugaringSpecification.getSpecification()));
    assertEquals(libraryCompilation, spec.isLibraryCompilation());
  }

  @Test
  public void testEmpty() {
    runFailing(
        "",
        diagnostics -> {
          diagnostics.assertErrorsMatch(
              allOf(
                  diagnosticMessage(containsString("Not a JSON Object")),
                  diagnosticOrigin(origin)));
        });
  }

  @Test
  public void testRequiredKeys() {
    ImmutableList<String> requiredKeys =
        ImmutableList.of(
            "configuration_format_version",
            "group_id",
            "artifact_id",
            "version",
            "required_compilation_api_level",
            "synthesized_library_classes_package_prefix",
            "common_flags",
            "program_flags",
            "library_flags");
    for (String key : requiredKeys) {
      Map<String, Object> data = template();
      data.remove(key);
      runFailing(
          toJson(data),
          diagnostics ->
              diagnostics.assertErrorsMatch(
                  allOf(
                      diagnosticMessage(containsString("Invalid desugared library configuration")),
                      diagnosticMessage(containsString("Expected required key '" + key + "'")),
                      diagnosticOrigin(origin))));
    }
  }

  @Test
  public void testUnsupportedVersion() {
    LinkedHashMap<String, Object> data = template();
    SemanticVersion minVersion = LegacyDesugaredLibrarySpecificationParser.MIN_SUPPORTED_VERSION;
    data.put(
        "version",
        SemanticVersion.create(
                minVersion.getMajor(), minVersion.getMinor(), minVersion.getPatch() - 1)
            .toString());
    runFailing(
        toJson(data),
        diagnostics ->
            diagnostics.assertErrorsMatch(
                allOf(
                    diagnosticMessage(containsString("upgrade the desugared library")),
                    diagnosticOrigin(origin))));
  }

  @Test
  public void testUnsupportedAbove() {
    LinkedHashMap<String, Object> data = template();
    data.put("configuration_format_version", 99);
    runFailing(
        toJson(data),
        diagnostics ->
            diagnostics.assertErrorsMatch(
                allOf(
                    diagnosticType(UnsupportedDesugaredLibraryConfigurationVersionDiagnostic.class),
                    diagnosticMessage(containsString("upgrade the D8/R8 compiler")),
                    diagnosticMessage(
                        containsString(
                            "https://developer.android.com/studio/build/library-desugaring-versions")),
                    diagnosticMessage(
                        containsString(
                            "https://developer.android.com/studio/build/library-desugaring")),
                    diagnosticOrigin(origin))));
  }

  @Test
  public void testCustomAndWrapperOverlap() {
    LinkedHashMap<String, Object> data = template();
    data.put(
        "common_flags",
        ImmutableList.of(
            ImmutableMap.of(
                "api_level_below_or_equal",
                100000,
                "custom_conversion",
                ImmutableMap.of("java.util.Foo", "j$.util.FooConv"),
                "wrapper_conversion",
                ImmutableList.of("java.util.Foo"))));
    runFailing(
        toJson(data),
        diagnostics ->
            diagnostics.assertErrorsMatch(
                allOf(
                    diagnosticMessage(containsString("Duplicate types")),
                    diagnosticMessage(containsString("java.util.Foo")),
                    diagnosticOrigin(origin))));
  }

  @Test
  public void testRedefinition() {
    LinkedHashMap<String, Object> data = template();
    data.put(
        "common_flags",
        ImmutableList.of(
            ImmutableMap.of(
                "api_level_below_or_equal",
                100000,
                "custom_conversion",
                ImmutableMap.of("java.util.Foo", "j$.util.FooConv1"))));
    data.put(
        "library_flags",
        ImmutableList.of(
            ImmutableMap.of(
                "api_level_below_or_equal",
                100000,
                "custom_conversion",
                ImmutableMap.of("java.util.Foo", "j$.util.FooConv2"))));
    runFailing(
        toJson(data),
        diagnostics ->
            diagnostics.assertErrorsMatch(
                allOf(
                    diagnosticMessage(containsString("Duplicate assignment of key")),
                    diagnosticMessage(containsString("java.util.Foo")),
                    diagnosticMessage(containsString("custom_conversion")),
                    diagnosticOrigin(origin))));
  }

  @Test
  public void testDuplicate() {
    LinkedHashMap<String, Object> data = template();
    data.put(
        "common_flags",
        ImmutableList.of(
            ImmutableMap.of(
                "api_level_below_or_equal",
                100000,
                "custom_conversion",
                ImmutableMap.of(
                    "java.util.Foo", "j$.util.FooConv1",
                    "java.util.Foo2", "j$.util.FooConv2"))));
    // The gson parser will overwrite the key in order during parsing, thus hiding potential issues.
    LegacyDesugaredLibrarySpecification spec = runPassing(toJson(data).replace("Foo2", "Foo"));
    assertEquals(
        Collections.singletonList("java.util.Foo"),
        spec.getCustomConversions().keySet().stream()
            .map(DexType::toString)
            .collect(Collectors.toList()));
    assertEquals(
        Collections.singletonList("j$.util.FooConv2"),
        spec.getCustomConversions().values().stream()
            .map(DexType::toString)
            .collect(Collectors.toList()));
  }

  // JSON building helpers.
  // This does not use gson to make the text input construction independent of gson.

  private static String toJson(Map<String, Object> data) {
    StringBuilder builder = new StringBuilder();
    toJsonObject(data, builder);
    return builder.toString();
  }

  private static void toJsonObject(Map<String, Object> data, StringBuilder builder) {
    StringUtils.append(
        builder,
        ListUtils.map(
            data.entrySet(),
            entry -> "\n  " + quote(entry.getKey()) + ": " + toJsonElement(entry.getValue())),
        ", ",
        BraceType.TUBORG);
  }

  private static String toJsonElement(Object element) {
    StringBuilder builder = new StringBuilder();
    toJsonElement(element, builder);
    return builder.toString();
  }

  private static void toJsonElement(Object element, StringBuilder builder) {
    if (element instanceof String) {
      builder.append(quote((String) element));
    } else if (element instanceof Integer) {
      builder.append(element);
    } else if (element instanceof List) {
      @SuppressWarnings("unchecked")
      List<Object> elements = (List<Object>) element;
      toJsonList(elements, builder);
    } else if (element instanceof Map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> elements = (Map<String, Object>) element;
      toJsonObject(elements, builder);
    } else {
      throw new IllegalStateException("Unexpected object type: " + element.getClass());
    }
  }

  private static void toJsonList(List<Object> element, StringBuilder builder) {
    StringUtils.append(
        builder, ListUtils.map(element, o -> "\n  " + toJsonElement(o)), ", ", BraceType.SQUARE);
  }

  private static String quote(String str) {
    return "\"" + str + "\"";
  }
}
