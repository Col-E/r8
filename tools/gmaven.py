#!/usr/bin/env python3
# Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
# for details. All rights reserved. Use of this source code is governed by a
# BSD-style license that can be found in the LICENSE file.

import re
import subprocess

GMAVEN_PUBLISHER = '/google/bin/releases/android-devtools/gmaven/publisher/gmaven-publisher'
GMAVEN_PUBLISH_STAGE_RELEASE_ID_PATTERN = re.compile(
    'Release ID = ([0-9a-f\-]+)')


def publisher_stage(gfiles, dry_run=False):
    if dry_run:
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

    matches = GMAVEN_PUBLISH_STAGE_RELEASE_ID_PATTERN.findall(
        output.decode("utf-8"))
    if matches == None or len(matches) > 1:
        print("Could not determine the release ID from the gmaven_publisher " +
              "output. Expected a line with 'Release ID = <release id>'.")
        print("Output was:")
        print(output)
        sys.exit(1)

    print(output)

    release_id = matches[0]
    return release_id


def publisher_stage_redir_test_info(release_id, artifact, dst):

    redir_command = ("/google/data/ro/teams/android-devtools-infra/tools/redir "
                     + "--alsologtostderr " +
                     "--gcs_bucket_path=/bigstore/gmaven-staging/${USER}/%s " +
                     "--port=1480") % release_id

    get_command = (
        "mvn org.apache.maven.plugins:maven-dependency-plugin:2.4:get " +
        "-Dmaven.repo.local=/tmp/maven_repo_local " +
        "-DremoteRepositories=http://localhost:1480 " + "-Dartifact=%s " +
        "-Ddest=%s") % (artifact, dst)

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
  implementation('%s') {
    changing = true
  }
}

Use this commands to get artifact from 'redir':

rm -rf /tmp/maven_repo_local
%s
""" % (redir_command, artifact, get_command))


def publisher_publish(release_id, dry_run=False):
    if dry_run:
        print('Dry-run, would have published %s' % release_id)
        return

    cmd = [GMAVEN_PUBLISHER, 'publish', release_id]
    output = subprocess.check_output(cmd)
