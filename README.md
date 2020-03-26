# KFlow: fast IPFIX flows collector with Kafka export

[![Build Status](https://travis-ci.com/sukhinin/kflow.svg?branch=master)](https://travis-ci.com/sukhinin/kflow)
[![codebeat badge](https://codebeat.co/badges/e9c6225e-1b91-4e83-b72e-593e0761572d)](https://codebeat.co/projects/github-com-sukhinin-kflow-master)

## Downloading and running

KFlow requires at least Java 1.8 to run. It is known to work with Apache Kafka 2.3.0, but any recent version
should be fine. Linux is required to achieve high performance, and MacOS can be used for testing.

KFlow is distributed as a self-containing JAR (also known as fat JAR). Simply download `kflow-VERSION-all.jar` 
from [GitHub releases page](https://github.com/sukhinin/kflow/releases) and run the following
(assuming java is on your PATH):
```
java -jar kflow-VERSION-all.jar
```

You can also build the project yourself (see [Building](#building) section).

## Configuration

By default KFlow listens for IPFIX on port 4739/udp and pushes decoded flows to local Kafka broker `localhost:9092`
to topic `ipfix`. It also exposes Prometheus metrics on port 8080/tcp. This behavior can be customized by overriding
default configuration.

Configuration values are resolved from multiple sources with the following precedence, from highest to lowest:
1. Java system properties prefixed with `app.`, usually passed as one or more `-Dapp.name=value` command line arguments,
2. custom `.properties` file specified with `-c` or `--config` command line argument,
3. [default configuration values](https://github.com/sukhinin/kflow/blob/master/src/main/resources/reference.properties).

### Important properties
| Property | Description |
| --- | --- |
| `server.port` | Port number to listen for IPFIX flows |
| `server.threads` | Number of processing threads |
| `server.buffer.size` | SO_RCVBUF buffer size in bytes (allocated per processing thread) |
| `kafka.topic` | Kafka topic to write decoded flows to |
| `kafka.producers` | Number of Kafka producers (see [Performance notes](#performance-notes)) |
| `kafka.props.bootstrap.servers` | Kafka brokers to setup initial connection with |
| `kafka.props.*` | Various Kafka producer configuration properties (see [producer docs](https://kafka.apache.org/documentation/#producerconfigs)) |
| `metrics.port` | Port number to expose Prometheus metrics |

## Output format

KFlow produces JSON records with the following fields:

| Field | Type | Description |
| --- | --- | --- |
| `dvc` | string (IPv4) | IPv4 address of host that sent IPFIX packet |
| `src` | string (IPv4) | sourceIPv4Address (8) |
| `srcp` | number | sourceTransportPort (7) |
| `dst` | string (IPv4) | destinationIPv4Address (12) |
| `dstp` | number | destinationTransportPort (11) |
| `proto` | number | protocolIdentifier (4) |
| `flags` | number | tcpControlBits (6) |
| `bytes` | number | octetDeltaCount (1) |
| `pkts` | number | packetDeltaCount (2) |
| `time` | number | Export time in milliseconds since epoch |

For the description of fields other than `dvc` or `time` please refer to
[IPFIX entities list](https://www.iana.org/assignments/ipfix/ipfix.xhtml).

## Performance notes

1. Because of the way Netty handles UDP channels, KFlow requires `epoll()` call to be available to achieve high
performance. It still can run without `epoll()` support, though all processing will be done in a signle thread
regardless of the `server.threads` setting.

2. At a very high volumes (about 500,000 flows per second in our setup) shared lock inside Kafka producer code 
becomes a bottleneck. To overcome this limitation, KFlow can create multiple producers (as specified 
by `kafka.producers` config value) and distribute load between them.

3. Receive queue monitoring and detecting packet drops is crucial for handling large volumes of traffic in production. 
KFlow exposes `server_packet_drops` and `server_receive_queue` Prometheus metrics. Both are parsed from `/proc/net/udp` 
and therefore available only on Linux.

4. When exposed metrics are not enough for performance troubleshooting, we recommend using profiling tools such as 
the excellent [async-profiler](https://github.com/jvm-profiling-tools/async-profiler).

## Building

KFlow uses Gradle as its build system.

- `./gradlew build` builds the project,
- `./gradlew run` runs it.

Fat JAR is produced in `build/libs/kflow-VERSION-all.jar`. The build also assembles redistributable application 
archives in `build/distributions` folder.

## Extensibility

KFlow was built with extensibility in mind.

Support for a new flow export format, such as Netflow or sFlow, can be added by implementing `PacketDecoder`
interface and passing it as a constructor parameter to `Server` class. Handling multiple formats in a single 
application is possible by creating multiple `Server` instances with different decoders and ports.

Similarly, Kafka output can be replaced by a custom `Sink` interface implementation, which is to be passed 
as parameter to `Server` class constructor.

## Limitations

1. KFlow supports IPv4 addresses only. There are no plans for adding IPv6 support.

2. The exported field set is limited to [basic fields](#output-format) only because of performance 
considerations.

If you need more feature rich solution and don't care about performance that much, you should probably 
take a look at Cloudflare's [goflow](https://github.com/cloudflare/goflow). In our setup a single `goflow`
node was able to handle about 250.000 flows per second.
