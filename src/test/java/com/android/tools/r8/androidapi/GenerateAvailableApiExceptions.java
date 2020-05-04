// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.androidapi;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cfmethodgeneration.MethodGenerationBase;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class GenerateAvailableApiExceptions {

  private static final int fixedApiLevel = AndroidApiLevel.L.getLevel();

  private static String generateBuildMapCode(Path apiVersionsXml) throws Exception {
    CodeInspector inspector = new CodeInspector(ToolHelper.getAndroidJar(fixedApiLevel));
    Map<DexClass, Boolean> isExceptionCache = new IdentityHashMap<>();
    Int2ReferenceMap<Set<String>> exceptionsMap = new Int2ReferenceOpenHashMap<>();

    // Read api-versions.xml locating all classes that derive throwable and adding them to the map.
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    Document document = factory.newDocumentBuilder().parse(apiVersionsXml.toFile());
    NodeList classes = document.getElementsByTagName("class");
    int totalCount = 0;
    for (int i = 0; i < classes.getLength(); i++) {
      Node node = classes.item(i);
      assert node.getNodeType() == Node.ELEMENT_NODE;
      Node sinceAttr = node.getAttributes().getNamedItem("since");
      if (sinceAttr == null) {
        continue;
      }
      int since = Integer.parseInt(sinceAttr.getNodeValue());
      if (since >= fixedApiLevel) {
        continue;
      }
      String name = node.getAttributes().getNamedItem("name").getNodeValue();
      String type = DescriptorUtils.getJavaTypeFromBinaryName(name);
      ClassSubject clazz = inspector.clazz(type);
      if (isThrowable(clazz, isExceptionCache, inspector)) {
        exceptionsMap.computeIfAbsent(since, k -> new TreeSet<>()).add(name);
        totalCount++;
      }
    }

    // Build the code for the buildMap method.
    StringBuilder builder = new StringBuilder();
    builder.append("public class DoNotCommit {");
    builder.append("public static Set<DexType> build(DexItemFactory factory, int minApiLevel) {");
    builder.append("  Set<DexType> types = SetUtils.newIdentityHashSet(" + totalCount + ");");
    for (int api = 1; api < fixedApiLevel; api++) {
      Set<String> names = exceptionsMap.get(api);
      if (names == null || names.isEmpty()) {
        continue;
      }
      builder.append("if (minApiLevel >= " + api + ") {");
      for (String name : names) {
        String desc = DescriptorUtils.getDescriptorFromClassBinaryName(name);
        builder.append("  types.add(factory.createType(\"" + desc + "\"));");
      }
      builder.append("}");
    }
    builder.append("  return types;");
    builder.append("}");
    builder.append("}");

    // Write to temp file and format that file.
    String rawOutput = builder.toString();
    Path tempFile = Files.createTempFile("output-", ".java");
    FileUtils.writeTextFile(tempFile, rawOutput);
    return MethodGenerationBase.formatRawOutput(tempFile);
  }

  private static boolean isThrowable(
      ClassSubject clazz, Map<DexClass, Boolean> cache, CodeInspector inspector) {
    if (!clazz.isPresent()) {
      return false;
    }
    if (clazz.getDexProgramClass().type == inspector.getFactory().objectType) {
      return false;
    }
    if (clazz.getDexProgramClass().type == inspector.getFactory().throwableType) {
      return true;
    }
    return cache.computeIfAbsent(
        clazz.getDexProgramClass(),
        c -> {
          return c.superType != null
              && isThrowable(inspector.clazz(c.superType.toSourceString()), cache, inspector);
        });
  }

  public static void main(String[] args) throws Exception {
    Path apiVersionsXml =
        args.length == 1
            ? Paths.get(args[0])
            : Paths.get("<your-android-checkout>/development/sdk/api-versions.xml");
    System.out.println(generateBuildMapCode(apiVersionsXml));
  }
}
