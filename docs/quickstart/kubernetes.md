---
layout: page
title: "Install"
description: "This page will help you get started and will guide you through installing Apache Zeppelin and running it in the command line."
group: quickstart
---
<!--
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
{% include JB/setup %}

# Zeppelin on Kubernetes

Zeppelin can run on clusters managed by [Kubernetes](https://kubernetes.io/). When Zeppelin runs in Pod, it creates pods for individual interpreter. Also Spark interpreter auto configured to use Spark on Kubernetes in client mode.

Key benefits are

 - Interpreter scale-out
 - Spark interpreter auto configure Spark on Kubernetes
 - Able to customize Kubernetes yaml file
 - Spark UI access

## Prerequisites

 - Zeppelin >= 0.9.0 docker image
 - Spark >= 2.4.0 docker image (in case of using Spark Interpreter)
 - A running Kubernetes cluster with access configured to it using [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
 - [Kubernetes DNS](https://kubernetes.io/docs/concepts/services-networking/dns-pod-service/) configured in your cluster
 - Enough cpu and memory in your Kubernetes cluster. We recommend 4CPUs, 6g of memory to be able to start Spark Interpreter with few executors.

   - If you're using [minikube](https://kubernetes.io/docs/setup/minikube/), check your cluster capacity (`kubectl describe node`) and increase if necessary

     ```
     $ minikube delete    # otherwise configuration won't apply
     $ minikube config set cpus <number>
     $ minikube config set memory <number in MB>
     $ minikube start
     $ minikube config view
     ```

## Quickstart

Let's first clone the Zeppelin repository from GitHub:

```sh
git clone https://github.com/apache/zeppelin.git
cd zeppelin
# you can check out to your desired version/branch
# git checkout tags/v0.10.1
# just make sure you check the version inside "./pom.xml"
```

Now we are going to create the `zeppelin-distribution` image. This may take some time and this image will be used as a base for the upcoming required images:

```sh
docker build -t zeppelin-distribution:latest -f ./Dockerfile .
```

Next, we will build our `zeppelin-server` image:

```sh
cd scripts/docker/zeppelin-server
# Looking at the "./pom.xml" we can see the version is 0.13.0-SNAPSHOT
# Let's set the correct version in our Dockerfile:
# vi Dockerfile
# ARG version="0.13.0-SNAPSHOT"
# Once you saved the Dockerfile with the correct version we can build our image:
docker build -t zeppelin-server:0.13.0-SNAPSHOT -f ./Dockerfile .
```

The last image we build is `zeppelin-interpreter`:

```sh
cd scripts/docker/zeppelin-interpreter
docker build -t zeppelin-interpreter:0.13.0-SNAPSHOT -f ./Dockerfile .
```

So we should now have the following images:

```sh
# sudo if you are on Linux and Docker requires root
$ docker images

REPOSITORY                    TAG               IMAGE ID       CREATED          SIZE
zeppelin-interpreter          0.13.0-SNAPSHOT   4f77fe989eed   3 minutes ago    622MB
zeppelin-server               0.13.0-SNAPSHOT   4f77fe989eed   3 minutes ago    622MB
zeppelin-distribution         latest            bd2fb4b321d2   40 minutes ago   1.27GB
```

Reminder: Please adjust the images in the YAML-File of `zeppelin-server.yaml`

Start zeppelin on Kubernetes cluster,

```sh
kubectl apply -f zeppelin-server.yaml
```

Port forward Zeppelin server port,

```sh
kubectl port-forward zeppelin-server 8080:80
```

and browse [localhost:8080](http://localhost:8080).
Try running some paragraphs and see if each interpreter is running as a Pod (using `kubectl get pods`), instead of a local process.

To shut down,

```sh
kubectl delete -f zeppelin-server.yaml
```

## Spark Interpreter

Build spark docker image to use Spark Interpreter.
Download spark binary distribution and run following command.
Spark 2.4.0 or later version is required.

```
# if you're using minikube, set docker-env
$ eval $(minikube docker-env)

# build docker image
$ <spark-distribution>/bin/docker-image-tool.sh -m -t 2.4.0 build
```

Run `docker images` and check if `spark:2.4.0` is created.
Configure `sparkContainerImage` of `zeppelin-server-conf` ConfigMap in `zeppelin-server.yaml`.


Create note and configure executor number (default 1)

```
%spark.conf
spark.executor.instances  5
```

And then start your spark interpreter

```
%spark
sc.parallelize(1 to 100).count
...
```
While `spark.master` property of SparkInterpreter starts with `k8s://` (default `k8s://https://kubernetes.default.svc` when Zeppelin started using zeppelin-server.yaml), Spark executors will be automatically created in your Kubernetes cluster.
Spark UI is accessible by clicking `SPARK JOB` on the Paragraph.

Check [here](https://spark.apache.org/docs/latest/running-on-kubernetes.html) to know more about Running Spark on Kubernetes.


## Build Zeppelin image manually

To build your own Zeppelin image, first build Zeppelin project with `-Pbuild-distr` flag.

```
$ ./mvnw package -DskipTests -Pbuild-distr <your flags>
```

Binary package will be created under `zeppelin-distribution/target` directory. Move created package file under `scripts/docker/zeppelin/bin/` directory.

```
$ mv zeppelin-distribution/target/zeppelin-*-bin.tgz scripts/docker/zeppelin/bin/
```

`scripts/docker/zeppelin/bin/Dockerfile` downloads package from internet. Modify the file to add package from filesystem.

```
...

# Find following section and comment out
#RUN echo "$LOG_TAG Download Zeppelin binary" && \
#    wget -O /tmp/zeppelin-${Z_VERSION}-bin-all.tgz "https://www.apache.org/dyn/closer.lua/zeppelin/zeppelin-${Z_VERSION}/zeppelin-${Z_VERSION}-bin-all.tgz?action=download" && \
#    tar -zxvf /tmp/zeppelin-${Z_VERSION}-bin-all.tgz && \
#    rm -rf /tmp/zeppelin-${Z_VERSION}-bin-all.tgz && \
#    mv /zeppelin-${Z_VERSION}-bin-all ${ZEPPELIN_HOME}

# Add following lines right after the commented line above
ADD zeppelin-${Z_VERSION}.tar.gz /
RUN ln -s /zeppelin-${Z_VERSION} /zeppelin
...
```

Then build docker image.

```
# configure docker env, if you're using minikube
$ eval $(minikube docker-env)

# change directory
$ cd scripts/docker/zeppelin/bin/

# build image. Replace <tag>.
$ docker build -t <tag> .
```

Finally, set custom image `<tag>` just created to `image` and `ZEPPELIN_K8S_CONTAINER_IMAGE` env variable of `zeppelin-server` container spec in `zeppelin-server.yaml` file.

Currently, single docker image is being used in both Zeppelin server and Interpreter pods. Therefore,

| Pod | Number of instances | Image | Note |
| --- | --- | --- | --- |
| Zeppelin Server | 1 | Zeppelin docker image | User creates/deletes with kubectl command |
| Zeppelin Interpreters | n | Zeppelin docker image | Zeppelin Server creates/deletes |
| Spark executors | m | Spark docker image | Spark Interpreter creates/deletes |

Currently, size of Zeppelin docker image is quite big. Zeppelin project is planning to provides lightweight images for each individual interpreter in the future.


## How it works

### Zeppelin on Kubernetes

`k8s/zeppelin-server.yaml` is provided to run Zeppelin Server with few sidecars and configurations.
Once Zeppelin Server is started in side Kubernetes, it auto configure itself to use `K8sStandardInterpreterLauncher`.

The launcher creates each interpreter in a Pod using templates located under `k8s/interpreter/` directory.
Templates in the directory applied in alphabetical order. Templates are rendered by [jinjava](https://github.com/HubSpot/jinjava)
and all interpreter properties are accessible inside the templates.

### Spark on Kubernetes

When interpreter group is `spark`, Zeppelin sets necessary spark configuration automatically to use Spark on Kubernetes.
It uses client mode, so Spark interpreter Pod works as a Spark driver, spark executors are launched in separate Pods.
This auto configuration can be overridden by manually setting `spark.master` property of Spark interpreter.


### Accessing Spark UI (or Service running in interpreter Pod)

Zeppelin server Pod has a reverse proxy as a sidecar, and it splits traffic to Zeppelin server and Spark UI running in the other Pods.
It assume both `<your service domain>` and `*.<your service domain>` point the nginx proxy address.
`<your service domain>` is directed to ZeppelinServer, `*.<your service domain>` is directed to interpreter Pods.

`<port>-<interpreter pod svc name>.<your service domain>` is convention to access any application running in interpreter Pod.


For example, When your service domain name is `local.zeppelin-project.org` Spark interpreter Pod is running with a name `spark-axefeg` and Spark UI is running on port 4040,

```
4040-spark-axefeg.local.zeppelin-project.org
```

is the address to access Spark UI.

Default service domain is `local.zeppelin-project.org:8080`. `local.zeppelin-project.org` and `*.local.zeppelin-project.org` configured to resolve `127.0.0.1`.
It allows access Zeppelin and Spark UI with `kubectl port-forward zeppelin-server 8080:80`.


If you like to use your custom domain

1. Configure [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/) in Kubernetes cluster for `http` port of the service `zeppelin-server` defined in `k8s/zeppelin-server.yaml`.
2. Configure DNS record that your service domain and wildcard subdomain point the IP Addresses of your Ingress.
3. Modify `serviceDomain` of `zeppelin-server-conf` ConfigMap in `k8s/zeppelin-server.yaml` file.
4. Apply changes (e.g. `kubectl apply -f k8s/zeppelin-server.yaml`)


## Persist /notebook and /conf directory

Notebook and configurations are not persisted by default. Please configure volume and update `k8s/zeppelin-server.yaml`
to use the volume to persist /notebook and /conf directory if necessary.


## Customization

### Zeppelin Server Pod
Edit `k8s/zeppelin-server.yaml` and apply.

### Interpreter Pod
Since Interpreter Pod is created/deleted by ZeppelinServer using templates under `k8s/interpreter` directory,
to customize,

  1. Prepare `k8s/interpreter` directory with customization (edit or create new yaml file), in a Kubernetes volume.
  2. Modify `k8s/zeppelin-server.yaml` and mount prepared volume dir `k8s/interpreter` to `/zeppelin/k8s/interpreter/`.
  3. Apply modified `k8s/zeppelin-server.yaml`.
  4. Run a paragraph will create an interpreter using modified yaml files.

The interpreter pod can also be customized through the interpreter settings. Here are some of the properties:

| Property Name | Default Value | Description |
| ----- | ----- | ----- |
| `zeppelin.k8s.interpreter.namespace` | `default` | Specify the namespace of the current interpreter. Users can set different namespaces for different interpreters. In order to minimize permissions, the interpreter pod can only be created in the `default` namespace by default. If users need to create an interpreter pod in other namespaces, they need to add the corresponding `rolebinding` in `k8s/zeppelin-server.yaml`.|
| `zeppelin.k8s.interpreter.serviceAccount` | `default` | The Kubernetes service account to use. |
| `zeppelin.k8s.interpreter.container.image` | `apache/zeppelin:<ZEPPELIN_VERSION>` | The interpreter image to use. |
| `zeppelin.k8s.interpreter.cores` | (optional)  | The number of cpu cores to use. |
| `zeppelin.k8s.interpreter.memory` | (optional) | The memory to use, e.g., `1g`. |
| `zeppelin.k8s.interpreter.gpu.type` | (optional) | Set the type of gpu to request when the interpreter pod is required to schedule gpu resources, e.g., `nvidia.com/gpu`. |
| `zeppelin.k8s.interpreter.gpu.nums` | (optional) | Tne number of gpu to use. |
| `zeppelin.k8s.interpreter.imagePullSecrets` | (optional) | Set the comma-separated list of Kubernetes secrets while pulling images, e.g., `mysecret1,mysecret2` |
| `zeppelin.k8s.interpreter.container.imagePullPolicy` | (optional) | Set the pull policy of the interpreter image, e.g., `Always` |
| `zeppelin.k8s.spark.container.imagePullPolicy` | (optional) | Set the pull policy of the spark image, e.g., `Always` |
| `zeppelin.spark.uiWebUrl` | `//{{PORT}}-{{SERVICE_NAME}}.{{SERVICE_DOMAIN}}` | The URL for user to access Spark UI. The default value is a [jinjava](https://github.com/HubSpot/jinjava) template that contains three variables. |
| `zeppelin.k8s.spark.useIngress` | (optional) | If true, the [Ingress](https://kubernetes.io/docs/concepts/services-networking/ingress/) will be created when creating the spark interpreter. So users can access the Spark UI through Ingress. |
| `zeppelin.k8s.spark.ingress.host` | `{{PORT}}-{{SERVICE_NAME}}.{{SERVICE_DOMAIN}}` | If `zeppelin.k8s.spark.useIngress` is `true`, it configures the `host` value of the Ingress. The default value is a [jinjava](https://github.com/HubSpot/jinjava) template that contains three variables. Users can access the Spark UI through a customized `zeppelin.k8s.spark.ingress.host`. |

## Future work

 - Smaller interpreter docker image.
 - Blocking communication between interpreter Pod.
 - Spark Interpreter Pod has Role CRUD for any pod/service in the same namespace. Which should be restricted to only Spark executors Pod.
 - Per note interpreter mode by default when Zeppelin is running on Kubernetes


## Development

Instead of build Zeppelin distribution package and docker image everytime during development,
Zeppelin can run locally (such as inside your IDE in debug mode) and able to run Interpreter using [K8sStandardInterpreterLauncher](https://github.com/apache/zeppelin/blob/master/zeppelin-plugins/launcher/k8s-standard/src/main/java/org/apache/zeppelin/interpreter/launcher/K8sStandardInterpreterLauncher.java) by configuring following environment variables.

| Environment variable | Value | Description |
| ----- | ----- | ----- |
| `ZEPPELIN_RUN_MODE` | `k8s` | Make Zeppelin run interpreter on Kubernetes |
| `ZEPPELIN_K8S_PORTFORWARD` | `true` | Enable port forwarding from local Zeppelin instance to Interpreters running on Kubernetes |
| `ZEPPELIN_K8S_CONTAINER_IMAGE` | `<image>:<version>` | Zeppelin interpreter docker image to use |
| `ZEPPELIN_K8S_SPARK_CONTAINER_IMAGE` | `<image>:<version>` | Spark docker image to use |
| `ZEPPELIN_K8S_NAMESPACE` | `<k8s namespace>` | Kubernetes namespace  to use |
| `KUBERNETES_AUTH_TOKEN` | `<token>` | Kubernetes auth token to create resources |

