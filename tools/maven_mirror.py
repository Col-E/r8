#!/usr/bin/env python
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import gradle
import fnmatch
import os
import re
import sys
import utils
import xml.etree.ElementTree as ET

CLOUD_LOCATION = 'gs://r8-deps/maven_mirror'

GRADLE_CACHE = os.path.join(utils.USER_HOME, '.gradle', 'caches')

def parse_arguments():
  parser = argparse.ArgumentParser()
  parser.add_argument('--check_mirror',
      help = 'Checks that all dependencies are  mirrored, '
          'returns non-zero if not.',
      default = False,
      action = 'store_true')
  return parser.parse_args()


def xml_get(element, tag):
  # The tags are prefixed with like: {http://maven.apache.org/POM/4.0.0}parent
  for child in element.iter():
    if child.tag.endswith(tag):
      yield child

def xml_get_single(element, tag):
  elements = list(xml_get(element, tag))
  if len(elements) == 0:
    return None
  assert len(elements) == 1
  return elements[0]

def find_pom_in_gradle_cache(pom, cached_poms):
  cached = [p for p in cached_poms if p.endswith(pom)]
  assert len(cached) == 1, 'did not find %s in %s' % (pom, cached_poms)
  return cached[0]

# This is a hack, gradle does not provide access to parent poms, which
# we need to mirror.
def get_parent(pom_file, gradle_cached_poms):
  tree = ET.parse(pom_file)
  root = tree.getroot()
  ns = {'pom': 'http://maven.apache.org/POM/4.0.0' }
  parent = root.find('pom:parent', ns)
  if parent is not None:
    group = parent.find('pom:groupId', ns).text
    artifact = parent.find('pom:artifactId', ns).text
    version = parent.find('pom:version', ns).text
    filename = '%s-%s.pom' % (artifact, version)
    parent_path = find_pom_in_gradle_cache(filename, gradle_cached_poms)
    return Entry(group, artifact, version, parent_path)

# Returns a tuple: (group, artifact, version)
def parse_descriptor(descriptor):
  # Descriptor like org.ow2.asm:asm:6.2.1
  split = descriptor.split(':')
  return (split[0], split[1], split[2])

class Entry(object):
  def __init__(self, group, artifact, version, path):
    self.group = group
    self.artifact = artifact
    self.version = version
    self.path = path
    assert os.path.exists(self.path)
    self.jar = None

  def set_jar(self, jar):
    self.jar = jar
    assert os.path.exists(jar)

  def get_cloud_dir(self):
    return os.path.join(CLOUD_LOCATION, '/'.join(self.group.split('.')))

  def get_name(self):
    return '%s-%s' % (self.artifact, self.version)

  def get_cloud_destination(self):
    return os.path.join(self.get_cloud_dir(), self.artifact, self.version,
                        self.get_name())

  def get_cloud_jar_location(self):
    assert self.jar is not None
    suffix = self.jar[len(self.jar)-4:]
    return self.get_cloud_destination() + suffix

  def get_cloud_pom_location(self):
    return self.get_cloud_destination() + '.pom'

def read_gradle_cache_pom_files():
  pom_files = []
  for root, dirnames, filenames in os.walk(GRADLE_CACHE):
    for filename in fnmatch.filter(filenames, '*.pom'):
      pom_files.append(os.path.join(root, filename))
  return pom_files

# We set the name to be group:artifact:version, same as gradle prints
def get_descriptor_from_path(entry):
  # Example
  # /usr.../org.ow2.asm/asm/6.2.1/3bc91be104d9292ff1dcc3dbf1002b7c320e767d/asm-6.2.1.pom
  basename = os.path.basename(entry)
  dirname = os.path.dirname(os.path.dirname(entry))
  version = os.path.basename(dirname)
  dirname = os.path.dirname(dirname)
  artifact = os.path.basename(dirname)
  dirname = os.path.dirname(dirname)
  group = os.path.basename(dirname)
  # Sanity, filename is artifact-version.{pom,jar}
  assert '%s-%s' % (artifact, version) == basename[0:len(basename)-4]
  return '%s:%s:%s' % (group, artifact, version)

def Main():
  args = parse_arguments()
  # Ensure that everything is downloaded before generating the pom list
  gradle.RunGradle(['-stop'])
  gradle_deps = gradle.RunGradleGetOutput(
      ['printMavenDeps', '-Pupdatemavendeps']).splitlines()
  gradle_poms = read_gradle_cache_pom_files()

  # Example output lines:
  # POM: /usr.../org.ow2.asm/asm/6.2.1/3bc91be104d9292ff1dcc3dbf1002b7c320e767d/asm-6.2.1.pom org.ow2.asm:asm:6.2.1
  # JAR: /usr.../com.google.code.gson/gson/2.7/751f548c85fa49f330cecbb1875893f971b33c4e/gson-2.7.jar
  poms = [l[5:] for l in gradle_deps if l.startswith('POM: ')]
  jars = [l[5:] for l in gradle_deps if l.startswith('JAR: ')]
  descriptor_to_entry = {}
  parents = []
  for pom in poms:
    split = pom.split(' ')
    filepath = split[0]
    gradle_descriptor = split[1]
    descriptor = get_descriptor_from_path(filepath)
    assert descriptor == gradle_descriptor
    (group, artifact, version) = parse_descriptor(gradle_descriptor)
    descriptor_to_entry[descriptor] = Entry(group, artifact, version, filepath)
    parent = get_parent(filepath, gradle_poms)
    while parent:
      descriptor = get_descriptor_from_path(parent.path)
      descriptor_to_entry[descriptor] = parent
      parent = get_parent(parent.path, gradle_poms)
  for jar in jars:
    if jar.startswith(utils.REPO_ROOT):
      continue
    descriptor = get_descriptor_from_path(jar)
    assert descriptor in descriptor_to_entry
    descriptor_to_entry[descriptor].set_jar(jar)
  has_missing = False
  for descriptor in descriptor_to_entry:
    entry = descriptor_to_entry[descriptor]
    if not utils.file_exists_on_cloud_storage(entry.get_cloud_pom_location()):
      if args.check_mirror:
        has_missing = True
        print 'Missing dependency for: ' + descriptor
      else:
        print 'Uploading: %s' % entry.path
        utils.upload_file_to_cloud_storage(entry.path, entry.get_cloud_pom_location())
    if entry.jar:
      if not utils.file_exists_on_cloud_storage(entry.get_cloud_jar_location()):
        if args.check_mirror:
          has_missing = True
          print 'Missing dependency for: ' + descriptor
        else:
          print 'Uploading: %s' % entry.jar
          utils.upload_file_to_cloud_storage(entry.jar, entry.get_cloud_jar_location())

  if args.check_mirror:
    if has_missing:
      print('The maven mirror has missing dependencies, please run'
            'tools/maven_mirror.py')
      return 1
    else:
      print('Mirror is up to date with all dependencies')

if __name__ == '__main__':
  sys.exit(Main())
