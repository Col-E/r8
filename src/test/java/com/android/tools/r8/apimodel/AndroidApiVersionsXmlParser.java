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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  private ParsedApiClass register(
      ClassReference reference, AndroidApiLevel apiLevel, boolean isInterface) {
    ParsedApiClass parsedApiClass = new ParsedApiClass(reference, apiLevel, isInterface);
    classes.add(parsedApiClass);
    return parsedApiClass;
  }

  private Set<String> getDeletedTypesMissingRemovedAttribute() {
    Set<String> removedTypeNames = new HashSet<>();
    if (maxApiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.U)) {
      removedTypeNames.add("com.android.internal.util.Predicate");
      removedTypeNames.add("android.adservices.AdServicesVersion");
    }
    return removedTypeNames;
  }

  private void readApiVersionsXmlFile() throws Exception {
    CodeInspector inspector = new CodeInspector(ToolHelper.getAndroidJar(maxApiLevel));
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    Document document = factory.newDocumentBuilder().parse(apiVersionsXml);
    NodeList classes = document.getElementsByTagName("class");
    Set<String> exemptionList = getDeletedTypesMissingRemovedAttribute();
    for (int i = 0; i < classes.getLength(); i++) {
      Node node = classes.item(i);
      assert node.getNodeType() == Node.ELEMENT_NODE;
      AndroidApiLevel apiLevel = getMaxAndroidApiLevelFromNode(node, AndroidApiLevel.B);
      String type = DescriptorUtils.getJavaTypeFromBinaryName(getName(node));
      ClassSubject clazz = inspector.clazz(type);
      if (!clazz.isPresent()) {
        if (!clazz.getOriginalName().startsWith("android.test")
            && !clazz.getOriginalName().startsWith("junit")) {
          assert exemptionList.contains(type) || hasRemoved(node);
          assert exemptionList.contains(type) || getRemoved(node).isLessThanOrEqualTo(maxApiLevel);
          if (!hasRemoved(node)) {
            exemptionList.remove(type);
          }
        }
        continue;
      }
      ClassReference originalReference = clazz.getOriginalReference();
      ParsedApiClass parsedApiClass = register(originalReference, apiLevel, clazz.isInterface());
      NodeList members = node.getChildNodes();
      for (int j = 0; j < members.getLength(); j++) {
        Node memberNode = members.item(j);
        if (isExtends(memberNode)) {
          parsedApiClass.registerSuperType(
              Reference.classFromBinaryName(getName(memberNode)),
              hasSince(memberNode) ? getSince(memberNode) : apiLevel);
        } else if (isImplements(memberNode)) {
          parsedApiClass.registerInterface(
              Reference.classFromBinaryName(getName(memberNode)),
              hasSince(memberNode) ? getSince(memberNode) : apiLevel);
        } else if (isMethod(memberNode)) {
          parsedApiClass.register(
              getMethodReference(originalReference, memberNode),
              getMaxAndroidApiLevelFromNode(memberNode, apiLevel));
        } else if (isField(memberNode)) {
          // The field do not have descriptors and are supposed to be unique.
          FieldSubject fieldSubject = clazz.uniqueFieldWithOriginalName(getName(memberNode));
          if (!fieldSubject.isPresent()) {
            assert hasRemoved(memberNode);
            assert getRemoved(memberNode).isLessThanOrEqualTo(maxApiLevel);
            continue;
          }
          parsedApiClass.register(
              fieldSubject.getOriginalReference(),
              getMaxAndroidApiLevelFromNode(memberNode, apiLevel));
        }
      }
    }
    assert exemptionList.isEmpty();
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

  private boolean isExtends(Node node) {
    return node.getNodeName().equals("extends");
  }

  private boolean isImplements(Node node) {
    return node.getNodeName().equals("implements");
  }

  private boolean hasSince(Node node) {
    return node.getAttributes().getNamedItem("since") != null;
  }

  private boolean hasRemoved(Node node) {
    return node.getAttributes().getNamedItem("removed") != null;
  }

  private AndroidApiLevel getSince(Node node) {
    assert hasSince(node);
    Node since = node.getAttributes().getNamedItem("since");
    return AndroidApiLevel.getAndroidApiLevel(Integer.parseInt(since.getNodeValue()));
  }

  private AndroidApiLevel getRemoved(Node node) {
    assert hasRemoved(node);
    Node removed = node.getAttributes().getNamedItem("removed");
    return AndroidApiLevel.getAndroidApiLevel(Integer.parseInt(removed.getNodeValue()));
  }

  private AndroidApiLevel getMaxAndroidApiLevelFromNode(Node node, AndroidApiLevel defaultValue) {
    if (node == null || !hasSince(node)) {
      return defaultValue;
    }
    return defaultValue.max(getSince(node));
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
    private final boolean isInterface;
    private final Map<ClassReference, AndroidApiLevel> superTypes = new LinkedHashMap<>();
    private final Map<ClassReference, AndroidApiLevel> interfaces = new LinkedHashMap<>();
    private final TreeMap<AndroidApiLevel, List<FieldReference>> fieldReferences = new TreeMap<>();
    private final Map<AndroidApiLevel, List<MethodReference>> methodReferences = new TreeMap<>();

    private ParsedApiClass(
        ClassReference classReference, AndroidApiLevel apiLevel, boolean isInterface) {
      this.classReference = classReference;
      this.apiLevel = apiLevel;
      this.isInterface = isInterface;
    }

    public ClassReference getClassReference() {
      return classReference;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }

    /**
     * getMemberCount() returns the total number of members present on the max api level, that is it
     * is the disjoint union of all members for all api levels.
     */
    public int getTotalMemberCount() {
      int count = 0;
      for (List<FieldReference> value : fieldReferences.values()) {
        count += value.size();
      }
      for (List<MethodReference> value : methodReferences.values()) {
        count += value.size();
      }
      return count;
    }

    private void register(FieldReference reference, AndroidApiLevel apiLevel) {
      fieldReferences.computeIfAbsent(apiLevel, ignoreArgument(ArrayList::new)).add(reference);
    }

    private void register(MethodReference reference, AndroidApiLevel apiLevel) {
      methodReferences.computeIfAbsent(apiLevel, ignoreArgument(ArrayList::new)).add(reference);
    }

    private void registerSuperType(ClassReference superType, AndroidApiLevel apiLevel) {
      AndroidApiLevel existing = superTypes.put(superType, apiLevel);
      assert existing == null;
    }

    private void registerInterface(ClassReference iface, AndroidApiLevel apiLevel) {
      AndroidApiLevel existing = interfaces.put(iface, apiLevel);
      assert existing == null;
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

    public void visitSuperType(BiConsumer<ClassReference, AndroidApiLevel> consumer) {
      superTypes.forEach(consumer);
    }

    public void visitInterface(BiConsumer<ClassReference, AndroidApiLevel> consumer) {
      interfaces.forEach(consumer);
    }

    public void amendCovariantMethod(MethodReference methodReference, AndroidApiLevel apiLevel) {
      register(methodReference, apiLevel);
    }

    public boolean isInterface() {
      return isInterface;
    }
  }
}
