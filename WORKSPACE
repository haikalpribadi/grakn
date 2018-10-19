#
# GRAKN.AI - THE KNOWLEDGE GRAPH
# Copyright (C) 2018 Grakn Labs Ltd
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#

workspace(name = "grakn_core")


####################
# Load Build Tools #
####################

# Load additional build tools, such bazel-deps and unused-deps
load("//dependencies/tools:dependencies.bzl", "tools_dependencies")
tools_dependencies()


#####################################
# Load Compiler Dependencies: ANTLR #
#####################################

# Load compiler tool: ANTLR
load("//dependencies/compilers:dependencies.bzl", "antlr_dependencies")
antlr_dependencies()

# Load dependencies for ANTLR rules
load("@rules_antlr//antlr:deps.bzl", "antlr_dependencies")
antlr_dependencies()


####################################
# Load Compiler Dependencies: GRPC #
####################################

# Load GRPC dependencies
load("//dependencies/compilers:dependencies.bzl", "grpc_dependencies", "python_dependencies", "node_dependencies")
grpc_dependencies()
python_dependencies()
node_dependencies()

## Python PIP dependencies
load("@io_bazel_rules_python//python:pip.bzl", "pip_repositories", "pip3_import")
pip_repositories()
pip3_import(
    name = "python_grakn_deps",
    requirements = "//client_python:requirements.txt",
)
load("@python_grakn_deps//:requirements.bzl", "pip_install")
pip_install()

load("@com_github_grpc_grpc//bazel:grpc_deps.bzl", com_github_grpc_grpc_bazel_grpc_deps = "grpc_deps")
com_github_grpc_grpc_bazel_grpc_deps()

# Node deps
load("@org_pubref_rules_node//node:rules.bzl", "node_repositories", "yarn_modules")
node_repositories()

yarn_modules(
    name = "grpc_node_modules",
    deps = {
        "google-protobuf": "3.6.1",
        "grpc": "1.15.1",
        "jest": "^23.4.0"
    },
)

# Load GRPC Java dependencies
load("@org_pubref_rules_proto//java:deps.bzl", "java_grpc_compile")
java_grpc_compile()

# Load GRPC Python dependencies
load("@org_pubref_rules_proto//python:deps.bzl", "python_grpc_compile")
python_grpc_compile()

# Load GRPC Node.js dependencies
load("@org_pubref_rules_proto//node:deps.bzl", "node_grpc_compile", "node_grpc_library")
node_grpc_compile()
node_grpc_library()



########################################
# Load Runtime Dependencies from Maven #
########################################

load("//dependencies/maven:dependencies.bzl", "maven_dependencies")
maven_dependencies()