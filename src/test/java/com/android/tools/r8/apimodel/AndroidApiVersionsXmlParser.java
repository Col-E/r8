// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidApiVersionsXmlParser {

  private final List<ParsedApiClass> classes = new ArrayList<>();

  private final File apiVersionsXml;
  private final AndroidApiLevel maxApiLevel;

  private AndroidApiVersionsXmlParser(File apiVersionsXml, AndroidApiLevel maxApiLevel) {
    this.apiVersionsXml = apiVersionsXml;
    this.maxApiLevel = maxApiLevel;
  }

  private ParsedApiClass register(ClassReference reference, AndroidApiLevel apiLevel) {
    ParsedApiClass parsedApiClass = new ParsedApiClass(reference, apiLevel);
    classes.add(parsedApiClass);
    return parsedApiClass;
  }

  private void readApiVersionsXmlFile() throws Exception {
    CodeInspector inspector = new CodeInspector(ToolHelper.getAndroidJar(maxApiLevel));
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    Document document = factory.newDocumentBuilder().parse(apiVersionsXml);
    NodeList classes = document.getElementsByTagName("class");
    for (int i = 0; i < classes.getLength(); i++) {
      Node node = classes.item(i);
      assert node.getNodeType() == Node.ELEMENT_NODE;
      AndroidApiLevel apiLevel = getMaxAndroidApiLevelFromNode(node, AndroidApiLevel.B);
      String type = DescriptorUtils.getJavaTypeFromBinaryName(getName(node));
      ClassSubject clazz = inspector.clazz(type);
      if (!clazz.isPresent()) {
        // TODO(b/190326408): Investigate why the class is not present.
        continue;
      }
      ClassReference originalReference = clazz.getOriginalReference();
      ParsedApiClass parsedApiClass = register(originalReference, apiLevel);
      NodeList members = node.getChildNodes();
      for (int j = 0; j < members.getLength(); j++) {
        Node memberNode = members.item(j);
        if (isMethod(memberNode)) {
          // TODO(b/190326408): Check for existence.
          parsedApiClass.register(
              getMethodReference(originalReference, memberNode),
              getMaxAndroidApiLevelFromNode(memberNode, apiLevel));
        } else if (isField(memberNode)) {
          // The field do not have descriptors and are supposed to be unique.
          FieldSubject fieldSubject = clazz.uniqueFieldWithName(getName(memberNode));
          if (!fieldSubject.isPresent()) {
            // TODO(b/190326408): Investigate why the member is not present.
            continue;
          }
          parsedApiClass.register(
              fieldSubject.getOriginalReference(),
              getMaxAndroidApiLevelFromNode(memberNode, apiLevel));
        }
      }
    }
  }

  private boolean isMethod(Node node) {
    return node.getNodeName().equals("method");
  }

  private String getName(Node node) {
    return node.getAttributes().getNamedItem("name").getNodeValue();
  }

  private MethodReference getMethodReference(ClassReference classDescriptor, Node node) {
    assert isMethod(node);
    String name = getName(node);
    int signatureStart = name.indexOf('(');
    assert signatureStart > 0;
    String parsedName = name.substring(0, signatureStart).replace("&lt;", "<");
    assert !parsedName.contains("&");
    return Reference.methodFromDescriptor(
        classDescriptor.getDescriptor(), parsedName, name.substring(signatureStart));
  }

  private boolean isField(Node node) {
    return node.getNodeName().equals("field");
  }

  private AndroidApiLevel getMaxAndroidApiLevelFromNode(Node node, AndroidApiLevel defaultValue) {
    if (node == null) {
      return defaultValue;
    }
    Node since = node.getAttributes().getNamedItem("since");
    if (since == null) {
      return defaultValue;
    }
    return defaultValue.max(
        AndroidApiLevel.getAndroidApiLevel(Integer.parseInt(since.getNodeValue())));
  }

  public static List<ParsedApiClass> getParsedApiClasses(
      File apiVersionsXml, AndroidApiLevel apiLevel) throws Exception {
    AndroidApiVersionsXmlParser parser = new AndroidApiVersionsXmlParser(apiVersionsXml, apiLevel);
    parser.readApiVersionsXmlFile();
    return parser.classes;
  }

  public static class ParsedApiClass {

    private final ClassReference classReference;
    private final AndroidApiLevel apiLevel;
    private final TreeMap<AndroidApiLevel, List<FieldReference>> fieldReferences = new TreeMap<>();
    private final Map<AndroidApiLevel, List<MethodReference>> methodReferences = new TreeMap<>();

    public ClassReference getClassReference() {
      return classReference;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }

    private ParsedApiClass(ClassReference classReference, AndroidApiLevel apiLevel) {
      this.classReference = classReference;
      this.apiLevel = apiLevel;
    }

    private void register(FieldReference reference, AndroidApiLevel apiLevel) {
      fieldReferences.computeIfAbsent(apiLevel, ignoreArgument(ArrayList::new)).add(reference);
    }

    private void register(MethodReference reference, AndroidApiLevel apiLevel) {
      methodReferences.computeIfAbsent(apiLevel, ignoreArgument(ArrayList::new)).add(reference);
    }

    public void visitFieldReferences(BiConsumer<AndroidApiLevel, List<FieldReference>> consumer) {
      fieldReferences.forEach(
          (apiLevel, references) -> {
            references.sort(Comparator.comparing(FieldReference::getFieldName));
            consumer.accept(apiLevel, references);
          });
    }

    public void visitMethodReferences(BiConsumer<AndroidApiLevel, List<MethodReference>> consumer) {
      methodReferences.forEach(
          (apiLevel, references) -> {
            references.sort(Comparator.comparing(MethodReference::getMethodName));
            consumer.accept(apiLevel, references);
          });
    }
  }
}
