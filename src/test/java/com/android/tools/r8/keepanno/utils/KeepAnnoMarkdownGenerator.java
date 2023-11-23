// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.annotations.KeepBinding;
import com.android.tools.r8.keepanno.annotations.KeepCondition;
import com.android.tools.r8.keepanno.annotations.KeepEdge;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByNative;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.keepanno.doctests.UsesReflectionDocumentationTest;
import com.android.tools.r8.keepanno.utils.KeepItemAnnotationGenerator.Generator;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeepAnnoMarkdownGenerator {

  public static void generateMarkdownDoc(Generator generator) {
    try {
      new KeepAnnoMarkdownGenerator(generator).internalGenerateMarkdownDoc();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String JAVADOC_URL =
      "https://storage.googleapis.com/r8-releases/raw/main/docs/keepanno/javadoc/";

  private static final String INCLUDE_MD_START = "[[[INCLUDE";
  private static final String INCLUDE_MD_DOC_START = "[[[INCLUDE DOC";
  private static final String INCLUDE_MD_CODE_START = "[[[INCLUDE CODE";
  private static final String INCLUDE_MD_END = "]]]";

  private static final String INCLUDE_DOC_START = "INCLUDE DOC:";
  private static final String INCLUDE_DOC_END = "INCLUDE END";
  private static final String INCLUDE_CODE_START = "INCLUDE CODE:";
  private static final String INCLUDE_CODE_END = "INCLUDE END";

  private final Generator generator;
  private final Map<String, String> typeLinkReplacements;
  private Map<String, String> docReplacements = new HashMap<>();
  private Map<String, String> codeReplacements = new HashMap<>();

  public KeepAnnoMarkdownGenerator(Generator generator) {
    this.generator = generator;
    typeLinkReplacements =
        getTypeLinkReplacements(
            KeepEdge.class,
            KeepBinding.class,
            KeepTarget.class,
            KeepCondition.class,
            UsesReflection.class,
            UsedByReflection.class,
            UsedByNative.class,
            KeepForApi.class);
    populateCodeAndDocReplacements(UsesReflectionDocumentationTest.class);
  }

  private Map<String, String> getTypeLinkReplacements(Class<?>... classes) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    for (Class<?> clazz : classes) {
      builder.put("`@" + clazz.getSimpleName() + "`", getMdLink(clazz));
    }
    return builder.build();
  }

  private void populateCodeAndDocReplacements(Class<?>... classes) {
    try {
      for (Class<?> clazz : classes) {
        Path sourceFile = ToolHelper.getSourceFileForTestClass(clazz);
        String text = FileUtils.readTextFile(sourceFile, StandardCharsets.UTF_8);
        extractMarkers(text, INCLUDE_DOC_START, INCLUDE_DOC_END, docReplacements);
        extractMarkers(text, INCLUDE_CODE_START, INCLUDE_CODE_END, codeReplacements);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void extractMarkers(
      String text, String markerKeyStart, String markerKeyEnd, Map<String, String> replacementMap) {
    int index = text.indexOf(markerKeyStart);
    while (index >= 0) {
      int markerTitleEnd = text.indexOf('\n', index);
      if (markerTitleEnd < 0) {
        throw new RuntimeException("Failed to find end marker title");
      }
      int end = text.indexOf(markerKeyEnd, index);
      if (end < 0) {
        throw new RuntimeException("Failed to find end marker");
      }
      int endBeforeNewLine = text.lastIndexOf('\n', end);
      if (endBeforeNewLine < markerTitleEnd) {
        throw new RuntimeException("No new-line before end marker");
      }
      String markerTitle = text.substring(index + markerKeyStart.length(), markerTitleEnd);
      String includeContent = text.substring(markerTitleEnd + 1, endBeforeNewLine);
      String old = replacementMap.put(markerTitle.trim(), includeContent);
      if (old != null) {
        throw new RuntimeException("Duplicate definition of marker");
      }
      index = text.indexOf(markerKeyStart, end);
    }
  }

  private String getMdLink(Class<?> clazz) {
    String url = JAVADOC_URL + clazz.getTypeName().replace('.', '/') + ".html";
    return "[@" + clazz.getSimpleName() + "](" + url + ")";
  }

  private void println() {
    generator.println("");
  }

  private void println(String line) {
    generator.println(line);
  }

  private void internalGenerateMarkdownDoc() throws IOException {
    Path template = Paths.get("doc/keepanno-guide.template.md");
    println("[comment]: <> (DO NOT EDIT - GENERATED FILE)");
    println("[comment]: <> (Changes should be made in " + template + ")");
    println();
    List<String> readAllLines = FileUtils.readAllLines(template);
    for (int i = 0; i < readAllLines.size(); i++) {
      String line = readAllLines.get(i);
      try {
        processLine(line, generator);
      } catch (Exception e) {
        System.err.println("Parse error on line " + (i + 1) + ":");
        System.err.println(line);
        System.err.println(e.getMessage());
      }
    }
  }

  private String replaceCodeAndDocMarkers(String line) {
    String originalLine = line;
    line = line.trim();
    if (!line.startsWith(INCLUDE_MD_START)) {
      return originalLine;
    }
    int keyStartIndex = line.indexOf(':');
    if (!line.endsWith(INCLUDE_MD_END) || keyStartIndex < 0) {
      throw new RuntimeException("Invalid include directive");
    }
    String key = line.substring(keyStartIndex + 1, line.length() - INCLUDE_MD_END.length());
    if (line.startsWith(INCLUDE_MD_DOC_START)) {
      return replaceDoc(key);
    }
    if (line.startsWith(INCLUDE_MD_CODE_START)) {
      return replaceCode(key);
    }
    throw new RuntimeException("Unknown replacement marker");
  }

  private String replaceDoc(String key) {
    String replacement = docReplacements.get(key);
    if (replacement == null) {
      throw new RuntimeException("No replacement defined for " + key);
    }
    return unindentLines(replacement, new StringBuilder()).toString();
  }

  private String replaceCode(String key) {
    String replacement = codeReplacements.get(key);
    if (replacement == null) {
      throw new RuntimeException("No replacement defined for " + key);
    }
    StringBuilder builder = new StringBuilder();
    builder.append("```\n");
    unindentLines(replacement, builder);
    builder.append("```\n");
    return builder.toString();
  }

  private StringBuilder unindentLines(String replacement, StringBuilder builder) {
    int shortestSpacePrefix = Integer.MAX_VALUE;
    List<String> lines = StringUtils.split(replacement, '\n');
    for (String line : lines) {
      if (!line.isEmpty()) {
        shortestSpacePrefix = Math.min(shortestSpacePrefix, findFirstNonSpaceIndex(line));
      }
    }
    if (shortestSpacePrefix > 0) {
      for (String line : lines) {
        if (!line.isEmpty()) {
          builder.append(line.substring(shortestSpacePrefix));
        }
        builder.append('\n');
      }
    } else {
      builder.append(replacement);
      builder.append('\n');
    }
    return builder;
  }

  private int findFirstNonSpaceIndex(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) != ' ') {
        return i;
      }
    }
    return line.length();
  }

  private String tryLinkReplacements(String line) {
    int index = line.indexOf("`@");
    if (index < 0) {
      return null;
    }
    int end = line.indexOf('`', index + 1);
    if (end < 0) {
      throw new RuntimeException("No end marker on line: " + line);
    }
    String typeLink = line.substring(index, end + 1);
    String replacement = typeLinkReplacements.get(typeLink);
    if (replacement == null) {
      throw new RuntimeException("Unknown type link: " + typeLink);
    }
    return line.replace(typeLink, replacement);
  }

  private void processLine(String line, Generator generator) {
    line = replaceCodeAndDocMarkers(line);
    String replacement = tryLinkReplacements(line);
    while (replacement != null) {
      line = replacement;
      replacement = tryLinkReplacements(line);
    }
    generator.println(line);
  }
}
