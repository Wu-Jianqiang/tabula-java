#!/usr/bin/env bash
#
# Install tabula to the local Maven repository (~/.m2/repository) for local debugging by other projects
# 安装 tabula 到本地 Maven 仓库（~/.m2/repository），供其他项目本地调试/测试引用
#
# Usage: ./mvn-install.sh
# 用法：./mvn-install.sh
#
# No authentication needed; unlike mvn-deploy.sh, this script does NOT publish to Maven Central
# 无需认证；与 mvn-deploy.sh 不同，本脚本不发布到中央仓库
#
set -euo pipefail

# Switch to the script's directory so pom.xml resolves correctly (callable from anywhere)
# 切换到脚本所在目录，确保 pom.xml 定位正确（可从任意目录调用）
cd "$(dirname "$0")"

# -Djansi.passthrough=true -Dstyle.color=always：keep Maven's colored terminal output
# -Djansi.passthrough=true -Dstyle.color=always：保留 Maven 终端彩色输出
# -DskipTests=true：skip tests during local debug iteration (run full tests before an official release)
# -DskipTests=true：本地调试迭代跳过测试（正式发布前请先全量跑测试）
# -Dgpg.skip=true：skip GPG signing, which is only required by Maven Central (bound to the verify phase in pom.xml)
# -Dgpg.skip=true：跳过 GPG 签名——中央仓库发布才需要签名（pom.xml 中绑定在 verify 阶段）
mvn -Djansi.passthrough=true -Dstyle.color=always -DskipTests=true -Dgpg.skip=true -f pom.xml install
