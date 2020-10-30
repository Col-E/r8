#!/usr/bin/env python
# Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import gradle
import hashlib
import jdk
import json
from os import makedirs
from os.path import join
from shutil import copyfile, make_archive, move, rmtree
import subprocess
import sys
from string import Template
import tempfile
import utils
import zipfile

DEPENDENCYTEMPLATE = Template(
"""
    <dependency>
        <groupId>$group</groupId>
        <artifactId>$artifact</artifactId>
        <version>$version</version>
    </dependency>""")

LICENSETEMPLATE = Template(
"""
    <license>
      <name>$name</name>
      <url>$url</url>
      <distribution>repo</distribution>
    </license>""")

R8_POMTEMPLATE = Template(
"""<project
    xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.android.tools</groupId>
  <artifactId>r8</artifactId>
  <version>$version</version>
  <name>D8 dexer and R8 shrinker</name>
  <description>
  D8 dexer and R8 shrinker.
  </description>
  <url>http://r8.googlesource.com/r8</url>
  <inceptionYear>2016</inceptionYear>
  <licenses>
    <license>
      <name>BSD-3-Clause</name>
      <url>https://opensource.org/licenses/BSD-3-Clause</url>
      <distribution>repo</distribution>
    </license>$library_licenses
  </licenses>
  <dependencies>$dependencies
  </dependencies>
  <developers>
    <developer>
      <name>The Android Open Source Project</name>
    </developer>
  </developers>
  <scm>
    <connection>
      https://r8.googlesource.com/r8.git
    </connection>
    <url>
      https://r8.googlesource.com/r8
    </url>
  </scm>
</project>
""")

DESUGAR_CONFIGUATION_POMTEMPLATE = Template(
"""<project
    xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.android.tools</groupId>
  <artifactId>desugar_jdk_libs_configuration</artifactId>
  <version>$version</version>
  <name>D8 configuration to desugar desugar_jdk_libs</name>
  <description>
  D8 configuration to desugar desugar_jdk_libs.
  </description>
  <url>http://r8.googlesource.com/r8</url>
  <inceptionYear>2019</inceptionYear>
  <licenses>
    <license>
      <name>BSD-3-Clause</name>
      <url>https://opensource.org/licenses/BSD-3-Clause</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <developers>
    <developer>
      <name>The Android Open Source Project</name>
    </developer>
  </developers>
  <scm>
    <connection>
      https://r8.googlesource.com/r8.git
    </connection>
    <url>
      https://r8.googlesource.com/r8
    </url>
  </scm>
</project>
""")

def parse_options(argv):
  result = argparse.ArgumentParser()
  result.add_argument('--out', help='The zip file to output')
  result.add_argument('--r8lib', action='store_true',
                      help='Build r8 with dependencies included shrunken')
  result.add_argument('--desugar-configuration', action='store_true',
                      help='Build r8 with dependencies included shrunken')
  return result.parse_args(argv)

def determine_version():
  version_file = join(
      utils.SRC_ROOT, 'com', 'android', 'tools', 'r8', 'Version.java')
  with open(version_file, 'r') as file:
    for line in file:
      if 'final String LABEL ' in line:
        result = line[line.find('"') + 1:]
        result = result[:result.find('"')]
        return result
  raise Exception('Unable to determine version.')

def generate_library_licenses():
  artifact_prefix = '- artifact: '
  license_prefix = 'license: '
  licenses = []
  license_url_prefix = 'licenseUrl: '
  license_urls = []
  # The ./LIBRARY-LICENSE file is a simple yaml file, which for each dependency
  # has the following information:
  #
  # - artifact: <maven artifact>  // in the form <group-id>:<artifact-id>:+
  #   name: <name of dependency>
  #   copyrightHolder: <name of copyright holder>
  #   license: <license name>
  #   licenseUrl: <url to license test>
  #
  # E.g. for Guava:
  #
  # - artifact: com.google.guava:guava:+
  #   name: Guava Google Core Libraries for Java
  #   copyrightHolder: The Guava Authors
  #   license: The Apache Software License, Version 2.0
  #   licenseUrl: http://www.apache.org/licenses/LICENSE-2.0.txt
  #
  # This file should always be up to date as the build will fail if it
  # is does not have information for all dependencies.
  with open('LIBRARY-LICENSE', 'r') as file:
    name = None
    url = None
    for line in file:
      trimmed = line.strip()
      # Collect license name and url for each artifact. They must come in
      # pairs for each artifact.
      if trimmed.startswith(artifact_prefix):
        assert not name
        assert not url
      if trimmed.startswith(license_prefix):
        name = trimmed[len(license_prefix):]
      if trimmed.startswith(license_url_prefix):
        url = trimmed[len(license_url_prefix):]
      # Licenses come in name/url pairs. When both are present add pair
      # to collected licenses if either name or url has not been recorded yet,
      # as some licenses with slightly different names point to the same url.
      if name and url:
        if (not name in licenses) or (not url in license_urls):
          licenses.append(name)
          license_urls.append(url)
        name = None
        url = None
      assert len(licenses) == len(license_urls)
  result = ''
  for i in range(len(licenses)):
    name = licenses[i]
    url = license_urls[i]
    result += LICENSETEMPLATE.substitute(name=name, url=url)
  return result


# Generate the dependencies block for the pom file.
#
# We ask gradle to list all dependencies. In that output
# we locate the runtimeClasspath block for 'main' which
# looks something like:
#
# runtimeClasspath - Runtime classpath of source set 'main'.
# +--- net.sf.jopt-simple:jopt-simple:4.6
# +--- com.googlecode.json-simple:json-simple:1.1
# +--- com.google.guava:guava:23.0
# +--- it.unimi.dsi:fastutil:7.2.0
# +--- org.ow2.asm:asm:6.0
# +--- org.ow2.asm:asm-commons:6.0
# |    \--- org.ow2.asm:asm-tree:6.0
# |         \--- org.ow2.asm:asm:6.0
# +--- org.ow2.asm:asm-tree:6.0 (*)
# +--- org.ow2.asm:asm-analysis:6.0
# |    \--- org.ow2.asm:asm-tree:6.0 (*)
# \--- org.ow2.asm:asm-util:6.0
#      \--- org.ow2.asm:asm-tree:6.0 (*)
#
# We filter out the repeats that are marked by '(*)'.
#
# For each remaining line, we remove the junk at the start
# in chunks. As an example:
#
# '  |    \--- org.ow2.asm:asm-tree:6.0  '  --strip-->
# '|    \--- org.ow2.asm:asm-tree:6.0'  -->
# '\--- org.ow2.asm:asm-tree:6.0'  -->
# 'org.ow2.asm:asm-tree:6.0'
#
# The end result is the dependency we are looking for:
#
# groupId: org.ow2.asm
# artifact: asm-tree
# version: 6.0
def generate_dependencies():
  dependencies = gradle.RunGradleGetOutput(['dependencies'])
  dependency_lines = []
  collect = False
  for line in dependencies.splitlines():
    if 'runtimeClasspath' in line and "'main'" in line:
      collect = True
      continue
    if collect:
      if not len(line) == 0:
        if not '(*)' in line:
          trimmed = line.strip()
          while trimmed.find(' ') != -1:
            trimmed = trimmed[trimmed.find(' ') + 1:].strip()
          if not trimmed in dependency_lines:
            dependency_lines.append(trimmed)
      else:
        break
  result = ''
  for dep in dependency_lines:
    components = dep.split(':')
    assert len(components) == 3
    group = components[0]
    artifact = components[1]
    version = components[2]
    result += DEPENDENCYTEMPLATE.substitute(
        group=group, artifact=artifact, version=version)
  return result

def write_default_r8_pom_file(pom_file, version):
  write_pom_file(R8_POMTEMPLATE, pom_file, version, generate_dependencies(), '')

def write_pom_file(template, pom_file, version, dependencies='', library_licenses=''):
  version_pom = template.substitute(
      version=version, dependencies=dependencies, library_licenses=library_licenses)
  with open(pom_file, 'w') as file:
    file.write(version_pom)

def hash_for(file, hash):
  with open(file, 'rb') as f:
    while True:
      # Read chunks of 1MB
      chunk = f.read(2 ** 20)
      if not chunk:
        break
      hash.update(chunk)
  return hash.hexdigest()

def write_md5_for(file):
  hexdigest = hash_for(file, hashlib.md5())
  with (open(file + '.md5', 'w')) as file:
    file.write(hexdigest)

def write_sha1_for(file):
  hexdigest = hash_for(file, hashlib.sha1())
  with (open(file + '.sha1', 'w')) as file:
    file.write(hexdigest)

def generate_maven_zip(name, version, pom_file, jar_file, out):
  with utils.TempDir() as tmp_dir:
    # Create the base maven version directory
    version_dir = join(tmp_dir, utils.get_maven_path(name, version))
    makedirs(version_dir)
    # Write the pom file.
    pom_file_location = join(version_dir, name + '-' + version + '.pom')
    copyfile(pom_file, pom_file_location)
    # Write the jar file.
    jar_file_location = join(version_dir, name + '-' + version + '.jar')
    copyfile(jar_file, jar_file_location)
    # Create check sums.
    write_md5_for(jar_file_location)
    write_md5_for(pom_file_location)
    write_sha1_for(jar_file_location)
    write_sha1_for(pom_file_location)
    # Zip it up - make_archive will append zip to the file, so remove.
    assert out.endswith('.zip')
    base_no_zip = out[0:len(out)-4]
    make_archive(base_no_zip, 'zip', tmp_dir)

def generate_r8_maven_zip(out, is_r8lib=False):
  # Build the R8 no deps artifact.
  if not is_r8lib:
    gradle.RunGradleExcludeDeps([utils.R8])
  else:
    gradle.RunGradle([utils.R8LIB, '-Pno_internal'])

  version = determine_version()
  with utils.TempDir() as tmp_dir:
    # Generate the pom file.
    pom_file = join(tmp_dir, 'r8.pom')
    write_pom_file(
        R8_POMTEMPLATE,
        pom_file,
        version,
        "" if is_r8lib else generate_dependencies(),
        generate_library_licenses() if is_r8lib else "")
    # Write the maven zip file.
    generate_maven_zip(
        'r8',
        version,
        pom_file,
        utils.R8LIB_JAR if is_r8lib else utils.R8_JAR,
        out)

# Write the desugaring configuration of a jar file with the following content:
#  java/
#    util/
#      <java.util conversions classes>
#    time/
#      <java.time conversions classes>
#  META-INF/
#    desugar/
#      d8/
#        desugar.json
#        lint/
#          <lint files>
def generate_jar_with_desugar_configuration(
    configuration, implementation, conversions, destination):
  with utils.TempDir() as tmp_dir:
    # Add conversion classes.
    with zipfile.ZipFile(conversions, 'r') as conversions_zip:
      conversions_zip.extractall(tmp_dir)

    # Add configuration
    configuration_dir = join(tmp_dir, 'META-INF', 'desugar', 'd8')
    makedirs(configuration_dir)
    copyfile(configuration, join(configuration_dir, 'desugar.json'))

    # Add lint configuartion.
    lint_dir = join(configuration_dir, 'lint')
    makedirs(lint_dir)
    cmd = [
        jdk.GetJavaExecutable(),
        '-cp',
        utils.R8_JAR,
        'com.android.tools.r8.GenerateLintFiles',
        configuration,
        implementation,
        lint_dir]
    utils.PrintCmd(cmd)
    subprocess.check_call(cmd)

    make_archive(destination, 'zip', tmp_dir)
    move(destination + '.zip', destination)

# Generate the maven zip for the configuration to desugar desugar_jdk_libs.
def generate_desugar_configuration_maven_zip(out):
  with utils.TempDir() as tmp_dir:
    version = utils.desugar_configuration_version()
    # Generate the pom file.
    pom_file = join(tmp_dir, 'desugar_configuration.pom')
    write_pom_file(DESUGAR_CONFIGUATION_POMTEMPLATE, pom_file, version)
    # Generate the jar with the configuration file.
    jar_file = join(tmp_dir, 'desugar_configuration.jar')
    generate_jar_with_desugar_configuration(
        utils.DESUGAR_CONFIGURATION,
        utils.DESUGAR_IMPLEMENTATION,
        utils.LIBRARY_DESUGAR_CONVERSIONS_ZIP,
        jar_file)
    # Write the maven zip file.
    generate_maven_zip(
        'desugar_jdk_libs_configuration', version, pom_file, jar_file, out)

def main(argv):
  options = parse_options(argv)
  if options.r8lib and options.desugar_configuration:
    raise Exception(
        'Cannot combine options --r8lib and --desugar-configuration')
  if options.out == None:
    raise Exception(
        'Need to supply output zip with --out.')
  if options.desugar_configuration:
    generate_desugar_configuration_maven_zip(options.out)
  else:
    generate_r8_maven_zip(options.out, options.r8lib)

if __name__ == "__main__":
  exit(main(sys.argv[1:]))
