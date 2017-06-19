# microbean-helm

The microbean-helm project lets you work with the server-side
componentry of [Helm][0] from Java.

# Helm

[Helm][0] is the package manager for [Kubernetes][1].  It consists of
a command line program named `helm` and a server-side component named
Tiller.  `helm` serves as a Tiller client.

# Tiller

[Tiller][2] is the server-side component of Helm.  Tiller accepts and
works with Helm [_charts_][3]&mdash;packaged Kubernetes manifest
templates together with their values.  microbean-helm lets
you
[build and work with those charts and the _releases_ they produce from Java][4] and
send them back and forth to and from Tiller.

## Installation

In a normal Helm usage scenario, Tiller is installed just-in-time by
the `helm` command line client (via the `helm init` subcommand).  It
runs as a Pod in a Kubernetes cluster.  microbean-helm features the
`TillerInstaller` class that can do the same thing from Java.

## Connectivity

Because Tiller runs as a Pod, communicating with it from outside the
cluster is not straightforward.  The `helm` command line client
forwards a local port to a port on the Tiller Pod and, via this
tunnel, establishes communication with the Tiller server.  The
microbean-helm project does the same thing but via a Java library.

## Communication

Tiller is fundamentally a [gRPC][5] application.  The microbean-helm
project [generates the Java bindings][6] to its gRPC API, allowing
applications to communicate with Tiller using Java classes.

# Documentation

The microbean-helm project [documentation is online][7].

[0]: https://helm.sh/
[1]: https://kubernetes.io/
[2]: https://docs.helm.sh/glossary/#tiller
[3]: https://docs.helm.sh/developing_charts/#
[4]: https://microbean.github.io/microbean-helm/apidocs/hapi/services/tiller/ReleaseServiceGrpc.ReleaseServiceStub.html
[5]: http://www.grpc.io/
[6]: https://microbean.github.io/microbean-helm/apidocs/index.html
[7]: https://microbean.github.io/microbean-helm/
