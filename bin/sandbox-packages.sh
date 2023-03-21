#!/bin/bash

# 定义sandbox的 target 目录
SANDBOX_TARGET_DIR=../target/sandbox


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

# 在/target/sandbox目录下创建：bin、lib、module、cfg、provider、sandbox-module目录
mkdir -p ${SANDBOX_TARGET_DIR}/bin
mkdir -p ${SANDBOX_TARGET_DIR}/lib
mkdir -p ${SANDBOX_TARGET_DIR}/module
mkdir -p ${SANDBOX_TARGET_DIR}/cfg
mkdir -p ${SANDBOX_TARGET_DIR}/provider
mkdir -p ${SANDBOX_TARGET_DIR}/sandbox-module

# 将jar包copy到target目录
cp ../sandbox-core/target/sandbox-core-*-jar-with-dependencies.jar ${SANDBOX_TARGET_DIR}/lib/sandbox-core.jar \
    && cp ../sandbox-agent/target/sandbox-agent-*-jar-with-dependencies.jar ${SANDBOX_TARGET_DIR}/lib/sandbox-agent.jar \
    && cp ../sandbox-spy/target/sandbox-spy-*-jar-with-dependencies.jar ${SANDBOX_TARGET_DIR}/lib/sandbox-spy.jar \
    && cp sandbox-logback.xml ${SANDBOX_TARGET_DIR}/cfg/sandbox-logback.xml \
    && cp sandbox.properties ${SANDBOX_TARGET_DIR}/cfg/sandbox.properties \
    && cp sandbox.sh ${SANDBOX_TARGET_DIR}/bin/sandbox.sh \
    && cp install-local.sh ${SANDBOX_TARGET_DIR}/install-local.sh

# 设置sandbox的版本
SANDBOX_VERSION=$(cat ..//sandbox-core/target/classes/com/alibaba/jvm/sandbox/version)
echo "${SANDBOX_VERSION}" > ${SANDBOX_TARGET_DIR}/cfg/version

# for example
mkdir -p ${SANDBOX_TARGET_DIR}/example\
    && cp ../sandbox-debug-module/target/sandbox-debug-module-*-jar-with-dependencies.jar\
            ${SANDBOX_TARGET_DIR}/example/sandbox-debug-module.jar

# for mgr
cp ../sandbox-mgr-module/target/sandbox-mgr-module-*-jar-with-dependencies.jar ${SANDBOX_TARGET_DIR}/module/sandbox-mgr-module.jar \
    && cp ../sandbox-mgr-provider/target/sandbox-mgr-provider-*-jar-with-dependencies.jar ${SANDBOX_TARGET_DIR}/provider/sandbox-mgr-provider.jar

# make it execute able
chmod +x ${SANDBOX_TARGET_DIR}/*.sh


# 给target文件夹打包成例如sandbox-1.3.3-bin.zip的压缩包
cd ../target/
zip -r sandbox-${SANDBOX_VERSION}-bin.zip sandbox/
cd -

# 给target文件夹打包成例如sandbox-1.3.3-bin.tar的压缩包
cd ../target/
tar -zcvf sandbox-${SANDBOX_VERSION}-bin.tar sandbox/
cd -

# 发布稳定版本，命名为: sandbox-stable-bin.zip 和 sandbox-stable-bin.tar
cp ../target/sandbox-${SANDBOX_VERSION}-bin.zip ../target/sandbox-stable-bin.zip
cp ../target/sandbox-${SANDBOX_VERSION}-bin.tar ../target/sandbox-stable-bin.tar

echo "package sandbox-${SANDBOX_VERSION}-bin.zip finish."
