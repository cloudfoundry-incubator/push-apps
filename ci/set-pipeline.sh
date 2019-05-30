#!/usr/bin/env bash
set -e

lpass ls &> /dev/null

fly -t cf-denver login -c https://concourse.cf-denver.com/ --team-name=main --open-browser

current_dir=$(cd $(dirname $0) && pwd)
config_file=${current_dir}/push-apps.yml

fly --target cf-denver set-pipeline --pipeline push-apps --config ${config_file} \
    --load-vars-from <(lpass show --notes 'Shared-CF-Denver-Pivotal/push-apps-ci')