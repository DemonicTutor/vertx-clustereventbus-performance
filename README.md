Reproducer to showcase performance registering consumers / localConsusmers if vertx is clustered

to run:
```
mvn clean package docker:build
docker run -d --rm --name tp1 -p 8080:8080 --cpuset-cpus 1 -m 512m vertx-clustereventbus-performance:latest
docker run -d --rm --name tp2 -p 8181:8080 --cpuset-cpus 2 -m 512m vertx-clustereventbus-performance:latest
docker run -d --rm --name tp3 -p 8282:8080 --cpuset-cpus 3 -m 512m vertx-clustereventbus-performance:latest
docker stats
```

then remove `@Ignore` and run `MainVerticleTest#load` however you like

to switch vertx version to 3.9.5 change property in pom.xml and modify `MainVerticleTest#httpRequest`  
to switch hazelcast / ignite uncomment dependency in pom.xml

# on my machine

## VertX 4.0.2 + Hazelcast
```
final var requestsPerSecond = 100;

CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O   PIDS
781dbd1e35f0   tp1       91.17%    209.8MiB / 512MiB   40.98%    9.35MB / 2.56GB   0B / 0B     72
8c50c97d62ca   tp2       53.34%    197.7MiB / 512MiB   38.62%    1.28GB / 4.54MB   0B / 0B     72
f163fb95e082   tp3       51.07%    197.4MiB / 512MiB   38.56%    1.28GB / 5MB      0B / 0B     85

stays there for ~10-15 seconds after requests end
CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O   PIDS
781dbd1e35f0   tp1       99.72%    225.9MiB / 512MiB   44.13%    46.1MB / 17.9GB   0B / 0B     72
8c50c97d62ca   tp2       56.06%    210.7MiB / 512MiB   41.15%    8.95GB / 22.6MB   0B / 0B     73
f163fb95e082   tp3       56.42%    208.2MiB / 512MiB   40.66%    8.95GB / 22.5MB   0B / 0B     85
```

```
final var requestsPerSecond = 1000;

CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O   PIDS
781dbd1e35f0   tp1       100.51%   246.8MiB / 512MiB   48.20%    66.7MB / 25.3GB   0B / 0B     72
8c50c97d62ca   tp2       57.68%    218.5MiB / 512MiB   42.67%    12.6GB / 31.2MB   0B / 0B     72
f163fb95e082   tp3       56.09%    217.3MiB / 512MiB   42.44%    12.6GB / 30.9MB   0B / 0B     86

cpu doesnt drop after requests stop
```

## VertX 3.9.5 + Hazelcast

```
final var requestsPerSecond = 100;

CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O   PIDS
c29c9960a920   tp3       0.25%     172.5MiB / 512MiB   33.70%    1.85MB / 893kB    0B / 0B     52
3f01cbf4bb8f   tp2       0.19%     178.2MiB / 512MiB   34.81%    2.01MB / 1.23MB   0B / 0B     61
515a79d376ff   tp1       100.64%   400.9MiB / 512MiB   78.30%    65.1MB / 61.7MB   0B / 0B     74


when requests stop immediately:
CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O   PIDS
c29c9960a920   tp3       0.42%     172.6MiB / 512MiB   33.71%    1.89MB / 914kB    0B / 0B     52
3f01cbf4bb8f   tp2       0.50%     179.1MiB / 512MiB   34.99%    2.04MB / 1.26MB   0B / 0B     61
515a79d376ff   tp1       0.42%     396.5MiB / 512MiB   77.45%    65.5MB / 62.5MB   0B / 0B     75
```


## VertX 4.0.2 + Ignite

```
final var requestsPerSecond = 100;

CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O     PIDS
08ed909edc55   tp3       90.05%    400.5MiB / 512MiB   78.22%    2.15MB / 1.89MB   0B / 0B       86
818f1d3e7db8   tp2       93.28%    401.5MiB / 512MiB   78.42%    2.22MB / 2.08MB   238kB / 0B    94
9062bf3b8bd5   tp1       100.14%   429.8MiB / 512MiB   83.95%    3.32MB / 3.41MB   81.9MB / 0B   101

~15 seconds after requests stop:
CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O     PIDS
08ed909edc55   tp3       0.49%     418.9MiB / 512MiB   81.82%    3.55MB / 3.15MB   0B / 0B       86
818f1d3e7db8   tp2       0.49%     416.1MiB / 512MiB   81.27%    3.6MB / 3.43MB    238kB / 0B    95
9062bf3b8bd5   tp1       99.63%    437.5MiB / 512MiB   85.45%    5.51MB / 5.83MB   81.9MB / 0B   102

~45 seconds after requests stop:
CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O     PIDS
08ed909edc55   tp3       2.06%     404.4MiB / 512MiB   78.98%    3.62MB / 3.21MB   0B / 0B       86
818f1d3e7db8   tp2       1.65%     409.2MiB / 512MiB   79.92%    3.67MB / 3.49MB   238kB / 0B    95
9062bf3b8bd5   tp1       1.49%     437.5MiB / 512MiB   85.45%    5.58MB / 5.9MB    81.9MB / 0B   102

```

```
final var requestsPerSecond = 1000;

CONTAINER ID   NAME      CPU %     MEM USAGE / LIMIT   MEM %     NET I/O           BLOCK I/O   PIDS
b1c31812666f   tp3       99.84%    410.3MiB / 512MiB   80.14%    3.45MB / 3.09MB   0B / 0B     86
a6a52f937053   tp2       99.51%    407MiB / 512MiB     79.49%    3.51MB / 3.3MB    0B / 0B     95
d15d9568e229   tp1       99.77%    429.9MiB / 512MiB   83.97%    6.35MB / 7.91MB   0B / 0B     102

cpu doesnt drop after requests stop
```

## VertX 3.9.5 + Ignite

