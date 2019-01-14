#!/usr/bin/env bash
set -e

if ! lpass status --quiet; then
 printf "\n\e[1;31mRun 'lpass login YOUR_EMAIL' first\e[0m\n\n"
 exit
fi

current_dir=$(cd $(dirname $0) && pwd)
config_file=${current_dir}/push-apps.yml

fly --target superpipe set-pipeline --pipeline push-apps --config ${config_file} \
    --load-vars-from <(lpass show --notes 'Shared-apm/concourse/healthwatch-product-secrets')