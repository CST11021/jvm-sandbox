#!/usr/bin/env bash

# sandbox的target目录
SANDBOX_TARGET_DIR=../target/sandbox

# exit shell with err_code
# $1 : err_code
# $2 : err_msg
exit_on_err()
{
    [[ ! -z "${2}" ]] && echo "${2}" 1>&2
    exit ${1}
}

# 执行sandbox-packages.sh，将sandbox工程进行打包
sh ./sandbox-packages.sh || exit_on_err 1 "install failed cause package failed"

# 将打包后生成的"target/sandbox"目录复制到"~/sandbox"目录
mkdir -p ${HOME}/sandbox || exit_on_err 1 "permission denied, can not mkdir ~/sandbox"
cp -r ${SANDBOX_TARGET_DIR}/* ${HOME}/sandbox  || exit_on_err 1 "permission denied, can not copy module to ~/sandbox"