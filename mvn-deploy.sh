#!/usr/bin/env bash
#
# Deploy tabula to Maven Central (Central Publishing)
# 部署 tabula 到 Maven Central（Central Publishing）
#
# Usage: ./mvn-deploy.sh
# 用法：./mvn-deploy.sh
#
# Prerequisites: ~/.m2/settings.xml must contain the central server credentials,
#               matching publishingServerId=central of central-publishing-maven-plugin in pom.xml
# 前置条件：~/.m2/settings.xml 已配置 central 服务器认证，
#           对应 pom.xml 中 central-publishing-maven-plugin 的 publishingServerId=central
#
set -euo pipefail

# Switch to the script's directory so pom.xml resolves correctly (callable from anywhere)
# 切换到脚本所在目录，确保 pom.xml 定位正确（可从任意目录调用）
cd "$(dirname "$0")"

# -Djansi.passthrough=true -Dstyle.color=always：keep Maven's colored terminal output
# -Djansi.passthrough=true -Dstyle.color=always：保留 Maven 终端彩色输出
# -DskipTests=true：skip tests at deploy time (full tests already ran before commit; avoids duplicate runs)
# -DskipTests=true：部署环节跳过测试（测试已在提交前全量跑过，避免重复执行）
# GPG signing is kept: it is required by Maven Central (unlike mvn-install.sh which passes -Dgpg.skip=true)
# 保留 GPG 签名：中央仓库发布强制要求（与 mvn-install.sh 跳过签名不同）
mvn -Djansi.passthrough=true -Dstyle.color=always -DskipTests=true -f pom.xml deploy
