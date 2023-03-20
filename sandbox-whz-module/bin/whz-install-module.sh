#!/bin/bash

# 定义sandbox的 target 目录
SANDBOX_USER_MODULE_DIR=${HOME}/.sandbox-module

# exit shell with err_code
# $1 : err_code
# $2 : err_msg
exit_on_err()
{
    [[ ! -z "${2}" ]] && echo "${2}" 1>&2
    exit ${1}
}

# 执行maven打包命令
mvn clean cobertura:cobertura package -Dmaven.test.skip=false -f ../pom.xml \
    || exit_on_err 1 "package sandbox failed."

# 将打包后生成jar包复制到"~/.sandbox-module"目录
mkdir -p ${SANDBOX_USER_MODULE_DIR} || exit_on_err 1 "permission denied, can not mkdir ~/.sandbox-module"
cp ../target/sandbox-whz-module-*-jar-with-dependencies.jar ${SANDBOX_USER_MODULE_DIR}  || exit_on_err 1 "permission denied, can not copy module to ~/.sandbox-module"

echo "whz install finish."
