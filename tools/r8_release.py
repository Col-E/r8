#!/usr/bin/env python3
# Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import argparse
import datetime
import os.path
import re
import stat
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree
import zipfile

import utils

R8_DEV_BRANCH = '8.3'
R8_VERSION_FILE = os.path.join(
    'src', 'main', 'java', 'com', 'android', 'tools', 'r8', 'Version.java')
THIS_FILE_RELATIVE = os.path.join('tools', 'r8_release.py')
GMAVEN_PUBLISHER = '/google/bin/releases/android-devtools/gmaven/publisher/gmaven-publisher'

DESUGAR_JDK_LIBS = 'desugar_jdk_libs'
DESUGAR_JDK_LIBS_CONFIGURATION = DESUGAR_JDK_LIBS + '_configuration'
ANDROID_TOOLS_PACKAGE = 'com.android.tools'

GITHUB_DESUGAR_JDK_LIBS = 'https://github.com/google/desugar_jdk_libs'

def install_gerrit_change_id_hook(checkout_dir):
  with utils.ChangedWorkingDirectory(checkout_dir):
    # Fancy way of getting the string ".git".
    git_dir = subprocess.check_output(
        ['git', 'rev-parse', '--git-dir']).decode('utf-8').strip()
    commit_msg_hooks = '%s/hooks/commit-msg' % git_dir
    if not os.path.exists(os.path.dirname(commit_msg_hooks)):
      os.mkdir(os.path.dirname(commit_msg_hooks))
    # Install commit hook to generate Gerrit 'Change-Id:'.
    urllib.request.urlretrieve(
        'https://gerrit-review.googlesource.com/tools/hooks/commit-msg',
        commit_msg_hooks)
    st = os.stat(commit_msg_hooks)
    os.chmod(
        commit_msg_hooks,
        st.st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

def checkout_r8(temp, branch):
  subprocess.check_call(['git', 'clone', utils.REPO_SOURCE, temp])
  with utils.ChangedWorkingDirectory(temp):
    subprocess.check_call([
      'git',
      'new-branch',
      '--upstream',
      'origin/%s' % branch,
      'dev-release'])
  install_gerrit_change_id_hook(temp)
  return temp


def prepare_release(args):
  if args.version:
    print("Cannot manually specify version when making a dev release.")
    sys.exit(1)

  def make_release(args):
    commithash = args.dev_release

    with utils.TempDir() as temp:
      with utils.ChangedWorkingDirectory(checkout_r8(temp, R8_DEV_BRANCH)):
        # Compute the current and new version on the branch.
        result = None
        for line in open(R8_VERSION_FILE, 'r'):
          result = re.match(
              r'.*LABEL = "%s\.(\d+)\-dev";' % R8_DEV_BRANCH, line)
          if result:
            break
        if not result or not result.group(1):
          print('Failed to find version label matching %s(\d+)-dev'\
                % R8_DEV_BRANCH)
          sys.exit(1)
        try:
          patch_version = int(result.group(1))
        except ValueError:
          print('Failed to convert version to integer: %s' % result.group(1))

        old_version = '%s.%s-dev' % (R8_DEV_BRANCH, patch_version)
        version = '%s.%s-dev' % (R8_DEV_BRANCH, patch_version + 1)

        # Verify that the merge point from main is not empty.
        merge_diff_output = subprocess.check_output([
            'git', 'diff', 'HEAD..%s' % commithash]).decode('utf-8')
        other_diff = version_change_diff(
            merge_diff_output, old_version, "main")
        if not other_diff:
          print('Merge point from main (%s)' % commithash, \
            'is the same as exiting release (%s).' % old_version)
          sys.exit(1)

        subprocess.check_call([
            'git', 'cl', 'new-branch', 'release-%s' % version])

        if args.dev_pre_cherry_pick:
          for pre_commit in args.dev_pre_cherry_pick:
            subprocess.check_call([
                'git', 'cherry-pick', '--no-edit', pre_commit])

        # Merge the desired commit from main on to the branch.
        subprocess.check_call(['git', 'merge', '--no-ff', '--no-edit', commithash])

        # Rewrite the version, commit and validate.
        sed(old_version, version, R8_VERSION_FILE)

        subprocess.check_call([
          'git', 'commit', '-a', '-m', 'Version %s' % version])

        version_diff_output = subprocess.check_output([
          'git', 'diff', '%s..HEAD' % commithash]).decode('utf-8')

        validate_version_change_diff(version_diff_output, "main", version)

        maybe_check_call(args, ['git', 'cl', 'upload', '--no-squash'])

        if args.dry_run:
          input(
              'DryRun: check %s for content of version %s [enter to continue]:'
              % (temp, version))

        return "%s dev version %s from hash %s for review" % (
          'DryRun: omitted upload of' if args.dry_run else 'Uploaded',
          version,
          commithash)

  return make_release


def maybe_tag(args, version):
  maybe_check_call(args, [
    'git', 'tag', '-a', version, '-m', '"%s"' % version])
  maybe_check_call(args, [
    'git', 'push', 'origin', 'refs/tags/%s' % version])


def version_change_diff(diff, old_version, new_version):
  invalid_line = None
  for line in str(diff).splitlines():
    if line.startswith('-  ') and \
        line != '-  public static final String LABEL = "%s";' % old_version:
      invalid_line = line
    elif line.startswith('+  ') and \
        line != '+  public static final String LABEL = "%s";' % new_version:
      invalid_line = line
  return invalid_line


def validate_version_change_diff(version_diff_output, old_version, new_version):
  invalid = version_change_diff(version_diff_output, old_version, new_version)
  if invalid:
    print("Unexpected diff:")
    print("=" * 80)
    print(version_diff_output)
    print("=" * 80)
    accept_string = 'THE DIFF IS OK!'
    answer = input(
      "Accept the additonal diff as part of the release? "
      "Type '%s' to accept: " % accept_string)
    if answer != accept_string:
      print("You did not type '%s'" % accept_string)
      print('Aborting dev release for %s' % version)
      sys.exit(1)


def maybe_check_call(args, cmd):
  if args.dry_run:
    print('DryRun:', ' '.join(cmd))
  else:
    print(' '.join(cmd))
    return subprocess.check_call(cmd)


def update_prebuilds(r8_checkout, version, checkout, keepanno=False):
  path = os.path.join(r8_checkout, 'tools', 'update_prebuilds_in_android.py')
  commit_arg = '--commit_hash=' if len(version) == 40 else '--version='
  cmd = [path, '--targets=lib', '--maps', commit_arg + version, checkout]
  if keepanno:
    cmd.append("--keepanno")
  subprocess.check_call(cmd)


def release_studio_or_aosp(r8_checkout, path, options, git_message, keepanno=False):
  with utils.ChangedWorkingDirectory(path):
    if not options.use_existing_work_branch:
      subprocess.call(['repo', 'abandon', 'update-r8'])
    if not options.no_sync:
      subprocess.check_call(['repo', 'sync', '-cq', '-j', '16'])

    prebuilts_r8 = os.path.join(path, 'prebuilts', 'r8')

    if not options.use_existing_work_branch:
      with utils.ChangedWorkingDirectory(prebuilts_r8):
        subprocess.check_call(['repo', 'start', 'update-r8'])

    update_prebuilds(r8_checkout, options.version, path, keepanno)

    with utils.ChangedWorkingDirectory(prebuilts_r8):
      if not options.use_existing_work_branch:
        subprocess.check_call(['git', 'commit', '-a', '-m', git_message])
      else:
        print('Not committing when --use-existing-work-branch. '
            + 'Commit message should be:\n\n'
            + git_message
            + '\n')
      # Don't upload if requested not to, or if changes are not committed due
      # to --use-existing-work-branch
      if not options.no_upload and not options.use_existing_work_branch:
        process = subprocess.Popen(['repo', 'upload', '.', '--verify',
                                    '--current-branch'],
                                   stdin=subprocess.PIPE)
        return process.communicate(input=b'y\n')[0]


def prepare_aosp(args):
  assert args.version
  assert os.path.exists(args.aosp), "Could not find AOSP path %s" % args.aosp

  def release_aosp(options):
    print("Releasing for AOSP")
    if options.dry_run:
      return 'DryRun: omitting AOSP release for %s' % options.version

    git_message = ("""Update D8 and R8 to %s

Version: %s
This build IS NOT suitable for preview or public release.

Built here: go/r8-releases/raw/%s

Test: TARGET_PRODUCT=aosp_arm64 m -j core-oj"""
                   % (args.version, args.version, args.version))
    # Fixes to Android U branch is based of 8.2.2-dev where the keepanno library
    # is not built.
    keepanno = not args.version.startswith('8.2.2-udc')
    return release_studio_or_aosp(
      utils.REPO_ROOT, args.aosp, options, git_message, keepanno=keepanno)

  return release_aosp


def prepare_maven(args):
  assert args.version

  def release_maven(options):
    gfile = '/bigstore/r8-releases/raw/%s/r8lib.zip' % args.version
    release_id = gmaven_publisher_stage(options, [gfile])

    print("Staged Release ID " + release_id + ".\n")
    gmaven_publisher_stage_redir_test_info(
        release_id, "com.android.tools:r8:%s" % args.version, "r8lib.jar")

    print
    answer = input("Continue with publishing [y/N]:")

    if answer != 'y':
      print('Aborting release to Google maven')
      sys.exit(1)

    gmaven_publisher_publish(args, release_id)

    print("")
    print("Published. Use the email workflow for approval.")

  return release_maven

# ------------------------------------------------------ column 70 --v
def git_message_dev(version, bugs):
  return """Update D8 R8 to %s

This is a development snapshot, it's fine to use for studio canary
build, but not for BETA or release, for those we would need a release
version of R8 binaries. This build IS suitable for preview release
but IS NOT suitable for public release.

Built here: go/r8-releases/raw/%s
Test: ./gradlew check
Bug: %s""" % (version, version, '\nBug: '.join(map(bug_fmt, bugs)))


def git_message_release(version, bugs):
  return """D8 R8 version %s

Built here: go/r8-releases/raw/%s/
Test: ./gradlew check

Bug: %s""" % (version, version, '\nBug: '.join(map(bug_fmt, bugs)))

def bug_fmt(bug):
  return "b/%s" % bug

def prepare_studio(args):
  assert args.version
  assert os.path.exists(args.studio), ("Could not find STUDIO path %s"
                                       % args.studio)

  def release_studio(options):
    print("Releasing for STUDIO")
    if options.dry_run:
      return 'DryRun: omitting studio release for %s' % options.version

    if 'dev' in options.version:
      git_message = git_message_dev(options.version, options.bug)
      r8_checkout = utils.REPO_ROOT
      return release_studio_or_aosp(
        r8_checkout, args.studio, options, git_message)
    else:
      with utils.TempDir() as temp:
        checkout_r8(temp, options.version[0:options.version.rindex('.')])
        git_message = git_message_release(options.version, options.bug)
        return release_studio_or_aosp(temp, args.studio, options, git_message)

  return release_studio


def g4_cp(old, new, file):
  subprocess.check_call('g4 cp {%s,%s}/%s' % (old, new, file), shell=True)


def g4_open(file):
  if not os.access(file, os.W_OK):
    subprocess.check_call('g4 open %s' % file, shell=True)


def g4_change(version):
  return subprocess.check_output(
      'g4 change --desc "Update R8 to version %s\n"' % (version),
      shell=True).decode('utf-8')

def get_cl_id(c4_change_output):
  startIndex = c4_change_output.find('Change ') + len('Change ')
  endIndex = c4_change_output.find(' ', startIndex)
  cl = c4_change_output[startIndex:endIndex]
  assert cl.isdigit()
  return cl

def sed(pattern, replace, path):
  with open(path, "r") as sources:
    lines = sources.readlines()
  with open(path, "w") as sources:
    for line in lines:
      sources.write(re.sub(pattern, replace, line))


def download_file(version, file, dst):
  dir = 'raw' if len(version) != 40 else 'raw/main'
  urllib.request.urlretrieve(
      ('https://storage.googleapis.com/r8-releases/%s/%s/%s' % (dir, version, file)),
      dst)

def download_gfile(gfile, dst):
  if not gfile.startswith('/bigstore/r8-releases'):
    print('Unexpected gfile prefix for %s' % gfile)
    sys.exit(1)

  urllib.request.urlretrieve(
      'https://storage.googleapis.com/%s' % gfile[len('/bigstore/'):],
      dst)

def blaze_run(target):
  return subprocess.check_output(
      'blaze run %s' % target, shell=True, stderr=subprocess.STDOUT).decode('utf-8')


def prepare_google3(args):
  assert args.version
  # Check if an existing client exists.
  if not args.use_existing_work_branch:
    check_no_google3_client(args, args.p4_client)

  def release_google3(options):
    print("Releasing for Google 3")
    if options.dry_run:
      return 'DryRun: omitting g3 release for %s' % options.version

    google3_base = subprocess.check_output(
        ['p4', 'g4d', '-f', args.p4_client]).decode('utf-8').rstrip()
    third_party_r8 = os.path.join(google3_base, 'third_party', 'java', 'r8')
    today = datetime.date.today()
    with utils.ChangedWorkingDirectory(third_party_r8):
      # download files
      g4_open('full.jar')
      g4_open('src.jar')
      g4_open('lib.jar')
      g4_open('lib.jar.map')
      g4_open('retrace_full.jar')
      g4_open('retrace_lib.jar')
      g4_open('retrace_lib.jar.map')
      g4_open('desugar_jdk_libs_configuration.jar')
      download_file(options.version, 'r8-full-exclude-deps.jar', 'full.jar')
      download_file(options.version, 'r8-full-exclude-deps.jar', 'retrace_full.jar')
      download_file(options.version, 'r8-src.jar', 'src.jar')
      download_file(options.version, 'r8lib-exclude-deps.jar', 'lib.jar')
      download_file(
          options.version, 'r8lib-exclude-deps.jar.map', 'lib.jar.map')
      download_file(
          options.version, 'r8lib-exclude-deps.jar.map', 'retrace_lib.jar.map')
      download_file(options.version, 'desugar_jdk_libs_configuration.jar',
                    'desugar_jdk_libs_configuration.jar')
      download_file(options.version, 'r8retrace-exclude-deps.jar', 'retrace_lib.jar')
      g4_open('METADATA')
      metadata_path = os.path.join(third_party_r8, 'METADATA')
      match_count = 0
      version_match_regexp = r'[1-9]\.[0-9]{1,2}\.[0-9]{1,3}-dev'
      for line in open(metadata_path, 'r'):
        result = re.search(version_match_regexp, line)
        if result:
          match_count = match_count + 1
      if match_count != 7:
        print((
            "Could not find the previous -dev release string to replace in "
                    + "METADATA. Expected to find is mentioned 7 times, but "
                    + "found %s occurrences. Please update %s manually and run "
                    + "again  with options --google3 "
                    + "--use-existing-work-branch.")
                % (match_count, metadata_path))
        sys.exit(1)
      sed(version_match_regexp, options.version, metadata_path)
      sed(r'\{ year.*\}',
          ('{ year: %i month: %i day: %i }'
           % (today.year, today.month, today.day)),
          metadata_path)
      subprocess.check_output('chmod u+w *', shell=True)

    with utils.ChangedWorkingDirectory(google3_base):
      blaze_result = blaze_run('//third_party/java/r8:d8 -- --version')

      assert options.version in blaze_result

      if not options.no_upload:
        change_result = g4_change(options.version)
        change_result += 'Run \'(g4d ' + args.p4_client \
                         + ' && tap_presubmit -p all --train -c ' \
                         + get_cl_id(change_result) + ')\' for running TAP global' \
                         + ' presubmit using the train.\n' \
                         + 'Run \'(g4d ' + args.p4_client \
                         + ' && tap_presubmit -p all --notrain --detach --email' \
                         + ' --skip_flaky_targets --skip_already_failing -c ' \
                         + get_cl_id(change_result) + ')\' for running an isolated' \
                         + ' TAP global presubmit.'
        return change_result

  return release_google3


def prepare_google3_retrace(args):
  assert args.version
  # Check if an existing client exists.
  if not args.use_existing_work_branch:
    check_no_google3_client(args, args.p4_client)

  def release_google3_retrace(options):
    print("Releasing Retrace for Google 3")
    if options.dry_run:
      return 'DryRun: omitting g3 release for %s' % options.version

    google3_base = subprocess.check_output(
      ['p4', 'g4d', '-f', args.p4_client]).decode('utf-8').rstrip()
    third_party_r8 = os.path.join(google3_base, 'third_party', 'java', 'r8')
    with utils.ChangedWorkingDirectory(third_party_r8):
      # download files
      g4_open('retrace_full.jar')
      g4_open('retrace_lib.jar')
      g4_open('retrace_lib.jar.map')
      download_file(options.version, 'r8-full-exclude-deps.jar', 'retrace_full.jar')
      download_file(options.version, 'r8retrace-exclude-deps.jar', 'retrace_lib.jar')
      download_file(
        options.version, 'r8lib-exclude-deps.jar.map', 'retrace_lib.jar.map')
      g4_open('METADATA')
      metadata_path = os.path.join(third_party_r8, 'METADATA')
      match_count = 0
      version_match_regexp = r'[1-9]\.[0-9]{1,2}\.[0-9]{1,3}-dev/r8retrace-exclude-deps.jar'
      for line in open(metadata_path, 'r'):
        result = re.search(version_match_regexp, line)
        if result:
          match_count = match_count + 1
      if match_count != 1:
        print(("Could not find the previous retrace release string to replace in " +
               "METADATA. Expected to find is mentioned 1 times. Please update %s " +
               "manually and run again with options --google3retrace " +
               "--use-existing-work-branch.") % metadata_path)
        sys.exit(1)
      sed(version_match_regexp, options.version + "/r8retrace-exclude-deps.jar", metadata_path)
      subprocess.check_output('chmod u+w *', shell=True)

    with utils.ChangedWorkingDirectory(google3_base):
      blaze_result = blaze_run('//third_party/java/r8:retrace -- --version')

      print(blaze_result)
      assert options.version in blaze_result

      if not options.no_upload:
        change_result = g4_change(options.version)
        change_result += 'Run \'(g4d ' + args.p4_client \
                         + ' && tap_presubmit -p all --train -c ' \
                         + get_cl_id(change_result) + ')\' for running TAP global' \
                         + ' presubmit using the train.\n' \
                         + 'Run \'(g4d ' + args.p4_client \
                         + ' && tap_presubmit -p all --notrain --detach --email' \
                         + ' --skip_flaky_targets --skip_already_failing -c ' \
                         + get_cl_id(change_result) + ')\' for running an isolated' \
                         + ' TAP global presubmit.'
        return change_result

  return release_google3_retrace

def update_desugar_library_in_studio(args):
  assert os.path.exists(args.studio), ("Could not find STUDIO path %s"
                                       % args.studio)

  def make_release(args):
    library_version = args.update_desugar_library_in_studio[0]
    configuration_version = args.update_desugar_library_in_studio[1]
    change_name = 'update-desugar-library-dependencies'

    with utils.ChangedWorkingDirectory(args.studio):
      if not args.use_existing_work_branch:
        subprocess.call(['repo', 'abandon', change_name])
      if not args.no_sync:
        subprocess.check_call(['repo', 'sync', '-cq', '-j', '16'])

      cmd = ['tools/base/bazel/bazel',
         'run',
         '//tools/base/bazel:add_dependency',
         '--',
         '--repo=https://maven.google.com com.android.tools:desugar_jdk_libs:%s' % library_version]
      utils.PrintCmd(cmd)
      subprocess.check_call(" ".join(cmd), shell=True)
      cmd = ['tools/base/bazel/bazel', 'shutdown']
      utils.PrintCmd(cmd)
      subprocess.check_call(cmd)

    prebuilts_tools = os.path.join(args.studio, 'prebuilts', 'tools')
    with utils.ChangedWorkingDirectory(prebuilts_tools):
      if not args.use_existing_work_branch:
        with utils.ChangedWorkingDirectory(prebuilts_tools):
          subprocess.check_call(['repo', 'start', change_name])
      m2_dir = os.path.join(
        'common', 'm2', 'repository', 'com', 'android', 'tools')
      subprocess.check_call(
        ['git',
         'add',
         os.path.join(m2_dir, DESUGAR_JDK_LIBS, library_version)])
      subprocess.check_call(
        ['git',
         'add',
         os.path.join(
           m2_dir, DESUGAR_JDK_LIBS_CONFIGURATION, configuration_version)])

      git_message = ("""Update library desugaring dependencies

  com.android.tools:desugar_jdk_libs:%s
  com.android.tools:desugar_jdk_libs_configuration:%s

Bug: %s
Test: L8ToolTest, L8DexDesugarTest"""
                     % (library_version,
                        configuration_version,
                        '\nBug: '.join(map(bug_fmt, args.bug))))

      if not args.use_existing_work_branch:
        subprocess.check_call(['git', 'commit', '-a', '-m', git_message])
      else:
        print('Not committing when --use-existing-work-branch. '
            + 'Commit message should be:\n\n'
            + git_message
            + '\n')
      # Don't upload if requested not to, or if changes are not committed due
      # to --use-existing-work-branch
      if not args.no_upload and not args.use_existing_work_branch:
        process = subprocess.Popen(['repo', 'upload', '.', '--verify'],
                                   stdin=subprocess.PIPE)
        return process.communicate(input='y\n')[0]

  return make_release


def prepare_desugar_library(args):

  def make_release(args):
    library_version = args.desugar_library[0]
    configuration_version = args.desugar_library[1]

    # TODO(b/237636871): Cleanup and generalize.
    if (not (library_version.startswith('1.1')
        or library_version.startswith('1.2')
        or library_version.startswith('2.0'))):
      print("Release script does not support desugared library version %s"
        % library_version)
      sys.exit(1)

    postfixes = ['']
    if library_version.startswith('1.2'):
      postfixes = ['_legacy']
    if library_version.startswith('2.0'):
      postfixes = ['_minimal', '', '_nio']

    with utils.TempDir() as temp:
      with utils.ChangedWorkingDirectory(temp):
        artifacts = []
        for postfix in postfixes:
          group_postfix = ('' if postfix == '_legacy' else postfix)
          archive_postfix = (postfix if library_version.startswith('1.1') else '_jdk11' + postfix)
          library_jar = DESUGAR_JDK_LIBS + postfix + '.jar'
          library_archive = DESUGAR_JDK_LIBS + archive_postfix + '.zip'
          configuration_archive = DESUGAR_JDK_LIBS_CONFIGURATION + archive_postfix + '.zip'
          library_gfile = ('/bigstore/r8-releases/raw/%s/%s/%s'
                % (DESUGAR_JDK_LIBS + group_postfix, library_version, library_archive))
          configuration_gfile = ('/bigstore/r8-releases/raw/main/%s/%s'
                % (configuration_version, configuration_archive))

          download_gfile(library_gfile, library_archive)
          download_gfile(configuration_gfile, configuration_archive)
          check_configuration(configuration_archive, group_postfix)
          artifacts.append(library_gfile)
          artifacts.append(configuration_gfile)

        release_id = gmaven_publisher_stage(args, artifacts)

        print("Staged Release ID " + release_id + ".\n")
        library_artifact_id = \
            '%s:%s:%s' % (ANDROID_TOOLS_PACKAGE, DESUGAR_JDK_LIBS, library_version)
        gmaven_publisher_stage_redir_test_info(
            release_id,
            library_artifact_id,
            library_jar)

        print("")
        answer = input("Continue with publishing [y/N]:")

        if answer != 'y':
          print('Aborting release to Google maven')
          sys.exit(1)

        gmaven_publisher_publish(args, release_id)

        print("")
        print("Published. Use the email workflow for approval.")

  return make_release


def check_configuration(configuration_archive, postfix):
  zip = zipfile.ZipFile(configuration_archive)
  zip.extractall()
  dirs = os.listdir(
    os.path.join('com', 'android', 'tools', DESUGAR_JDK_LIBS_CONFIGURATION + postfix))
  if len(dirs) != 1:
    print('Unexpected archive content, %s' + dirs)
    sys.exit(1)

  version = dirs[0]
  pom_file = os.path.join(
    'com',
    'android',
    'tools',
    DESUGAR_JDK_LIBS_CONFIGURATION + postfix,
    version,
    '%s-%s.pom' % (DESUGAR_JDK_LIBS_CONFIGURATION + postfix, version))
  version_from_pom = extract_version_from_pom(pom_file)
  if version != version_from_pom:
    print('Version mismatch, %s != %s' % (version, version_from_pom))
    sys.exit(1)

def check_no_google3_client(args, client_name):
  if not args.use_existing_work_branch:
    clients = subprocess.check_output('g4 myclients', shell=True).decode('utf-8')
    if ':%s:' % client_name in clients:
      if args.delete_work_branch:
        subprocess.check_call('g4 citc -d -f %s' % client_name, shell=True)
      else:
        print(("Remove the existing '%s' client before continuing " +
               "(force delete: 'g4 citc -d -f %s'), " +
               "or use either --use-existing-work-branch or " +
               "--delete-work-branch.") % (client_name, client_name))
        sys.exit(1)


def extract_version_from_pom(pom_file):
    ns = "http://maven.apache.org/POM/4.0.0"
    xml.etree.ElementTree.register_namespace('', ns)
    tree = xml.etree.ElementTree.ElementTree()
    tree.parse(pom_file)
    return tree.getroot().find("{%s}version" % ns).text


GMAVEN_PUBLISH_STAGE_RELEASE_ID_PATTERN = re.compile('Release ID = ([0-9a-f\-]+)')


def gmaven_publisher_stage(args, gfiles):
  if args.dry_run:
    print('Dry-run, would have staged %s' % gfiles)
    return 'dry-run-release-id'

  print("Staging: %s" % ', '.join(gfiles))
  print("")

  cmd = [GMAVEN_PUBLISHER, 'stage', '--gfile', ','.join(gfiles)]
  output = subprocess.check_output(cmd)

  # Expect output to contain:
  # [INFO] 06/19/2020 09:35:12 CEST: >>>>>>>>>> Staged
  # [INFO] 06/19/2020 09:35:12 CEST: Release ID = 9171d015-18f6-4a90-9984-1c362589dc1b
  # [INFO] 06/19/2020 09:35:12 CEST: Stage Path = /bigstore/studio_staging/maven2/sgjesse/9171d015-18f6-4a90-9984-1c362589dc1b

  matches = GMAVEN_PUBLISH_STAGE_RELEASE_ID_PATTERN.findall(output.decode("utf-8"))
  if matches == None or len(matches) > 1:
    print("Could not determine the release ID from the gmaven_publisher " +
          "output. Expected a line with 'Release ID = <release id>'.")
    print("Output was:")
    print(output)
    sys.exit(1)

  print(output)

  release_id = matches[0]
  return release_id


def gmaven_publisher_stage_redir_test_info(release_id, artifact, dst):

  redir_command = ("/google/data/ro/teams/android-devtools-infra/tools/redir "
                 + "--alsologtostderr "
                 + "--gcs_bucket_path=/bigstore/gmaven-staging/${USER}/%s "
                 + "--port=1480") % release_id

  get_command = ("mvn org.apache.maven.plugins:maven-dependency-plugin:2.4:get "
                + "-Dmaven.repo.local=/tmp/maven_repo_local "
                + "-DremoteRepositories=http://localhost:1480 "
                + "-Dartifact=%s "
                + "-Ddest=%s") % (artifact, dst)

  print("""To test the staged content with 'redir' run:

%s

Then add the following repository to settings.gradle to search the 'redir'
repository:

dependencyResolutionManagement {
  repositories {
    maven {
      url 'http://localhost:1480'
      allowInsecureProtocol true
    }
  }
}

and add the following repository to gradle.build for for the staged version:

dependencies {
  coreLibraryDesugaring('%s') {
    changing = true
  }
}

Use this commands to get artifact from 'redir':

rm -rf /tmp/maven_repo_local
%s
""" % (redir_command, artifact, get_command))


def gmaven_publisher_publish(args, release_id):
  if args.dry_run:
    print('Dry-run, would have published %s' % release_id)
    return

  cmd = [GMAVEN_PUBLISHER, 'publish', release_id]
  output = subprocess.check_output(cmd)

def branch_change_diff(diff, old_version, new_version):
  invalid_line = None
  for line in str(diff).splitlines():
    if line.startswith('-R8') and \
        line != "-R8_DEV_BRANCH = '%s'" % old_version:
      print(line)
      invalid_line = line
    elif line.startswith('+R8') and \
        line != "+R8_DEV_BRANCH = '%s'" % new_version:
      print(line)
      invalid_line = line
  return invalid_line


def validate_branch_change_diff(version_diff_output, old_version, new_version):
  invalid = branch_change_diff(version_diff_output, old_version, new_version)
  if invalid:
    print("")
    print("The diff for the branch change in tools/release.py is not as expected:")
    print("")
    print("=" * 80)
    print(version_diff_output)
    print("=" * 80)
    print("")
    print("Validate the uploaded CL before landing.")
    print("")


def prepare_branch(args):
  branch_version = args.new_dev_branch[0]
  commithash = args.new_dev_branch[1]

  current_semver = utils.check_basic_semver_version(
    R8_DEV_BRANCH, ", current release branch version should be x.y", 2)
  semver = utils.check_basic_semver_version(
    branch_version, ", release branch version should be x.y", 2)
  if not semver.larger_than(current_semver):
    print('New branch version "'
      + branch_version
      + '" must be strictly larger than the current "'
      + R8_DEV_BRANCH
      + '"')
    sys.exit(1)

  def make_branch(options):
    with utils.TempDir() as temp:
      subprocess.check_call(['git', 'clone', utils.REPO_SOURCE, temp])
      with utils.ChangedWorkingDirectory(temp):
        subprocess.check_call(['git', 'branch', branch_version, commithash])

        subprocess.check_call(['git', 'checkout', branch_version])

        # Rewrite the version, commit and validate.
        old_version = 'main'
        full_version = branch_version + '.0-dev'
        version_prefix = 'public static final String LABEL = "'
        sed(version_prefix + old_version,
          version_prefix + full_version,
          R8_VERSION_FILE)

        subprocess.check_call([
          'git', 'commit', '-a', '-m', 'Version %s' % full_version])

        version_diff_output = subprocess.check_output([
          'git', 'diff', '%s..HEAD' % commithash])

        validate_version_change_diff(version_diff_output, old_version, full_version)

        # Double check that we want to create a new release branch.
        if not options.dry_run:
          answer = input('Create new branch for %s [y/N]:' % branch_version)
          if answer != 'y':
            print('Aborting new branch for %s' % branch_version)
            sys.exit(1)

        maybe_check_call(options, [
          'git', 'push', 'origin', 'HEAD:%s' % branch_version])
        maybe_tag(options, full_version)

        print('Updating tools/r8_release.py to make new dev releases on %s'
          % branch_version)

        subprocess.check_call(['git', 'new-branch', 'update-release-script'])

        # Check this file for the setting of the current dev branch.
        result = None
        for line in open(THIS_FILE_RELATIVE, 'r'):
          result = re.match(
              r"^R8_DEV_BRANCH = '(\d+).(\d+)'", line)
          if result:
            break
        if not result or not result.group(1):
          print('Failed to find version label in %s' % THIS_FILE_RELATIVE)
          sys.exit(1)

        # Update this file with the new dev branch.
        sed("R8_DEV_BRANCH = '%s.%s" % (result.group(1), result.group(2)),
          "R8_DEV_BRANCH = '%s.%s" % (str(semver.major), str(semver.minor)),
          THIS_FILE_RELATIVE)

        message = \
            'Prepare %s for branch %s' % (THIS_FILE_RELATIVE, branch_version)
        subprocess.check_call(['git', 'commit', '-a', '-m', message])

        branch_diff_output = subprocess.check_output(['git', 'diff', 'HEAD~'])

        validate_branch_change_diff(
          branch_diff_output, R8_DEV_BRANCH, branch_version)

        maybe_check_call(options, ['git', 'cl', 'upload', '-f', '-m', message])

        print('')
        print('Make sure to send out the branch change CL for review.')
        print('')

  return make_branch


def parse_options():
  result = argparse.ArgumentParser(description='Release r8')
  group = result.add_mutually_exclusive_group()
  group.add_argument('--dev-release',
                      metavar=('<main hash>'),
                      help='The hash to use for the new dev version of R8')
  group.add_argument('--version',
                      metavar=('<version>'),
                      help='The new version of R8 (e.g., 1.4.51) to release to selected channels')
  group.add_argument('--desugar-library',
                      nargs=2,
                      metavar=('<version>', '<configuration hash>'),
                      help='The new version of com.android.tools:desugar_jdk_libs')
  group.add_argument('--update-desugar-library-in-studio',
                      nargs=2,
                      metavar=('<version>', '<configuration version>'),
                      help='Update studio mirror of com.android.tools:desugar_jdk_libs')
  group.add_argument('--new-dev-branch',
                      nargs=2,
                      metavar=('<version>', '<main hash>'),
                      help='Create a new branch starting a version line (e.g. 2.0)')
  result.add_argument('--dev-pre-cherry-pick',
                      metavar=('<main hash(s)>'),
                      default=[],
                      action='append',
                      help='List of commits to cherry pick before doing full '
                           'merge, mostly used for reverting cherry picks')
  result.add_argument('--no-sync', '--no_sync',
                      default=False,
                      action='store_true',
                      help='Do not sync repos before uploading')
  result.add_argument('--bug',
                      metavar=('<bug(s)>'),
                      default=[],
                      action='append',
                      help='List of bugs for release version')
  result.add_argument('--no-bugs',
                      default=False,
                      action='store_true',
                      help='Allow Studio release without specifying any bugs')
  result.add_argument('--studio',
                      metavar=('<path>'),
                      help='Release for studio by setting the path to a studio '
                           'checkout')
  result.add_argument('--aosp',
                      metavar=('<path>'),
                      help='Release for aosp by setting the path to the '
                           'checkout')
  result.add_argument('--maven',
                      default=False,
                      action='store_true',
                      help='Release to Google Maven')
  result.add_argument('--google3',
                      default=False,
                      action='store_true',
                      help='Release for google 3')
  result.add_argument('--google3retrace',
                      default=False,
                      action='store_true',
                      help='Release retrace for google 3')
  result.add_argument('--p4-client',
                      default='update-r8',
                      metavar=('<client name>'),
                      help='P4 client name for google 3')
  result.add_argument('--use-existing-work-branch', '--use_existing_work_branch',
                      default=False,
                      action='store_true',
                      help='Use existing work branch/CL in aosp/studio/google3')
  result.add_argument('--delete-work-branch', '--delete_work_branch',
                      default=False,
                      action='store_true',
                      help='Delete CL in google3')
  result.add_argument('--no-upload', '--no_upload',
                      default=False,
                      action='store_true',
                      help="Don't upload for code review")
  result.add_argument('--dry-run',
                      default=False,
                      action='store_true',
                      help='Only perform non-commiting tasks and print others.')
  result.add_argument('--dry-run-output', '--dry_run_output',
                      default=os.getcwd(),
                      metavar=('<path>'),
                      help='Location for dry run output.')
  args = result.parse_args()
  if (len(args.bug) > 0 and args.no_bugs):
    print("Use of '--bug' and '--no-bugs' are mutually exclusive")
    sys.exit(1)

  if (args.studio
      and args.version
      and not 'dev' in args.version
      and args.bug == []
      and not args.no_bugs):
    print("When releasing a release version to Android Studio add the "
           + "list of bugs by using '--bug'")
    sys.exit(1)

  if args.version and not 'dev' in args.version and args.google3:
    print("WARNING: You should not roll a release version into google 3")

  return args


def main():
  args = parse_options()
  targets_to_run = []

  if args.new_dev_branch:
    if args.google3 or args.studio or args.aosp:
      print('Cannot create a branch and roll at the same time.')
      sys.exit(1)
    targets_to_run.append(prepare_branch(args))

  if args.dev_release:
    if args.google3 or args.studio or args.aosp:
      print('Cannot create a dev release and roll at the same time.')
      sys.exit(1)
    targets_to_run.append(prepare_release(args))

  if (args.google3
      or args.maven
      or (args.studio and not args.no_sync)
      or (args.desugar_library and not args.dry_run)):
    utils.check_gcert()

  if args.google3:
    targets_to_run.append(prepare_google3(args))
  if args.google3retrace:
    targets_to_run.append(prepare_google3_retrace(args))
  if args.studio and not args.update_desugar_library_in_studio:
    targets_to_run.append(prepare_studio(args))
  if args.aosp:
    targets_to_run.append(prepare_aosp(args))
  if args.maven:
    targets_to_run.append(prepare_maven(args))

  if args.desugar_library:
    targets_to_run.append(prepare_desugar_library(args))

  if args.update_desugar_library_in_studio:
    if not args.studio:
      print("--studio required")
      sys.exit(1)
    if args.bug == []:
      print("Update studio mirror of com.android.tools:desugar_jdk_libs "
             + "requires at least one bug by using '--bug'")
      sys.exit(1)
    targets_to_run.append(update_desugar_library_in_studio(args))

  final_results = []
  for target_closure in targets_to_run:
    final_results.append(target_closure(args))

  print('\n\n**************************************************************')
  print('PRINTING SUMMARY')
  print('**************************************************************\n\n')

  for result in final_results:
    if result is not None:
      print(result)


if __name__ == '__main__':
  sys.exit(main())
